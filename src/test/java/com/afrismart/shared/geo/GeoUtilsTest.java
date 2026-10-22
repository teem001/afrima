package com.afrismart.shared.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoUtilsTest {

    @Test
    void distanceBetweenYabaAndIkejaIsAboutTenKilometres() {
        double meters = GeoUtils.distanceMeters(6.5095, 3.3711, 6.6018, 3.3515);
        assertThat(meters).isCloseTo(10_490, within(150.0));
    }

    @Test
    void distanceToSelfIsZero() {
        assertThat(GeoUtils.distanceMeters(6.5, 3.3, 6.5, 3.3)).isZero();
    }

    @Test
    void boundingBoxContainsEveryPointWithinRadius() {
        double lat = 6.5158;
        double lng = 3.3791;
        GeoUtils.BoundingBox box = GeoUtils.boundingBox(lat, lng, 5_000);
        assertThat(GeoUtils.distanceMeters(lat, lng, box.maxLat(), lng)).isCloseTo(5_000, within(1.0));
        assertThat(GeoUtils.distanceMeters(lat, lng, lat, box.maxLng())).isCloseTo(5_000, within(1.0));
        assertThat(box.minLat()).isLessThan(lat);
        assertThat(box.minLng()).isLessThan(lng);
    }

    @Test
    void roundKmKeepsOneDecimal() {
        assertThat(GeoUtils.roundKm(1_234)).isEqualTo(1.2);
        assertThat(GeoUtils.roundKm(1_250)).isEqualTo(1.3);
    }
}
