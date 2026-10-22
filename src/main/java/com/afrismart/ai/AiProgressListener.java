package com.afrismart.ai;

/** Lets a streaming transport show the agent's steps while {@link AiAgentService#chat} is still running. */
public interface AiProgressListener {

    AiProgressListener NONE = new AiProgressListener() {
    };

    default void conversationOpened(java.util.UUID conversationId) {
    }

    default void modelTurn(int round) {
    }

    default void toolStarted(String tool) {
    }

    default void toolFinished(String tool, boolean success, String message) {
    }
}
