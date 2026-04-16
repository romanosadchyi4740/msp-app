package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.request.UserUpdateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.UserResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.service.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserController {

    UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getCurrentUser(@AuthenticationPrincipal User user) {
        log.info("Getting current user: {}", user.getId());
        return ResponseEntity.ok(userService.getUserById(user.getId()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponseDto> updateProfile(
            @AuthenticationPrincipal User user,
            @RequestBody UserUpdateDto dto
    ) {
        log.info("Updating profile for user: {}", user.getId());
        return ResponseEntity.ok(userService.updateProfile(user.getId(), dto));
    }

    @PostMapping("/me/become-artist")
    public ResponseEntity<UserResponseDto> becomeArtist(@AuthenticationPrincipal User user) {
        log.info("Becoming an artist for user: {}", user.getId());
        return ResponseEntity.ok(userService.becomeArtist(user.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable UUID id) {
        log.info("Getting user with ID: {}", id);
        return ResponseEntity.ok(userService.getUserById(id));
    }

}
