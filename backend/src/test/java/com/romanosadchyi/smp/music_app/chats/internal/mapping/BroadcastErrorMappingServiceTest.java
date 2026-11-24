package com.romanosadchyi.smp.music_app.chats.internal.mapping;

import com.romanosadchyi.smp.music_app.chats.internal.exception.BroadcastEndedException;
import com.romanosadchyi.smp.music_app.chats.internal.exception.BroadcastNotFoundException;
import com.romanosadchyi.smp.music_app.shared.exception.ErrorResponseDto;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentTypeMismatchException;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class BroadcastErrorMappingServiceTest {

    BroadcastErrorMappingService mappingService = new BroadcastErrorMappingService();

    @Test
    void map_UsesTheRestCodes() {
        assertThat(mappingService.map(new BroadcastNotFoundException("There is no such broadcast")))
                .isEqualTo(new ErrorResponseDto("404 NOT_FOUND", "There is no such broadcast"));
        assertThat(mappingService.map(new UserNotAuthorizedException("Only the host may control this broadcast")))
                .isEqualTo(new ErrorResponseDto("403 FORBIDDEN", "Only the host may control this broadcast"));
        assertThat(mappingService.map(new BroadcastEndedException("This broadcast has ended")))
                .isEqualTo(new ErrorResponseDto("409 CONFLICT", "This broadcast has ended"));
        assertThat(mappingService.map(new IllegalArgumentException("There is no such track")))
                .isEqualTo(new ErrorResponseDto("400 BAD_REQUEST", "There is no such track"));
    }

    @Test
    void map_UnreadablePayloadOrDestination_IsABadRequest() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);

        assertThat(mappingService.map(new MessageConversionException("Unrecognized field")))
                .isEqualTo(new ErrorResponseDto("400 BAD_REQUEST", "The message could not be read"));
        assertThat(mappingService.map(new MethodArgumentTypeMismatchException(
                MessageBuilder.withPayload("x").build(), parameter, "not a UUID")))
                .isEqualTo(new ErrorResponseDto("400 BAD_REQUEST", "The message could not be read"));
    }

    @Test
    void map_AnythingElse_HidesTheDetails() {
        ErrorResponseDto response = mappingService.map(new IllegalStateException("connection pool exhausted"));

        assertThat(response.code()).isEqualTo("500 INTERNAL_SERVER_ERROR");
        assertThat(response.message()).doesNotContain("pool");
    }
}
