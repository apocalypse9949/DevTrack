package com.devtrack.controller;

import com.devtrack.config.JwtAuthenticationFilter;
import com.devtrack.config.JwtTokenProvider;
import com.devtrack.dto.AuthDtos.*;
import com.devtrack.entity.Role;
import com.devtrack.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testRegister_Success() throws Exception {
        RegisterRequest request = new RegisterRequest("developer_user", "dev@devtrack.com", "password123", Role.ROLE_DEVELOPER);
        UserResponse response = new UserResponse(UUID.randomUUID(), "developer_user", "dev@devtrack.com", Role.ROLE_DEVELOPER);

        when(userService.registerUser(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("developer_user"))
                .andExpect(jsonPath("$.role").value("ROLE_DEVELOPER"));
    }

    @Test
    void testLogin_Success() throws Exception {
        LoginRequest request = new LoginRequest("developer_user", "password123");
        UserResponse userResponse = new UserResponse(UUID.randomUUID(), "developer_user", "dev@devtrack.com", Role.ROLE_DEVELOPER);
        LoginResponse response = new LoginResponse("mocked-jwt-token", userResponse);

        when(userService.loginUser(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mocked-jwt-token"))
                .andExpect(jsonPath("$.user.username").value("developer_user"));
    }
}
