package com.devtrack.service;

import com.devtrack.dto.AuthDtos.*;
import com.devtrack.entity.User;
import com.devtrack.exception.BadRequestException;
import com.devtrack.repository.UserRepository;
import com.devtrack.config.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private AuthenticationManager authenticationManager;

    public UserResponse registerUser(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BadRequestException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Email is already registered");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();

        User savedUser = userRepository.save(user);
        return convertToResponse(savedUser);
    }

    public LoginResponse loginUser(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadRequestException("User record not found after authentication"));

        return new LoginResponse(jwt, convertToResponse(user));
    }

    @Transactional(readOnly = true)
    public UserResponse convertToResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }
}
