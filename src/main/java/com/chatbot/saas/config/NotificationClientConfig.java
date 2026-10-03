package com.chatbot.saas.config;

import com.chatbot.saas.util.PublicAddressGuard;
import io.netty.channel.ChannelOption;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.InetNameResolver;
import io.netty.resolver.InetSocketAddressResolver;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * HTTP client for shops' notification webhooks. The URL is checked when it's saved, but DNS
 * can change afterwards (DNS rebinding), so this client also refuses to connect to non-public
 * addresses at the moment it resolves the host. It never follows redirects.
 */
@Configuration
public class NotificationClientConfig {

    @Bean
    public WebClient notificationWebClient() {
        HttpClient httpClient = HttpClient.create()
                .resolver(PublicOnlyResolverGroup.INSTANCE)
                .followRedirect(false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .responseTimeout(Duration.ofSeconds(10));
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

    static final class PublicOnlyResolverGroup extends AddressResolverGroup<InetSocketAddress> {
        static final PublicOnlyResolverGroup INSTANCE = new PublicOnlyResolverGroup();

        @Override
        protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
            return new InetSocketAddressResolver(executor, new InetNameResolver(executor) {
                @Override
                protected void doResolve(String host, Promise<InetAddress> promise) {
                    try {
                        promise.setSuccess(publicOnly(InetAddress.getAllByName(host)).get(0));
                    } catch (UnknownHostException e) {
                        promise.setFailure(e);
                    }
                }

                @Override
                protected void doResolveAll(String host, Promise<List<InetAddress>> promise) {
                    try {
                        promise.setSuccess(publicOnly(InetAddress.getAllByName(host)));
                    } catch (UnknownHostException e) {
                        promise.setFailure(e);
                    }
                }
            });
        }

        private static List<InetAddress> publicOnly(InetAddress[] addresses) throws UnknownHostException {
            if (Arrays.stream(addresses).anyMatch(a -> !PublicAddressGuard.isPublic(a))) {
                throw new UnknownHostException("Refusing to send a notification to a non-public address");
            }
            return List.of(addresses);
        }
    }
}
