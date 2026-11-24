package com.romanosadchyi.smp.music_app.rtc.internal.mapping;

import com.romanosadchyi.smp.music_app.rtc.internal.exception.SignalPeerNotFoundException;
import com.romanosadchyi.smp.music_app.shared.exception.ErrorResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentTypeMismatchException;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class SignalErrorMappingServiceTest {

    SignalErrorMappingService mappingService = new SignalErrorMappingService();

    @Test
    void map_UsesTheRestCodes() {
        assertThat(mappingService.map(new SignalPeerNotFoundException("The recipient is not in this room")))
                .isEqualTo(new ErrorResponseDto("404 NOT_FOUND", "The recipient is not in this room"));
        assertThat(mappingService.map(new IllegalArgumentException("A recipient is required")))
                .isEqualTo(new ErrorResponseDto("400 BAD_REQUEST", "A recipient is required"));
    }

    @Test
    void map_UnreadablePayload_IsABadRequest() {
        assertThat(mappingService.map(new MessageConversionException("Unrecognized field")))
                .isEqualTo(new ErrorResponseDto("400 BAD_REQUEST", "The signal could not be read"));
    }

    @Test
    void map_MalformedDestinationVariable_IsABadRequest() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);

        assertThat(mappingService.map(new MethodArgumentTypeMismatchException(
                MessageBuilder.withPayload("x").build(), parameter, "not a UUID")).code())
                .isEqualTo("400 BAD_REQUEST");
    }

    @Test
    void map_AnythingElse_HidesTheDetails() {
        ErrorResponseDto response = mappingService.map(new IllegalStateException("could not send v=0 o=- 46117"));

        assertThat(response.code()).isEqualTo("500 INTERNAL_SERVER_ERROR");
        assertThat(response.message()).doesNotContain("46117");
    }
}
