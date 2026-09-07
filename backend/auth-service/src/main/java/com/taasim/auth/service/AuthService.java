package com.taasim.auth.service;

import com.taasim.auth.dto.AuthResponse;
import com.taasim.auth.dto.LoginRequest;
import com.taasim.auth.dto.RegisterRequest;
import com.taasim.auth.model.Role;
import com.taasim.auth.model.User;
import com.taasim.auth.repository.UserRepository;
import com.taasim.auth.security.JwtTokenProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public User register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered: " + request.getEmail());
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));  // Hash!
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setRole(Role.valueOf(request.getRole().toUpperCase()));

        User saved = userRepository.save(user);
        System.out.println("👤 Registered: " + saved.getEmail() + " [" + saved.getRole() + "]");
        return saved;
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        String token = jwtTokenProvider.generateToken(
                user.getId(), user.getEmail(), user.getRole().name()
        );

        System.out.println("🔑 Login: " + user.getEmail() + " [" + user.getRole() + "]");

        return new AuthResponse(token, user.getRole().name(), user.getEmail(), user.getFullName());
    }
}
