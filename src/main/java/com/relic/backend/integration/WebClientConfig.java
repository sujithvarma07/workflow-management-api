package com.relic.backend.integration;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient externalApiWebClient(
            @Value("${app.external-api.base-url}") String baseUrl,
            @Value("${app.external-api.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${app.external-api.response-timeout-ms:5000}") int responseTimeoutMs) {

        // Both a connection-establishment timeout and a response (read/write)
        // timeout are set explicitly - without the former, a network that
        // silently drops packets (rather than refusing the connection) can
        // hang a request indefinitely; without the latter, a server that
        // accepts the connection but never responds does the same.
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(responseTimeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(responseTimeoutMs, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .build();
    }
}
