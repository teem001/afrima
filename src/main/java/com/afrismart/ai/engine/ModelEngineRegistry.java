package com.afrismart.ai.engine;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.afrismart.ai.AiProperties;
import com.afrismart.shared.api.ApiException;
import com.afrismart.shared.api.ErrorCodes;

@Component
public class ModelEngineRegistry {

    private final List<ModelEngine> engines;
    private final AiProperties properties;

    public ModelEngineRegistry(List<ModelEngine> engines, AiProperties properties) {
        this.engines = engines;
        this.properties = properties;
    }

    public ModelEngine active() {
        return find().orElseThrow(() -> new ApiException(
                ErrorCodes.AI_NOT_CONFIGURED,
                "AI engine " + properties.getEngine() + " is not configured. Set OPENAI_API_KEY and restart.",
                HttpStatus.SERVICE_UNAVAILABLE
        ));
    }

    public Optional<ModelEngine> find() {
        return engines.stream()
                .filter(engine -> engine.supports(properties.getEngine()))
                .filter(ModelEngine::isConfigured)
                .findFirst();
    }
}
