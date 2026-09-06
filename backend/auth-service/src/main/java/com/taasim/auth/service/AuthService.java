package com.taasim.auth.service;

import com.taasim.auth.dto.RegisterRequest;
import com.taasim.auth.model.Role;
import com.taasim.auth.model.User;
import com.taasim.auth.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
}
