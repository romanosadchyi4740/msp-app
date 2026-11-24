package com.romanosadchyi.smp.music_app.users.internal.controller;

import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import com.romanosadchyi.smp.music_app.shared.security.JwtService;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
import com.romanosadchyi.smp.music_app.users.external.UserRole;
import com.romanosadchyi.smp.music_app.users.internal.dto.CurrentUserResponseDto;
import com.romanosadchyi.smp.music_app.users.internal.dto.UserUpdateDto;
import com.romanosadchyi.smp.music_app.users.internal.service.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserController {

    UserService userService;
    JwtService jwtService;

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponseDto> getCurrentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Getting current user: {}", user.id());
        return ResponseEntity.ok(userService.getCurrentUser(user.id()));
    }

    @PutMapping("/me")
    public ResponseEntity<CurrentUserResponseDto> updateProfile(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody UserUpdateDto dto
    ) {
        log.info("Updating profile for user: {}", user.id());
        return ResponseEntity.ok(userService.updateProfile(user.id(), dto));
    }

    @PostMapping("/me/become-artist")
    public ResponseEntity<CurrentUserResponseDto> becomeArtist(@AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Becoming an artist for user: {}", user.id());
        CurrentUserResponseDto artist = userService.becomeArtist(user.id());
        // The role is a token claim, so the caller needs a new token to use artist endpoints before the next login
        String token = jwtService.generateToken(
                new AuthenticatedUser(user.id(), user.email(), UserRole.ROLE_ARTIST.name()));
        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(artist);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable UUID id) {
        log.info("Getting user with ID: {}", id);
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<UserResponseDto>> searchUsers(
            @RequestParam("q") String query,
            @RequestParam(value = "pageNo", defaultValue = "0") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize
    ) {
        log.info("Searching users with query: {}", query);
        return ResponseEntity.ok(userService.searchUsers(query, pageNo, pageSize));
    }

    @PostMapping("/{id}/follow")
    public ResponseEntity<Void> followUser(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        log.info("User {} following user {}", user.id(), id);
        userService.followUser(user.id(), id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/follow")
    public ResponseEntity<Void> unfollowUser(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        log.info("User {} unfollowing user {}", user.id(), id);
        userService.unfollowUser(user.id(), id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/following")
    public ResponseEntity<List<UserResponseDto>> getFollowing(@AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Getting following for user {}", user.id());
        return ResponseEntity.ok(userService.getFollowing(user.id()));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> terminateIdentity(@AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Terminating identity for user: {}", user.id());
        userService.terminateIdentity(user.id());
        return ResponseEntity.noContent().build();
    }
}
