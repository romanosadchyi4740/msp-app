package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.request.UserUpdateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto;
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

import java.util.List;
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

    @GetMapping("/{id}/tracks")
    public ResponseEntity<java.util.List<com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto>> getUserTracks(@PathVariable UUID id) {
        log.info("Getting tracks for user with ID: {}", id);
        return ResponseEntity.ok(userService.getUserTracks(id));
    }

    @GetMapping("/search")
    public ResponseEntity<org.springframework.data.domain.Page<UserResponseDto>> searchUsers(
            @RequestParam("q") String query,
            @RequestParam(value = "pageNo", defaultValue = "0") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize
    ) {
        log.info("Searching users with query: {}", query);
        return ResponseEntity.ok(userService.searchUsers(query, pageNo, pageSize));
    }

    @PostMapping("/{id}/follow")
    public ResponseEntity<Void> followUser(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        log.info("User {} following user {}", user.getId(), id);
        userService.followUser(user.getId(), id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/follow")
    public ResponseEntity<Void> unfollowUser(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        log.info("User {} unfollowing user {}", user.getId(), id);
        userService.unfollowUser(user.getId(), id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/following")
    public ResponseEntity<java.util.List<UserResponseDto>> getFollowing(@AuthenticationPrincipal User user) {
        log.info("Getting following for user {}", user.getId());
        return ResponseEntity.ok(userService.getFollowing(user.getId()));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> terminateIdentity(@AuthenticationPrincipal User user) {
        log.info("Terminating identity for user: {}", user.getId());
        userService.terminateIdentity(user.getId());
        return ResponseEntity.noContent().build();
    }

}
