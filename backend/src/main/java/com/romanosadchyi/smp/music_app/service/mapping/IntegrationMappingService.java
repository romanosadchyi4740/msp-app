package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.response.IntegrationRequestResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.IntegrationRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IntegrationMappingService {

    UserMappingService userMappingService;
    TrackMappingService trackMappingService;

    public IntegrationRequestResponseDto map(IntegrationRequest request) {
        return new IntegrationRequestResponseDto(
                request.getId(),
                request.getTargetPlaylist().getId(),
                userMappingService.map(request.getRequester()),
                request.getProposedTracks().stream().map(trackMappingService::map).collect(Collectors.toList()),
                request.getStatus().name(),
                request.getCreatedAt()
        );
    }
}
