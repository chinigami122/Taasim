package com.taasim.auth;

import com.taasim.auth.dto.AuthResponse;
import com.taasim.auth.dto.LoginRequest;
import com.taasim.auth.dto.RegisterRequest;
import com.taasim.auth.model.Role;
import com.taasim.auth.model.User;
import com.taasim.auth.repository.UserRepository;
import com.taasim.auth.security.JwtTokenProvider;
import com.taasim.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthRegistrationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("taasim_test")
            .withUsername("taasim")
            .withPassword("taasim");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void register_withRealPostgresAndFlyway_persistsUserCorrectly() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("it_driver@taasim.com");
        request.setPassword("securePassword999");
        request.setFullName("Integration Driver");
        request.setPhone("+212699999999");
        request.setRole("DRIVER");

        User registered = authService.register(request);

        assertThat(registered.getId()).isNotNull();
        assertThat(registered.getEmail()).isEqualTo("it_driver@taasim.com");

        // Verify direct DB lookup via JPA Repository
        Optional<User> foundInDb = userRepository.findByEmail("it_driver@taasim.com");
        assertThat(foundInDb).isPresent();
        User dbUser = foundInDb.get();
        assertThat(dbUser.getFullName()).isEqualTo("Integration Driver");
        assertThat(dbUser.getRole()).isEqualTo(Role.DRIVER);
        assertThat(dbUser.isActive()).isTrue();
        assertThat(passwordEncoder.matches("securePassword999", dbUser.getPassword())).isTrue();
    }

    @Test
    void register_duplicateEmail_failsWithException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("duplicate@taasim.com");
        request.setPassword("pass1");
        request.setFullName("User One");
        request.setRole("CLIENT");

        authService.register(request);

        // Attempt second registration with same email
        RegisterRequest duplicate = new RegisterRequest();
        duplicate.setEmail("duplicate@taasim.com");
        duplicate.setPassword("pass2");
        duplicate.setFullName("User Two");
        duplicate.setRole("CLIENT");

        assertThatThrownBy(() -> authService.register(duplicate))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email already registered: duplicate@taasim.com");
    }

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void registerAndLogin_endToEnd_returnsValidJwtToken() {
        // 1. Register
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setEmail("jwt_test@taasim.com");
        registerReq.setPassword("secretPassword");
        registerReq.setFullName("JWT Test User");
        registerReq.setRole("DRIVER");

        User registered = authService.register(registerReq);
        assertThat(registered).isNotNull();

        // 2. Login with valid credentials
        LoginRequest loginReq = new LoginRequest("jwt_test@taasim.com", "secretPassword");
        AuthResponse authResponse = authService.login(loginReq);

        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getEmail()).isEqualTo("jwt_test@taasim.com");
        assertThat(authResponse.getRole()).isEqualTo("DRIVER");
        assertThat(authResponse.getAccessToken()).isNotBlank();

        // 3. Verify JWT claims
        String token = authResponse.getAccessToken();
        String userIdFromToken = jwtTokenProvider.validateTokenAndGetUserId(token);
        assertThat(userIdFromToken).isEqualTo(registered.getId().toString());
        String roleFromToken = jwtTokenProvider.getRoleFromToken(token);
        assertThat(roleFromToken).isEqualTo("DRIVER");

        // 4. Verify login fails with wrong password
        LoginRequest wrongLogin = new LoginRequest("jwt_test@taasim.com", "wrongPassword");
        assertThatThrownBy(() -> authService.login(wrongLogin))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid password");
    }
}
