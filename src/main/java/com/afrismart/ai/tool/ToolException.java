package com.afrismart.ai.tool;

import com.afrismart.shared.api.ErrorCodes;

public class ToolException extends RuntimeException {

    private final String code;

    public ToolException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static ToolException invalidArgument(String message) {
        return new ToolException(ErrorCodes.INVALID_TOOL_ARGUMENTS, message);
    }

    public String getCode() {
        return code;
    }
}
