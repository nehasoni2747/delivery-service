package nl.delivery.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import nl.delivery.order.api.CreateOrderRequest;
import nl.delivery.order.domain.DeliveryType;

class PricingClientTest {
    @Test
    void sendsJsonOverHttpAndDeserializesPricingResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new PricingClient(builder.baseUrl("http://pricing").build());
        server.expect(requestTo("http://pricing/api/v1/pricing/evaluations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(httpRequest -> {
                    var sent = new ObjectMapper().readTree(
                            ((MockClientHttpRequest) httpRequest).getBodyAsBytes());
                    UUID requestId = UUID.fromString(sent.get("requestId").asText());
                    return withSuccess("""
                            {"requestId":"%s","eligible":true,
                             "price":{"amount":4.95,"currency":"EUR"},
                             "priceComponents":[{"type":"BASE_PRICE","amount":4.95}],
                             "evaluatedAt":"2026-11-25T10:00:00Z"}
                            """.formatted(requestId), MediaType.APPLICATION_JSON).createResponse(httpRequest);
                });

        var request = new CreateOrderRequest("DEMO-1",
                new CreateOrderRequest.Party("Sender", "1012 AB", "NL"),
                new CreateOrderRequest.Party("Recipient", "5611EM", "NL"),
                new CreateOrderRequest.Parcel(500), DeliveryType.STANDARD);
        var response = client.evaluate(request);

        assertThat(response.eligible()).isTrue();
        assertThat(response.price().currency()).isEqualTo("EUR");
        server.verify();
    }
}
