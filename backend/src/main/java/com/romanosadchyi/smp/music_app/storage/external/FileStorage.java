package com.romanosadchyi.smp.music_app.storage.external;

import org.springframework.web.multipart.MultipartFile;

/**
 * Object storage for uploaded files. Files are addressed by a key such as {@code tracks/<id>.mp3}; clients never
 * read the store directly but get a time-limited presigned URL.
 */
public interface FileStorage {

    /**
     * Stores the file under {@code key}, replacing an existing object.
     *
     * @throws StorageException if the file cannot be stored
     */
    void upload(MultipartFile file, String key);

    /**
     * A presigned GET URL for the object, valid for {@code minio.presigned-url-expiry-seconds}.
     *
     * @throws StorageException if the URL cannot be generated
     */
    String presignedGetUrl(String key);

    /**
     * Deletes the object. A blank key or {@code PENDING} (a track whose upload has not finished) is ignored, and
     * a failure is logged, not thrown, so deleting the database rows still succeeds.
     */
    void delete(String key);
}
