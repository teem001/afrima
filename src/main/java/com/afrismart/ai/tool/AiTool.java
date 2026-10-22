package com.afrismart.ai.tool;

import java.util.Set;

import com.afrismart.shared.security.Role;

public interface AiTool {

    AiToolType type();

    String description();

    JsonSchema parameters();

    /** Roles whose assistant is offered this tool; the consumer and merchant agents see different sets. */
    Set<Role> roles();

    ToolResult execute(ToolArgs args, AiToolContext context);

    default ToolDefinition definition() {
        return new ToolDefinition(type().name(), description(), parameters());
    }
}
