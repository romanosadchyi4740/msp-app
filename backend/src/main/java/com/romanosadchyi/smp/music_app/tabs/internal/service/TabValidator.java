package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.tabs.internal.parsing.GuitarProSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks tab input before anything is stored. Every rejection is an {@link IllegalArgumentException} (400).
 */
@Component
public class TabValidator {

    static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    static final Set<String> GUITAR_PRO_EXTENSIONS = Set.of("gp", "gp5", "gpx");
    static final int MAX_TEXT_LENGTH = 255;
    static final int MAX_RAW_TEXT_LENGTH = 100_000;

    /**
     * Beats per measure, then a note value that is a power of two: {@code 4/4}, {@code 6/8}, {@code 12/16}.
     */
    static final Pattern TIME_SIGNATURE = Pattern.compile("([1-9]|[1-9][0-9])/(1|2|4|8|16|32)");

    /**
     * Uploads are only allowed for material the user owns or that is in the public domain (R14).
     */
    public void requireRightsConfirmed(Boolean rightsConfirmed) {
        if (!Boolean.TRUE.equals(rightsConfirmed)) {
            throw new IllegalArgumentException(
                    "Confirm that you own this material or that it is in the public domain");
        }
    }

    public void validateMetadata(String title, String artist, Integer tempoBpm, String timeSignature) {
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("Title is required");
        }
        validateTextLengths(title, artist);
        validateRhythm(tempoBpm, timeSignature);
    }

    /**
     * Title and artist, both optional here, are at most 255 characters long.
     */
    public void validateTextLengths(String title, String artist) {
        if ((title != null && title.length() > MAX_TEXT_LENGTH)
                || (artist != null && artist.length() > MAX_TEXT_LENGTH)) {
            throw new IllegalArgumentException("Title and artist can be at most 255 characters long");
        }
    }

    /**
     * The tempo and time signature, both optional.
     */
    public void validateRhythm(Integer tempoBpm, String timeSignature) {
        if (tempoBpm != null && tempoBpm <= 0) {
            throw new IllegalArgumentException("Tempo must be a positive number of beats per minute");
        }
        if (timeSignature != null && !TIME_SIGNATURE.matcher(timeSignature).matches()) {
            throw new IllegalArgumentException("Time signature must look like 4/4 or 6/8");
        }
    }

    /**
     * Only checks presence and size; whether the text is a tab is decided by parsing it.
     */
    public void validateRawText(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            throw new IllegalArgumentException("Tab text is required");
        }
        if (rawText.length() > MAX_RAW_TEXT_LENGTH) {
            throw new IllegalArgumentException("Tab text can be at most 100000 characters long");
        }
    }

    /**
     * Checks size, extension and that the content is the Guitar Pro format the extension names.
     *
     * @return the file's extension in lower case, which names the Guitar Pro version
     */
    public String validateGuitarProFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A Guitar Pro file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("A Guitar Pro file can be at most 10 MB");
        }
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String normalized = extension != null ? extension.toLowerCase(Locale.ROOT) : "";
        if (!GUITAR_PRO_EXTENSIONS.contains(normalized)) {
            throw new IllegalArgumentException("Only Guitar Pro files (.gp, .gp5, .gpx) can be uploaded");
        }
        if (!GuitarProSignature.matches(normalized, readContent(file))) {
            throw new IllegalArgumentException("The file is not a valid ." + normalized + " Guitar Pro file");
        }
        return normalized;
    }

    /**
     * A read failure is the server's, not the client's, so it is not a 400.
     */
    private static byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("The uploaded file could not be read", e);
        }
    }
}
