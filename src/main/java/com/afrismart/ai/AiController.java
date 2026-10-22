package com.afrismart.ai;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.afrismart.ai.conversation.AiConversationService;
import com.afrismart.ai.engine.ModelEngineRegistry;
import com.afrismart.ai.tool.AiToolContext;
import com.afrismart.ai.tool.AiToolRegistry;
import com.afrismart.ai.tool.ToolDefinition;
import com.afrismart.ai.tool.ToolExecution;
import com.afrismart.shared.api.ApiException;
import com.afrismart.shared.api.ErrorCodes;
import com.afrismart.shared.security.AuthUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiAgentService agentService;
    private final AiToolRegistry toolRegistry;
    private final AiConversationService conversationService;
    private final ModelEngineRegistry engines;
    private final AiProperties properties;

    public AiController(
            AiAgentService agentService,
            AiToolRegistry toolRegistry,
            AiConversationService conversationService,
            ModelEngineRegistry engines,
            AiProperties properties
    ) {
        this.agentService = agentService;
        this.toolRegistry = toolRegistry;
        this.conversationService = conversationService;
        this.engines = engines;
        this.properties = properties;
    }

    @GetMapping("/status")
    public AiStatus status() {
        return engines.find()
                .map(engine -> new AiStatus(properties.getEngine().name(), true, engine.model()))
                .orElseGet(() -> new AiStatus(properties.getEngine().name(), false, null));
    }

    @GetMapping("/tools")
    public List<ToolDefinition> tools() {
        return toolRegistry.definitionsFor(AuthUser.require().role());
    }

    @PostMapping("/tools/{name}/invoke")
    public ToolExecution invoke(@PathVariable String name, @Valid @RequestBody(required = false) InvokeToolRequest request) {
        AuthUser user = AuthUser.require();
        if (toolRegistry.availableTo(name, user.role()).isEmpty()) {
            throw ApiException.forbidden(ErrorCodes.TOOL_NOT_AVAILABLE, "Tool " + name + " is not available to this user");
        }
        InvokeToolRequest body = request == null ? new InvokeToolRequest(null, null, null) : request;
        return toolRegistry.invoke(name, body.arguments(), new AiToolContext(user, body.latitude(), body.longitude()));
    }

    @PostMapping("/chat")
    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request) {
        return agentService.chat(AuthUser.require(), request);
    }

    @GetMapping("/conversations")
    public List<AiConversationService.ConversationSummary> conversations() {
        return conversationService.list(AuthUser.require());
    }

    @GetMapping("/conversations/{id}/messages")
    public AiConversationService.ConversationDetail messages(@PathVariable UUID id) {
        return conversationService.detail(AuthUser.require(), id);
    }

    public record AiStatus(String engine, boolean configured, String model) {
    }
}
