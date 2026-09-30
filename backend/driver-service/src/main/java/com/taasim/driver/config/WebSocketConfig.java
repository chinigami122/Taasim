package com.taasim.driver.config;

import com.taasim.common.security.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.*;

import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtils jwtUtils;

    public WebSocketConfig(@Value("${jwt.secret:dev-only-secret-please-change-me-it-must-be-256-bits-long-abcd1234}") String jwtSecret) {
        this.jwtUtils = new JwtUtils(jwtSecret);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");               // outbound destinations
        config.setApplicationDestinationPrefixes("/app");  // inbound @MessageMapping
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register raw WebSocket endpoint
        registry.addEndpoint("/ws/tracking")
                .setAllowedOriginPatterns("*");

        // Register SockJS fallback endpoint
        registry.addEndpoint("/ws/tracking")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration reg) {
        reg.setSendTimeLimit(15_000)             // 15s to flush send buffer
           .setSendBufferSizeLimit(512 * 1024)   // 512KB per session
           .setMessageSizeLimit(64 * 1024);      // 64KB per message
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration reg) {
        reg.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String auth = accessor.getFirstNativeHeader("Authorization");
                    if (auth == null || !auth.startsWith("Bearer ")) {
                        auth = accessor.getFirstNativeHeader("token");
                        if (auth != null && !auth.startsWith("Bearer ")) {
                            auth = "Bearer " + auth;
                        }
                    }

                    if (auth == null || !auth.startsWith("Bearer ")) {
                        throw new MessageDeliveryException("Missing or invalid Authorization header in STOMP CONNECT frame");
                    }

                    String token = auth.substring(7);
                    if (!jwtUtils.isValid(token)) {
                        throw new MessageDeliveryException("Invalid JWT token in STOMP CONNECT frame");
                    }

                    String userId = jwtUtils.getUserId(token);
                    String role = jwtUtils.getRole(token);
                    List<SimpleGrantedAuthority> authorities = (role != null && !role.isBlank())
                            ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                            : Collections.emptyList();

                    accessor.setUser(new UsernamePasswordAuthenticationToken(userId, null, authorities));
                }
                return message;
            }
        });
    }
}
