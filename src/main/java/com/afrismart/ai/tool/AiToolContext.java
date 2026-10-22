package com.afrismart.ai.tool;

import java.util.Optional;

import com.afrismart.shared.api.ErrorCodes;
import com.afrismart.shared.security.AuthUser;

public record AiToolContext(AuthUser user, Double latitude, Double longitude) {

    public GeoPoint requireLocation(ToolArgs args) {
        return location(args).orElseThrow(() -> new ToolException(
                ErrorCodes.LOCATION_REQUIRED,
                "The user's location is unknown. Ask them to share their location."
        ));
    }

    /** Device location wins over model-supplied coordinates, which the model cannot verify. */
    public Optional<GeoPoint> location(ToolArgs args) {
        if (latitude != null && longitude != null) {
            return Optional.of(new GeoPoint(latitude, longitude));
        }
        Double lat = args.decimal("latitude");
        Double lng = args.decimal("longitude");
        if (lat == null || lng == null) {
            return Optional.empty();
        }
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw ToolException.invalidArgument("latitude/longitude are out of range");
        }
        return Optional.of(new GeoPoint(lat, lng));
    }

    public record GeoPoint(double latitude, double longitude) {
    }
}
