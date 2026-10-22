package com.afrismart.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.afrismart.ai.engine.AiEngineType;

@ConfigurationProperties(prefix = "afrismart.ai")
public class AiProperties {

    private AiEngineType engine = AiEngineType.OPENAI;
    private int maxToolRounds = 6;
    private int historyWindow = 12;
    private final OpenAi openai = new OpenAi();

    public AiEngineType getEngine() {
        return engine;
    }

    public void setEngine(AiEngineType engine) {
        this.engine = engine;
    }

    public int getMaxToolRounds() {
        return maxToolRounds;
    }

    public void setMaxToolRounds(int maxToolRounds) {
        this.maxToolRounds = maxToolRounds;
    }

    public int getHistoryWindow() {
        return historyWindow;
    }

    public void setHistoryWindow(int historyWindow) {
        this.historyWindow = historyWindow;
    }

    public OpenAi getOpenai() {
        return openai;
    }

    public static class OpenAi {
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "";
        private String model = "gpt-4o-mini";
        private Double temperature;
        private Duration timeout = Duration.ofSeconds(60);

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
}
