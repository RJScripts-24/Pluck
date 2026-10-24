package dev.pluck.backend.controller;

import dev.pluck.backend.model.User;
import dev.pluck.backend.repository.UserRepository;
import dev.pluck.backend.service.CliService;
import dev.pluck.backend.service.SnippetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CliControllerTest {

    private CliService cliService;
    private UserRepository userRepository;
    private CliController cliController;

    @BeforeEach
    void setUp() {
        cliService = mock(CliService.class);
        userRepository = mock(UserRepository.class);
        cliController = new CliController(cliService, userRepository);

        when(userRepository.findAll()).thenReturn(List.of(new User("usr_alex", "Alex Developer", "alex@pluck.dev", "hash", "owner")));
    }

    @Test
    @DisplayName("Stage 9: CliController sync returns 200 OK")
    void testSync() {
        when(cliService.sync(anyString(), any())).thenReturn(Map.of("workspace", "ws_personal", "count", 0));
        ResponseEntity<Map<String, Object>> resp = cliController.sync(null, "ws_personal", null);
        assertEquals(200, resp.getStatusCode().value());
        assertEquals("ws_personal", resp.getBody().get("workspace"));
    }

    @Test
    @DisplayName("Stage 9: CliController push catches SecurityException and returns 422 Unprocessable Entity")
    void testPushSecretDetected() {
        when(cliService.push(anyString(), any(), any()))
                .thenThrow(new SecurityException("SECRET_DETECTED: Stripe key"));

        ResponseEntity<?> resp = cliController.push(Map.of("code", String.join("_", "sk", "live", "123")), "ws_personal");
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resp.getStatusCode());
    }

    @Test
    @DisplayName("Stage 9: CliController push catches VersionConflictException and returns 409 Conflict")
    void testPushConflict() {
        when(cliService.push(anyString(), any(), any()))
                .thenThrow(new SnippetService.VersionConflictException(3));

        ResponseEntity<?> resp = cliController.push(Map.of("version", 1), "ws_personal");
        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
    }

    @Test
    @DisplayName("Stage 9: CliController resolve returns 404 when not found")
    void testResolveNotFound() {
        when(cliService.resolve(anyString(), eq("missing"), anyString()))
                .thenThrow(new NoSuchElementException("Not found"));

        ResponseEntity<?> resp = cliController.resolve("missing", "npm", "ws_personal");
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }
}
