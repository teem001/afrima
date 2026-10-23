package com.afrismart.shared.config;

import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatUploadConfig {

    private static final int MAX_POST_BYTES = 10 * 1024 * 1024;

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatMaxPostSize() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            connector.setMaxPostSize(MAX_POST_BYTES);
            if (connector.getProtocolHandler() instanceof AbstractHttp11Protocol<?> protocol) {
                protocol.setMaxSwallowSize(MAX_POST_BYTES);
            }
        });
    }
}
