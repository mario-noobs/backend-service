package com.mario.backend.audit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "alert")
public class AlertProperties {

    private String recipients = "admin@face-system.local";

    private FailedAuth failedAuth = new FailedAuth();

    private ServerError serverError = new ServerError();

    @Getter
    @Setter
    public static class FailedAuth {
        private int threshold = 5;
        private int windowMinutes = 10;
    }

    @Getter
    @Setter
    public static class ServerError {
        private boolean enabled = true;
    }
}
