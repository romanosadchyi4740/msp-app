package com.romanosadchyi.smp.music_app.sync.internal.dto;

/**
 * A clock sample request (KAN-41), sent to {@code /app/time}.
 *
 * @param clientTime the client's clock in epoch milliseconds when it sent the request; echoed back unchanged
 */
public record TimeSyncRequest(Long clientTime) {
}
