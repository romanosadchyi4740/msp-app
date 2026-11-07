package com.romanosadchyi.smp.music_app.sync.internal.dto;

/**
 * The answer to a {@link TimeSyncRequest}, sent to the asking session's {@code /user/queue/time}. The client takes
 * {@code offset = serverTime - (clientTime + receiveTime) / 2} and keeps the sample with the smallest round trip.
 *
 * @param clientTime the request's {@code clientTime}, so the client can match the answer to its request
 * @param serverTime the server clock in epoch milliseconds when the request was handled
 */
public record TimeSyncResponse(Long clientTime, long serverTime) {
}
