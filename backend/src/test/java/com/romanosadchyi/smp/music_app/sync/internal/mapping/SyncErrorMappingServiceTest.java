package com.romanosadchyi.smp.music_app.sync.internal.mapping;

import com.romanosadchyi.smp.music_app.shared.exception.ErrorResponseDto;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.sync.internal.exception.PlaybackStateNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentTypeMismatchException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class SyncErrorMappingServiceTest {

    SyncErrorMappingService mappingService = new SyncErrorMappingService();

    @Test
    void map_UsesTheRestCodes() {
        assertThat(mappingService.map(new PlaybackStateNotFoundException("gone")))
                .isEqualTo(new ErrorResponseDto("404 NOT_FOUND", "gone"));
        assertThat(mappingService.map(new UserNotAuthorizedException("Only the host can control playback")))
                .isEqualTo(new ErrorResponseDto("403 FORBIDDEN", "Only the host can control playback"));
        assertThat(mappingService.map(new IllegalArgumentException("SEEK needs a measure number")))
                .isEqualTo(new ErrorResponseDto("400 BAD_REQUEST", "SEEK needs a measure number"));
    }

    @Test
    void map_UnreadablePayload_IsABadRequest() {
        assertThat(mappingService.map(new MessageConversionException("Unrecognized field")).code())
                .isEqualTo("400 BAD_REQUEST");
    }

    @Test
    void map_MalformedDestinationVariable_IsABadRequest() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);

        assertThat(mappingService.map(new MethodArgumentTypeMismatchException(
                MessageBuilder.withPayload("x").build(), parameter, "not a UUID")).code())
                .isEqualTo("400 BAD_REQUEST");
    }

    @Test
    void map_LostRace_IsAConflict() {
        assertThat(mappingService.map(new ObjectOptimisticLockingFailureException("PlaybackState", "id")).code())
                .isEqualTo("409 CONFLICT");
    }

    @Test
    void map_AnythingElse_HidesTheDetails() {
        ErrorResponseDto response = mappingService.map(new IllegalStateException("connection pool exhausted"));

        assertThat(response.code()).isEqualTo("500 INTERNAL_SERVER_ERROR");
        assertThat(response.message()).doesNotContain("pool");
    }
}
