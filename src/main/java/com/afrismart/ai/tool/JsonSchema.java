package com.afrismart.ai.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonSchema(
        String type,
        String description,
        Map<String, JsonSchema> properties,
        List<String> required,
        @JsonProperty("enum") List<String> enumValues,
        JsonSchema items
) {

    public static JsonSchema object(Map<String, JsonSchema> properties, String... required) {
        return new JsonSchema("object", null, new LinkedHashMap<>(properties), List.of(required), null, null);
    }

    public static JsonSchema string(String description) {
        return new JsonSchema("string", description, null, null, null, null);
    }

    public static JsonSchema number(String description) {
        return new JsonSchema("number", description, null, null, null, null);
    }

    public static JsonSchema integer(String description) {
        return new JsonSchema("integer", description, null, null, null, null);
    }

    public static JsonSchema oneOf(String description, List<String> values) {
        return new JsonSchema("string", description, null, null, values, null);
    }

    public static JsonSchema array(String description, JsonSchema items) {
        return new JsonSchema("array", description, null, null, null, items);
    }
}
