package com.ticketing.file;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.api.exceptions.NotFound;
import com.cloudinary.utils.ObjectUtils;

/**
 * The only class that knows Cloudinary exists. Everything it returns is expressed in the app's own
 * terms, so replacing this file replaces the provider.
 */
@Component
// only wired when credentials are configured; blank keys mean no provider at all, keeping tests offline
@ConditionalOnExpression("'${app.cloudinary.api-key:}'.length() > 0")
class CloudinaryObjectStorage implements ObjectStorage {

    private static final String DEFAULT_UPLOAD_PREFIX = "https://api.cloudinary.com";
    private static final Map<String, String> MIME_BY_FORMAT = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");

    private final Cloudinary cloudinary;

    CloudinaryObjectStorage(CloudinaryProperties properties) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", properties.cloudName(),
                "api_key", properties.apiKey(),
                "api_secret", properties.apiSecret(),
                "secure", true));
    }

    @Override
    public SignedUpload signUpload(String publicId, String folder) {
        long timestamp = System.currentTimeMillis() / 1000L;
        Map<String, Object> toSign = ObjectUtils.asMap(
                "public_id", publicId, "folder", folder, "timestamp", timestamp);
        String signature = cloudinary.apiSignRequest(
                toSign, cloudinary.config.apiSecret, cloudinary.config.signatureVersion);
        String prefix = cloudinary.config.uploadPrefix != null ? cloudinary.config.uploadPrefix : DEFAULT_UPLOAD_PREFIX;
        String uploadUrl = prefix + "/v1_1/" + cloudinary.config.cloudName + "/image/upload";
        return new SignedUpload(uploadUrl, cloudinary.config.apiKey, timestamp, publicId, folder, signature);
    }

    @Override
    public Optional<StoredObject> find(String publicId) {
        try {
            Map<?, ?> resource = cloudinary.api().resource(publicId, ObjectUtils.emptyMap());
            return Optional.of(new StoredObject(
                    mimeFor(String.valueOf(resource.get("format"))),
                    ((Number) resource.get("bytes")).longValue()));
        } catch (NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException("Could not look up stored object " + publicId, e);
        }
    }

    @Override
    public void destroy(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            throw new IllegalStateException("Could not destroy stored object " + publicId, e);
        }
    }

    @Override
    public String imageUrl(String publicId) {
        return cloudinary.url().secure(true)
                .transformation(new Transformation().quality("auto").fetchFormat("auto"))
                .generate(publicId);
    }

    private String mimeFor(String format) {
        return MIME_BY_FORMAT.getOrDefault(format, "image/" + format);
    }
}
