package com.romanosadchyi.smp.music_app.tabs.internal.parsing;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Checks that a file's content is the Guitar Pro format its extension names. The browser parses the file itself;
 * this only keeps other files out of storage.
 * <ul>
 *     <li>{@code .gp5}: a length byte, then {@code FICHIER GUITAR PRO v5};</li>
 *     <li>{@code .gpx} (Guitar Pro 6): a {@code BCFZ} or {@code BCFS} container;</li>
 *     <li>{@code .gp} (Guitar Pro 7 and later): a ZIP archive with {@code Content/score.gpif}. Entry names are
 *     stored uncompressed, so nothing is unpacked.</li>
 * </ul>
 */
public final class GuitarProSignature {

    private static final byte[] GP5_HEADER = "FICHIER GUITAR PRO v5".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GPX_COMPRESSED = "BCFZ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GPX_UNCOMPRESSED = "BCFS".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_HEADER = {'P', 'K', 3, 4};
    private static final byte[] GP_SCORE_ENTRY = "Content/score.gpif".getBytes(StandardCharsets.US_ASCII);

    private GuitarProSignature() {
    }

    /**
     * @param extension the file extension in lower case: {@code gp}, {@code gp5} or {@code gpx}
     */
    public static boolean matches(String extension, byte[] content) {
        return switch (extension) {
            case "gp5" -> content.length > 0 && content[0] >= GP5_HEADER.length
                    && startsWith(content, 1, GP5_HEADER);
            case "gpx" -> startsWith(content, 0, GPX_COMPRESSED) || startsWith(content, 0, GPX_UNCOMPRESSED);
            case "gp" -> startsWith(content, 0, ZIP_HEADER) && contains(content, GP_SCORE_ENTRY);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] content, int offset, byte[] prefix) {
        return content.length >= offset + prefix.length
                && Arrays.equals(content, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    private static boolean contains(byte[] content, byte[] part) {
        for (int i = 0; i + part.length <= content.length; i++) {
            if (Arrays.equals(content, i, i + part.length, part, 0, part.length)) {
                return true;
            }
        }
        return false;
    }
}
