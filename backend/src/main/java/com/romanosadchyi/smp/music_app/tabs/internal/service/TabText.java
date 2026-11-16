package com.romanosadchyi.smp.music_app.tabs.internal.service;

/**
 * Normalizes the free text fields of tab input (title, artist) before they are checked and stored.
 */
final class TabText {

    private TabText() {
    }

    static String stripToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
