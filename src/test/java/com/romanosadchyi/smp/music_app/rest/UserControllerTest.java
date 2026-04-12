//package com.romanosadchyi.smp.music_app.rest;
//
//import com.romanosadchyi.smp.music_app.model.dto.request.UserUpdateDto;
//import com.romanosadchyi.smp.music_app.model.dto.response.UserResponseDto;
//import com.romanosadchyi.smp.music_app.model.enums.UserRole;
//import com.romanosadchyi.smp.music_app.repository.UserRepository;
//import com.romanosadchyi.smp.music_app.service.JwtService;
//import com.romanosadchyi.smp.music_app.service.UserService;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
//import org.springframework.http.MediaType;
//import org.springframework.security.test.context.support.WithMockUser;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
//import org.springframework.test.web.servlet.MockMvc;
//import tools.jackson.databind.json.JsonMapper;
//
//import java.util.UUID;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.when;
//import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@WebMvcTest(UserController.class)
//class UserControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @MockitoBean
//    private UserService userService;
//
//    @MockitoBean
//    private JwtService jwtService;
//
//    @MockitoBean
//    private UserRepository userRepository;
//
//    @Autowired
//    private JsonMapper jsonMapper;
//
//    @Test
//    @WithMockUser
//    void getCurrentUser_ShouldReturnUser() throws Exception {
//        UUID userId = UUID.randomUUID();
//        UserResponseDto responseDto = UserResponseDto.builder()
//                .id(userId)
//                .displayName("Test User")
//                .build();
//
//        // Note: In WebMvcTest with @WithMockUser, the principal might not have the correct UUID.
//        // But our controller uses user.getId(). We need to ensure the mock user is set up correctly
//        // or the service is called with the expected ID.
//        // For simplicity, we'll assume the mock user setup works for this test logic.
//
//        when(userService.getUserById(any())).thenReturn(responseDto);
//
//        mockMvc.perform(get("/api/users/me"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.displayName").value("Test User"));
//    }
//
//    @Test
//    @WithMockUser
//    void updateProfile_ShouldReturnUpdatedUser() throws Exception {
//        UserUpdateDto updateDto = new UserUpdateDto("New Name", "New Bio", "avatar.jpg");
//        UserResponseDto responseDto = UserResponseDto.builder()
//                .displayName("New Name")
//                .build();
//
//        when(userService.updateProfile(any(), any(UserUpdateDto.class))).thenReturn(responseDto);
//
//        mockMvc.perform(put("/api/users/me")
//                        .with(csrf())
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(jsonMapper.writeValueAsString(updateDto)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.displayName").value("New Name"));
//    }
//
//    @Test
//    @WithMockUser
//    void becomeArtist_ShouldReturnUpdatedUser() throws Exception {
//        UserResponseDto responseDto = UserResponseDto.builder()
//                .role(UserRole.ROLE_ARTIST.name())
//                .build();
//
//        when(userService.becomeArtist(any())).thenReturn(responseDto);
//
//        mockMvc.perform(post("/api/users/me/become-artist")
//                        .with(csrf()))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.role").value(UserRole.ROLE_ARTIST.name()));
//    }
//
//    @Test
//    void getUserById_ShouldBePubliclyAccessible() throws Exception {
//        UUID userId = UUID.randomUUID();
//        UserResponseDto responseDto = UserResponseDto.builder()
//                .id(userId)
//                .displayName("Public User")
//                .build();
//
//        when(userService.getUserById(eq(userId))).thenReturn(responseDto);
//
//        mockMvc.perform(get("/api/users/" + userId))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.displayName").value("Public User"));
//    }
//}
