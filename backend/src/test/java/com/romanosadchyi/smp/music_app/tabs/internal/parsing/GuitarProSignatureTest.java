package com.romanosadchyi.smp.music_app.tabs.internal.parsing;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class GuitarProSignatureTest {

    @Test
    void matches_Gp5Header_IsAccepted() {
        assertThat(GuitarProSignature.matches("gp5", gp5())).isTrue();
    }

    @Test
    void matches_OlderGuitarProHeaderOrOtherContentAsGp5_IsRejected() {
        assertThat(GuitarProSignature.matches("gp5", header("FICHIER GUITAR PRO v4.06"))).isFalse();
        assertThat(GuitarProSignature.matches("gp5", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(GuitarProSignature.matches("gp5", new byte[0])).isFalse();
    }

    @Test
    void matches_GpxContainer_IsAcceptedCompressedOrNot() {
        assertThat(GuitarProSignature.matches("gpx", "BCFZ....".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(GuitarProSignature.matches("gpx", "BCFS....".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(GuitarProSignature.matches("gpx", "BCF".getBytes(StandardCharsets.US_ASCII))).isFalse();
    }

    @Test
    void matches_ZipWithAGuitarProScore_IsAcceptedAsGp() throws IOException {
        assertThat(GuitarProSignature.matches("gp", zip("Content/score.gpif"))).isTrue();
    }

    @Test
    void matches_OtherZip_IsRejectedAsGp() throws IOException {
        assertThat(GuitarProSignature.matches("gp", zip("word/document.xml"))).isFalse();
    }

    @Test
    void matches_ContentOfAnotherVersion_IsRejected() throws IOException {
        assertThat(GuitarProSignature.matches("gpx", gp5())).isFalse();
        assertThat(GuitarProSignature.matches("gp5", zip("Content/score.gpif"))).isFalse();
        assertThat(GuitarProSignature.matches("gp3", gp5())).isFalse();
    }

    /**
     * The start of a Guitar Pro 5 file: the length of the version string, then the string.
     */
    static byte[] gp5() {
        return header("FICHIER GUITAR PRO v5.00");
    }

    private static byte[] header(String version) {
        byte[] text = version.getBytes(StandardCharsets.US_ASCII);
        byte[] content = new byte[31];
        content[0] = (byte) text.length;
        System.arraycopy(text, 0, content, 1, text.length);
        return content;
    }

    static byte[] zip(String entry) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write("<GPIF/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
