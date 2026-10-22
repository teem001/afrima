package com.afrismart.ai.tool;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.afrismart.shared.api.ApiException;
import com.afrismart.shared.api.ErrorCodes;
import com.afrismart.shared.security.Role;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AiToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(AiToolRegistry.class);
    private static final TypeReference<Map<String, Object>> ARGS_TYPE = new TypeReference<>() {
    };

    private final List<AiTool> tools;
    private final ObjectMapper objectMapper;

    public AiToolRegistry(List<AiTool> tools, ObjectMapper objectMapper) {
        this.tools = tools.stream().sorted(Comparator.comparing(AiTool::type)).toList();
        this.objectMapper = objectMapper;
    }

    public List<ToolDefinition> definitionsFor(Role role) {
        return tools.stream()
                .filter(tool -> tool.roles().contains(role))
                .map(AiTool::definition)
                .toList();
    }

    public Optional<AiTool> availableTo(String name, Role role) {
        return find(name).filter(tool -> tool.roles().contains(role));
    }

    public ToolExecution invokeJson(String name, String argumentsJson, AiToolContext context) {
        Map<String, Object> arguments;
        try {
            arguments = argumentsJson == null || argumentsJson.isBlank()
                    ? Map.of()
                    : objectMapper.readValue(argumentsJson, ARGS_TYPE);
        } catch (JsonProcessingException ex) {
            return new ToolExecution(name, Map.of(), ToolResult.error(
                    ErrorCodes.INVALID_TOOL_ARGUMENTS, "Arguments were not valid JSON"
            ));
        }
        return invoke(name, arguments, context);
    }

    public ToolExecution invoke(String name, Map<String, Object> arguments, AiToolContext context) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        Optional<AiTool> tool = availableTo(name, context.user().role());
        if (tool.isEmpty()) {
            return new ToolExecution(name, args, ToolResult.error(
                    ErrorCodes.TOOL_NOT_AVAILABLE, "Tool " + name + " is not available to this user"
            ));
        }
        try {
            return new ToolExecution(tool.get().type().name(), args, tool.get().execute(new ToolArgs(args), context));
        } catch (ToolException ex) {
            return new ToolExecution(name, args, ToolResult.error(ex.getCode(), ex.getMessage()));
        } catch (ApiException ex) {
            return new ToolExecution(name, args, ToolResult.error(ex.getCode(), ex.getMessage()));
        } catch (RuntimeException ex) {
            log.warn("Tool {} failed", name, ex);
            return new ToolExecution(name, args, ToolResult.error(ErrorCodes.TOOL_FAILED, "The tool failed unexpectedly"));
        }
    }

    private Optional<AiTool> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        return tools.stream().filter(tool -> tool.type().name().equals(normalized)).findFirst();
    }
}
