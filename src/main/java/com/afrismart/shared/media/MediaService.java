package com.afrismart.shared.media;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.afrismart.shared.api.ApiException;
import com.afrismart.shared.api.ErrorCodes;
import com.afrismart.shared.security.AuthUser;

@Service
public class MediaService {

    public static final int MAX_IMAGE_BYTES = 800_000;
    public static final int MAX_VIDEO_BYTES = 2_500_000;
    private static final int MAX_IMAGE_EDGE = 1280;
    private static final float JPEG_QUALITY = 0.72f;

    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif"
    );
    private static final Set<String> VIDEO_TYPES = Set.of(
            "video/webm", "video/mp4", "video/quicktime"
    );

    private final MediaAssetRepository assets;

    public MediaService(MediaAssetRepository assets) {
        this.assets = assets;
    }

    @Transactional
    public MediaViews.UploadResponse upload(AuthUser user, MediaViews.UploadRequest request) {
        ParsedMedia parsed = parse(request.contentType(), request.data());
        MediaAsset asset = new MediaAsset();
        asset.setOwnerId(user.id());
        asset.setContentType(parsed.contentType());
        asset.setByteSize(parsed.bytes().length);
        asset.setData(parsed.bytes());
        assets.save(asset);
        return toResponse(asset);
    }

    @Transactional(readOnly = true)
    public MediaAsset require(UUID mediaId) {
        return assets.findById(mediaId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.MEDIA_NOT_FOUND, "Media not found"));
    }

    public String urlFor(UUID mediaId) {
        return mediaId == null ? null : "/api/v1/media/" + mediaId;
    }

    public MediaViews.UploadResponse toResponse(MediaAsset asset) {
        return new MediaViews.UploadResponse(
                asset.getId(),
                urlFor(asset.getId()),
                asset.getContentType(),
                asset.getByteSize()
        );
    }

    private ParsedMedia parse(String declaredType, String raw) {
        String payload = raw == null ? "" : raw.trim();
        String contentType = declaredType == null ? "" : declaredType.trim().toLowerCase(Locale.ROOT);
        if (payload.startsWith("data:")) {
            int comma = payload.indexOf(',');
            if (comma < 0) {
                throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Invalid media data URL");
            }
            String header = payload.substring(5, comma);
            int semi = header.indexOf(';');
            contentType = (semi < 0 ? header : header.substring(0, semi)).trim().toLowerCase(Locale.ROOT);
            payload = payload.substring(comma + 1);
        }
        if ("image/jpg".equals(contentType)) {
            contentType = "image/jpeg";
        }
        if ("video/quicktime".equals(contentType)) {
            contentType = "video/mp4";
        }

        boolean image = IMAGE_TYPES.contains(contentType);
        boolean video = VIDEO_TYPES.contains(contentType) || "video/mp4".equals(contentType) || "video/webm".equals(contentType);
        if (!image && !video) {
            throw ApiException.badRequest(
                    ErrorCodes.VALIDATION_FAILED,
                    "Only JPEG, PNG, WebP, GIF photos or WebM/MP4 videos are allowed"
            );
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(payload.replaceAll("\\s", ""));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Media data is not valid base64");
        }
        if (bytes.length == 0) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Media is empty");
        }

        if (image) {
            // Client already JPEG-compresses; skip AWT recompress when under the cap to avoid OOM on tiny Vercel instances.
            if (!("image/jpeg".equals(contentType) && bytes.length <= MAX_IMAGE_BYTES)
                    && !("image/gif".equals(contentType) && bytes.length <= MAX_IMAGE_BYTES)) {
                bytes = recompressImage(bytes, contentType);
                contentType = "image/jpeg";
            }
            if (bytes.length > MAX_IMAGE_BYTES) {
                throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Image must be under 800 KB after compression");
            }
        } else if (bytes.length > MAX_VIDEO_BYTES) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Video must be under 2.5 MB after compression");
        }

        return new ParsedMedia(contentType, bytes);
    }

    /** Second-pass shrink so oversized phone photos still fit even if the client skips compression. */
    private byte[] recompressImage(byte[] original, String contentType) {
        if ("image/gif".equals(contentType) && original.length <= MAX_IMAGE_BYTES) {
            return original;
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
            if (source == null) {
                if (original.length <= MAX_IMAGE_BYTES) {
                    return original;
                }
                throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Could not read image for compression");
            }
            int w = source.getWidth();
            int h = source.getHeight();
            double scale = Math.min(1.0, (double) MAX_IMAGE_EDGE / Math.max(w, h));
            int tw = Math.max(1, (int) Math.round(w * scale));
            int th = Math.max(1, (int) Math.round(h * scale));

            BufferedImage rgb = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, tw, th, null);
            g.dispose();

            float quality = JPEG_QUALITY;
            byte[] encoded = encodeJpeg(rgb, quality);
            while (encoded.length > MAX_IMAGE_BYTES && quality > 0.4f) {
                quality -= 0.08f;
                encoded = encodeJpeg(rgb, quality);
            }
            if (encoded.length > MAX_IMAGE_BYTES && Math.max(tw, th) > 960) {
                double tighter = 960.0 / Math.max(tw, th);
                int nw = Math.max(1, (int) Math.round(tw * tighter));
                int nh = Math.max(1, (int) Math.round(th * tighter));
                BufferedImage smaller = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = smaller.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(rgb, 0, 0, nw, nh, null);
                g2.dispose();
                encoded = encodeJpeg(smaller, 0.65f);
            }
            // Prefer the smaller of original vs recompressed when both fit; otherwise keep recompressed.
            if (encoded.length <= MAX_IMAGE_BYTES
                    && original.length <= MAX_IMAGE_BYTES
                    && original.length <= encoded.length
                    && "image/jpeg".equals(contentType)) {
                return original;
            }
            return encoded;
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            if (original.length <= MAX_IMAGE_BYTES) {
                return original;
            }
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Could not compress image");
        }
    }

    private static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writer available");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.max(0.05f, Math.min(1f, quality)));
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private record ParsedMedia(String contentType, byte[] bytes) {
    }
}
