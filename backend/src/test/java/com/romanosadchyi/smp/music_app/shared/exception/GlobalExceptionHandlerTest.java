package com.romanosadchyi.smp.music_app.shared.exception;

import lombok.experimental.StandardException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void responseStatusException_AnswersWithItsStatusAndReason(CapturedOutput output) {
        ResponseEntity<ErrorResponseDto> response = handler.handleGeneralException(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(new ErrorResponseDto("401 UNAUTHORIZED", "Invalid credentials"));
        assertThat(output).contains("WARN").contains("Request refused with 401 (ResponseStatusException)")
                .doesNotContain("ERROR");
    }

    @Test
    void responseStatusException_ClientError_IsLoggedWithoutItsCause(CapturedOutput output) {
        ResponseStatusException e = new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Failed",
                new IllegalArgumentException("token-fragment-abc"));

        handler.handleGeneralException(e);

        assertThat(output).doesNotContain("token-fragment-abc").doesNotContain("\tat ");
    }

    @Test
    void springMvcClientError_AnswersWithItsStatus() {
        ResponseEntity<ErrorResponseDto> response = handler.handleGeneralException(
                new NoResourceFoundException(HttpMethod.GET, "/api/nothing", "api/nothing"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void responseStatusException_ServerError_IsAGeneric500() {
        ResponseEntity<ErrorResponseDto> response = handler.handleGeneralException(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Storage down"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo(GlobalExceptionHandler.UNEXPECTED_ERROR);
    }

    @Test
    void dataAccessException_IsA500LoggedWithoutItsMessage(CapturedOutput output) {
        DataIntegrityViolationException e = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"email_unique\"",
                new SQLException("Key (email)=(someone@example.com) already exists."));

        ResponseEntity<ErrorResponseDto> response = handler.handleGeneralException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(output).contains("DataIntegrityViolationException").contains("SQLException")
                .doesNotContain("someone@example.com").doesNotContain("email_unique");
    }

    @Test
    void otherException_IsA500WithTheGenericMessage(CapturedOutput output) {
        ResponseEntity<ErrorResponseDto> response = handler.handleGeneralException(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo(GlobalExceptionHandler.UNEXPECTED_ERROR);
        assertThat(output).contains("ERROR").contains("IllegalStateException: boom");
    }

    @Test
    void conflictException_Is409WithItsMessage() {
        ResponseEntity<ErrorResponseDto> response = handler.handleConflictException(new TestConflict("Taken"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Taken");
    }

    @StandardException
    static class TestConflict extends ConflictException {
    }
}
