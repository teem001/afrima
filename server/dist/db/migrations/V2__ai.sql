CREATE TABLE ai_conversations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    title VARCHAR(255) NOT NULL,
    last_message_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_ai_conversations_user ON ai_conversations (user_id, last_message_at);

CREATE TABLE ai_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES ai_conversations (id) ON DELETE CASCADE,
    seq INTEGER NOT NULL,
    role VARCHAR(16) NOT NULL,
    content VARCHAR(20000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (conversation_id, seq)
);

CREATE TABLE ai_tool_invocations (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES ai_conversations (id) ON DELETE CASCADE,
    seq INTEGER NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    arguments_json VARCHAR(4000) NOT NULL,
    result_json VARCHAR(20000) NOT NULL,
    success BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_ai_tool_invocations_conversation ON ai_tool_invocations (conversation_id);
