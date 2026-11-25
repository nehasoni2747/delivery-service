package nl.delivery.pricing.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PricingControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void returnsBusinessRejectionAsSuccessfulEvaluation() throws Exception {
        mvc.perform(post("/api/v1/pricing/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"7cb82db3-fe9a-49e1-a0f9-fdeae2455869",
                                 "originPostalCode":"1012AB","destinationPostalCode":"5611EM",
                                 "weightInGrams":20001,"deliveryType":"STANDARD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.rejectionCode").value("WEIGHT_LIMIT_EXCEEDED"));
    }

    @Test
    void reportsInvalidDutchPostalCodeAsProblemDetails() throws Exception {
        mvc.perform(post("/api/v1/pricing/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"7cb82db3-fe9a-49e1-a0f9-fdeae2455869",
                                 "originPostalCode":"not-a-postcode","destinationPostalCode":"5611EM",
                                 "weightInGrams":100,"deliveryType":"STANDARD"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.originPostalCode").exists());
    }
}
