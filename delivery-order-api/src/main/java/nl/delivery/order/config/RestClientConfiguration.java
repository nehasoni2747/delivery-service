package nl.delivery.order.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfiguration {
    @Bean
    RestClient pricingRestClient(
            RestClient.Builder builder,
            @Value("${pricing.base-url}") String baseUrl,
            @Value("${pricing.connect-timeout}") Duration connectTimeout,
            @Value("${pricing.response-timeout}") Duration responseTimeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(responseTimeout);
        return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Bean
    java.time.Clock systemClock() {
        return java.time.Clock.systemUTC();
    }
}
