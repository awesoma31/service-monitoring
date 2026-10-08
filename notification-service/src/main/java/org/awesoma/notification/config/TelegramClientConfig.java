package org.awesoma.notification.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

@Configuration
public class TelegramClientConfig {

    @Bean
    @Qualifier("telegramWebClient")
    WebClient telegramWebClient(TelegramProperties properties) {
        HttpClient client = HttpClient.create()
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(properties.requestTimeout().toMillis()))
                .responseTimeout(properties.requestTimeout())
                .doOnConnected(connection -> connection.addHandlerLast(new ReadTimeoutHandler(
                        properties.requestTimeout().toMillis(), TimeUnit.MILLISECONDS)));

        if (StringUtils.hasText(properties.proxyHost())) {
            client = client.proxy(proxy -> proxy
                    .type(ProxyProvider.Proxy.HTTP)
                    .host(properties.proxyHost())
                    .port(properties.proxyPort()));
        }

        return WebClient.builder()
                .baseUrl(properties.apiBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(client))
                .build();
    }
}
