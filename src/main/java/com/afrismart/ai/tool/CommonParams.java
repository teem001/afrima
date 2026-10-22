package com.afrismart.ai.tool;

public final class CommonParams {

    public static final JsonSchema LATITUDE =
            JsonSchema.number("Latitude to search around. Omit to use the user's device location.");
    public static final JsonSchema LONGITUDE =
            JsonSchema.number("Longitude to search around. Omit to use the user's device location.");
    public static final JsonSchema RADIUS =
            JsonSchema.integer("Search radius in meters. Default 15000.");

    private CommonParams() {
    }
}
