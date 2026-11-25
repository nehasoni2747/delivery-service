package nl.delivery.order.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import nl.delivery.order.persistence.DeliveryOrderRepository;
import nl.delivery.order.service.PricingClient;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:orders;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired DeliveryOrderRepository repository;
    @MockBean PricingClient pricingClient;

    @BeforeEach
    void clearOrders() {
        repository.deleteAll();
    }

    @Test
    void persistsOrderAndReturnsIdempotentReplay() throws Exception {
        when(pricingClient.evaluate(any())).thenAnswer(invocation -> {
            return new PricingEvaluationResponse(java.util.UUID.randomUUID(), true,
                    new PricingEvaluationResponse.Price(new BigDecimal("4.95"), "EUR"),
                    List.of(new PricingEvaluationResponse.PriceComponent("BASE_PRICE", new BigDecimal("4.95"))),
                    null, null, OffsetDateTime.now());
        });
        String request = """
                {"customerReference":"DEMO-ORDER-001",
                 "sender":{"name":"Sender Example","postalCode":"1012 AB","countryCode":"NL"},
                 "recipient":{"name":"Recipient Example","postalCode":"5611EM","countryCode":"NL"},
                 "parcel":{"weightInGrams":500},"deliveryType":"STANDARD"}
                """;

        var first = mvc.perform(post("/api/v1/delivery-orders")
                        .header("Idempotency-Key", "order-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.price.amount").value(4.95))
                .andReturn();
        String orderId = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.orderId");

        mvc.perform(post("/api/v1/delivery-orders")
                        .header("Idempotency-Key", "order-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId));

        mvc.perform(get("/api/v1/delivery-orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        verify(pricingClient, times(1)).evaluate(any());
        org.assertj.core.api.Assertions.assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void rejectsChangedContentForAnExistingIdempotencyKey() throws Exception {
        when(pricingClient.evaluate(any())).thenAnswer(invocation -> {
            return new PricingEvaluationResponse(java.util.UUID.randomUUID(), true,
                    new PricingEvaluationResponse.Price(new BigDecimal("4.95"), "EUR"),
                    List.of(new PricingEvaluationResponse.PriceComponent("BASE_PRICE", new BigDecimal("4.95"))),
                    null, null, OffsetDateTime.now());
        });
        String body = """
                {"customerReference":"DEMO-ORDER-001",
                 "sender":{"name":"Sender Example","postalCode":"1012AB","countryCode":"NL"},
                 "recipient":{"name":"Recipient Example","postalCode":"5611EM","countryCode":"NL"},
                 "parcel":{"weightInGrams":500},"deliveryType":"STANDARD"}
                """;

        mvc.perform(post("/api/v1/delivery-orders").header("Idempotency-Key", "order-key-2")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/delivery-orders").header("Idempotency-Key", "order-key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("500", "501")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Idempotency conflict"));
    }

    @Test
    void recordsPricingFailureWithoutConfirmingOrder() throws Exception {
        when(pricingClient.evaluate(any())).thenThrow(
                new org.springframework.web.client.ResourceAccessException("timeout"));
        var result = mvc.perform(post("/api/v1/delivery-orders")
                        .header("Idempotency-Key", "order-key-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerReference":"DEMO-ORDER-003",
                                 "sender":{"name":"Sender Example","postalCode":"1012AB","countryCode":"NL"},
                                 "recipient":{"name":"Recipient Example","postalCode":"5611EM","countryCode":"NL"},
                                 "parcel":{"weightInGrams":500},"deliveryType":"STANDARD"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PRICING_UNAVAILABLE"))
                .andExpect(jsonPath("$.price").doesNotExist())
                .andReturn();
        String orderId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.orderId");
        mvc.perform(get("/api/v1/delivery-orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRICING_UNAVAILABLE"));
    }

    @Test
    void concurrentSameKeyRequestsShareOneOrderAndOnePricingCall() throws Exception {
        var pricingStarted = new CountDownLatch(1);
        var finishPricing = new CountDownLatch(1);
        when(pricingClient.evaluate(any())).thenAnswer(invocation -> {
            pricingStarted.countDown();
            if (!finishPricing.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test pricing wait timed out");
            }
            return new PricingEvaluationResponse(UUID.randomUUID(), true,
                    new PricingEvaluationResponse.Price(new BigDecimal("4.95"), "EUR"),
                    List.of(new PricingEvaluationResponse.PriceComponent("BASE_PRICE", new BigDecimal("4.95"))),
                    null, null, OffsetDateTime.now());
        });
        String body = """
                {"customerReference":"DEMO-CONCURRENT-001",
                 "sender":{"name":"Sender Example","postalCode":"1012AB","countryCode":"NL"},
                 "recipient":{"name":"Recipient Example","postalCode":"5611EM","countryCode":"NL"},
                 "parcel":{"weightInGrams":500},"deliveryType":"STANDARD"}
                """;
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> mvc.perform(post("/api/v1/delivery-orders")
                            .header("Idempotency-Key", "order-key-concurrent")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn());
            org.assertj.core.api.Assertions.assertThat(pricingStarted.await(5, TimeUnit.SECONDS)).isTrue();

            var second = mvc.perform(post("/api/v1/delivery-orders")
                            .header("Idempotency-Key", "order-key-concurrent")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PRICING_PENDING"))
                    .andReturn();
            finishPricing.countDown();
            var firstResult = first.get(5, TimeUnit.SECONDS);
            String firstId = com.jayway.jsonpath.JsonPath.read(firstResult.getResponse().getContentAsString(), "$.orderId");
            String secondId = com.jayway.jsonpath.JsonPath.read(second.getResponse().getContentAsString(), "$.orderId");

            org.assertj.core.api.Assertions.assertThat(firstResult.getResponse().getStatus()).isEqualTo(201);
            org.assertj.core.api.Assertions.assertThat(firstId).isEqualTo(secondId);
            verify(pricingClient, times(1)).evaluate(any());
            org.assertj.core.api.Assertions.assertThat(repository.count()).isEqualTo(1);
        } finally {
            finishPricing.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void persistsEligibilityRejectionAsRejectedOrder() throws Exception {
        when(pricingClient.evaluate(any())).thenReturn(new PricingEvaluationResponse(UUID.randomUUID(), false,
                null, List.of(), "WEIGHT_LIMIT_EXCEEDED",
                "The maximum supported parcel weight is 20000 grams.", OffsetDateTime.now()));

        mvc.perform(post("/api/v1/delivery-orders")
                        .header("Idempotency-Key", "order-key-rejected")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerReference":"DEMO-ORDER-REJECTED",
                                 "sender":{"name":"Sender Example","postalCode":"1012AB","countryCode":"NL"},
                                 "recipient":{"name":"Recipient Example","postalCode":"5611EM","countryCode":"NL"},
                                 "parcel":{"weightInGrams":20001},"deliveryType":"STANDARD"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionCode").value("WEIGHT_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.price").doesNotExist());
    }
}
