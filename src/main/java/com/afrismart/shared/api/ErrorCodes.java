package com.afrismart.shared.api;

public final class ErrorCodes {

    public static final String EMAIL_TAKEN = "EMAIL_TAKEN";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String STORE_NOT_FOUND = "STORE_NOT_FOUND";
    public static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
    public static final String MEDIA_NOT_FOUND = "MEDIA_NOT_FOUND";
    public static final String SKU_TAKEN = "SKU_TAKEN";
    public static final String FORBIDDEN_STORE = "FORBIDDEN_STORE";
    public static final String INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";
    public static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    public static final String INVALID_ORDER_STATE = "INVALID_ORDER_STATE";
    public static final String EMPTY_ORDER = "EMPTY_ORDER";
    public static final String CONVERSATION_NOT_FOUND = "CONVERSATION_NOT_FOUND";
    public static final String AI_NOT_CONFIGURED = "AI_NOT_CONFIGURED";
    public static final String AI_ENGINE_ERROR = "AI_ENGINE_ERROR";
    public static final String TOOL_NOT_AVAILABLE = "TOOL_NOT_AVAILABLE";
    public static final String TOOL_FAILED = "TOOL_FAILED";
    public static final String INVALID_TOOL_ARGUMENTS = "INVALID_TOOL_ARGUMENTS";
    public static final String LOCATION_REQUIRED = "LOCATION_REQUIRED";

    private ErrorCodes() {
    }
}
