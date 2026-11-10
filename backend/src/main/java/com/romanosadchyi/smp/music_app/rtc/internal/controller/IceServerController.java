package com.romanosadchyi.smp.music_app.rtc.internal.controller;

import com.romanosadchyi.smp.music_app.rtc.internal.api.RtcApi;
import com.romanosadchyi.smp.music_app.rtc.internal.api.model.IceServersResponse;
import com.romanosadchyi.smp.music_app.rtc.internal.service.IceServerService;
import com.romanosadchyi.smp.music_app.shared.security.CurrentUser;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the interface generated from {@code openapi/rtc.yaml}, which holds the mappings; the current user comes
 * from {@link CurrentUser}. The response holds a credential, so it is never cached and never logged.
 */
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IceServerController implements RtcApi {

    IceServerService iceServerService;

    @Override
    public ResponseEntity<IceServersResponse> getIceServers() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(iceServerService.iceServers(CurrentUser.get().id()));
    }
}
