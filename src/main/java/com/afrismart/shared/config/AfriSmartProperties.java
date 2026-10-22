package com.afrismart.shared.config;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "afrismart")
public class AfriSmartProperties {

    private final Jwt jwt = new Jwt();
    private final Search search = new Search();
    private final Clock clock = new Clock();

    public Jwt getJwt() {
        return jwt;
    }

    public Search getSearch() {
        return search;
    }

    public Clock getClock() {
        return clock;
    }

    public static class Jwt {
        private String issuer = "AfriSmart";
        private String secret = "";
        private Duration ttl = Duration.ofHours(24);

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }
    }

    public static class Search {
        private int defaultRadiusMeters = 15_000;
        private int maxResults = 50;

        public int getDefaultRadiusMeters() {
            return defaultRadiusMeters;
        }

        public void setDefaultRadiusMeters(int defaultRadiusMeters) {
            this.defaultRadiusMeters = defaultRadiusMeters;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }
    }

    public static class Clock {
        private String zone = "Africa/Lagos";

        public String getZone() {
            return zone;
        }

        public void setZone(String zone) {
            this.zone = zone;
        }

        public ZoneId zoneId() {
            return ZoneId.of(zone);
        }
    }
}
