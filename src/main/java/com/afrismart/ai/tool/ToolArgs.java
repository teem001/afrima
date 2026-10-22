package com.afrismart.ai.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ToolArgs {

    private final Map<String, Object> values;

    public ToolArgs(Map<String, Object> values) {
        this.values = values == null ? Map.of() : values;
    }

    public String string(String name) {
        Object value = values.get(name);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    public String requireString(String name) {
        String value = string(name);
        if (value == null) {
            throw ToolException.invalidArgument(name + " is required");
        }
        return value;
    }

    public Double decimal(String name) {
        Object value = values.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            throw ToolException.invalidArgument(name + " must be a number");
        }
    }

    public int integer(String name, int fallback) {
        Double value = decimal(name);
        return value == null ? fallback : (int) Math.round(value);
    }

    public UUID uuid(String name) {
        String value = string(name);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw ToolException.invalidArgument(name + " must be an id returned by an earlier tool call");
        }
    }

    public UUID requireUuid(String name) {
        UUID value = uuid(name);
        if (value == null) {
            throw ToolException.invalidArgument(name + " is required");
        }
        return value;
    }

    public List<UUID> uuids(String name) {
        Object value = values.get(name);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw ToolException.invalidArgument(name + " must be a list of ids");
        }
        return list.stream()
                .map(item -> new ToolArgs(Map.of("id", item)).requireUuid("id"))
                .toList();
    }

    public List<ToolArgs> objects(String name) {
        Object value = values.get(name);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw ToolException.invalidArgument(name + " must be a list of objects");
        }
        return list.stream().map(item -> {
            if (!(item instanceof Map<?, ?> map)) {
                throw ToolException.invalidArgument(name + " must be a list of objects");
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, val) -> copy.put(String.valueOf(key), val));
            return new ToolArgs(copy);
        }).toList();
    }
}
