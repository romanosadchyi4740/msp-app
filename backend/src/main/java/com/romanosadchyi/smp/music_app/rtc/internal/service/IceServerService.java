package com.romanosadchyi.smp.music_app.rtc.internal.service;

import com.romanosadchyi.smp.music_app.rtc.internal.api.model.IceServer;
import com.romanosadchyi.smp.music_app.rtc.internal.api.model.IceServersResponse;
import com.romanosadchyi.smp.music_app.rtc.internal.config.TurnProperties;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * The ICE servers a browser uses for a room's audio connections (KAN-46): STUN, and TURN with credentials that work
 * for a few hours only, so a leaked one is soon useless and the shared secret never leaves the server. The
 * credentials follow the coturn {@code use-auth-secret} scheme, which coturn checks without asking this server.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IceServerService {

    static final String HMAC_ALGORITHM = "HmacSHA1";

    TurnProperties turnProperties;
    Clock clock;

    /**
     * The STUN servers, if any are configured, then the TURN servers with a credential for {@code userId} that
     * expires after the configured lifetime.
     */
    public IceServersResponse iceServers(UUID userId) {
        Instant expiresAt = clock.instant().plusSeconds(turnProperties.credentialTtlSeconds());
        String username = expiresAt.getEpochSecond() + ":" + userId;
        List<IceServer> servers = new ArrayList<>();
        if (!turnProperties.stunUrls().isEmpty()) {
            servers.add(new IceServer(turnProperties.stunUrls()));
        }
        servers.add(new IceServer(turnProperties.urls()).username(username).credential(credential(username)));
        return new IceServersResponse(servers, Instant.ofEpochSecond(expiresAt.getEpochSecond()));
    }

    /**
     * {@code base64(HMAC-SHA1(secret, username))}, as coturn computes it.
     */
    private String credential(String username) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(turnProperties.secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            // HmacSHA1 is in every JRE and takes a key of any length, so this is a broken runtime
            throw new IllegalStateException("Could not sign the TURN credential", e);
        }
    }
}
