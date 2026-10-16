package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.response.IntegrationRequestResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.IntegrationRequest;
import com.romanosadchyi.smp.music_app.music.internal.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IntegrationMappingService {

    UserLookup userLookup;
    TrackMappingService trackMappingService;

    public IntegrationRequestResponseDto map(IntegrationRequest request) {
        return new IntegrationRequestResponseDto(
                request.getId(),
                request.getTargetPlaylist().getId(),
                userLookup.getProfile(request.getRequesterId()),
                trackMappingService.mapAll(request.getProposedTracks()),
                request.getStatus().name(),
                request.getCreatedAt()
        );
    }
}
