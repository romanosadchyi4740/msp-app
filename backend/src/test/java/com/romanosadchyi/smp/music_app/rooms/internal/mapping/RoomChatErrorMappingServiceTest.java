package com.romanosadchyi.smp.music_app.rooms.internal.mapping;

import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomClosedException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.shared.exception.ErrorResponseDto;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentTypeMismatchException;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class RoomChatErrorMappingServiceTest {

    RoomChatErrorMappingService mappingService = new RoomChatErrorMappingService();

    @Test
    void map_UsesTheRestCodes() {
        assertThat(mappingService.map(new RoomNotFoundException("Room not found")))
                .isEqualTo(new ErrorResponseDto("404 NOT_FOUND", "Room not found"));
        assertThat(mappingService.map(new IllegalArgumentException("A message needs some text")))
                .isEqualTo(new ErrorResponseDto("400 BAD_REQUEST", "A message needs some text"));
        assertThat(mappingService.map(new RoomClosedException("The room is closed")))
                .isEqualTo(new ErrorResponseDto("409 CONFLICT", "The room is closed"));
        assertThat(mappingService.map(new UserNotAuthorizedException("Not allowed")))
                .isEqualTo(new ErrorResponseDto("403 FORBIDDEN", "Not allowed"));
    }

    @Test
    void map_UnreadablePayload_IsABadRequest() {
        assertThat(mappingService.map(new MessageConversionException("Unrecognized field")))
                .isEqualTo(new ErrorResponseDto("400 BAD_REQUEST", "The message could not be read"));
    }

    @Test
    void map_MalformedDestinationVariable_IsABadRequest() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);

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
