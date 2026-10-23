package com.afrismart.shared.media;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class MediaViews {

    private MediaViews() {
    }

    public record UploadRequest(
            @NotBlank @Size(max = 128) String contentType,
            /** Raw base64 or a full data URL (data:image/jpeg;base64,...). */
            @NotBlank String data
    ) {
    }

    public record UploadResponse(UUID id, String url, String contentType, int byteSize) {
    }
}
