package com.taasim.auth.service;

import com.taasim.auth.dto.RegisterRequest;
import com.taasim.auth.model.Role;
import com.taasim.auth.model.User;
import com.taasim.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private AuthService authService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository);
    }

    @Test
    void register_validRequest_hashesPasswordAndSavesUser() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("soufiane@test.com");
        request.setPassword("secret123");
        request.setFullName("Soufiane B");
        request.setPhone("+212600000000");
        request.setRole("DRIVER");

        when(userRepository.existsByEmail("soufiane@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        User result = authService.register(request);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("soufiane@test.com");
        assertThat(result.getFullName()).isEqualTo("Soufiane B");
        assertThat(result.getRole()).isEqualTo(Role.DRIVER);
        assertThat(result.isActive()).isTrue();

        // Password must be hashed with BCrypt, not plain text
        assertThat(result.getPassword()).isNotEqualTo("secret123");
        assertThat(passwordEncoder.matches("secret123", result.getPassword())).isTrue();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("soufiane@test.com");
    }

    @Test
    void register_duplicateEmail_throwsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@test.com");
        request.setPassword("pass");
        request.setRole("CLIENT");

        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email already registered: existing@test.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_lowercaseRole_parsesCorrectly() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("client@test.com");
        request.setPassword("pass123");
        request.setFullName("Client Test");
        request.setRole("client"); // lowercase

        when(userRepository.existsByEmail("client@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = authService.register(request);

        assertThat(result.getRole()).isEqualTo(Role.CLIENT);
    }
}
