package com.afrismart.shared.media;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.afrismart.shared.security.AuthUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaViews.UploadResponse upload(@Valid @RequestBody MediaViews.UploadRequest request) {
        return media.upload(AuthUser.require(), request);
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<byte[]> get(@PathVariable UUID mediaId) {
        MediaAsset asset = media.require(mediaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .contentLength(asset.getByteSize())
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(asset.getData());
    }
}
