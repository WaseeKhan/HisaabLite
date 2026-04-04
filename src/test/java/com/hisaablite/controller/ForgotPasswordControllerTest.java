package com.hisaablite.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.hisaablite.entity.PasswordResetToken;
import com.hisaablite.entity.PlanType;
import com.hisaablite.entity.Role;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import com.hisaablite.repository.PasswordResetTokenRepository;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.service.EmailService;

@ExtendWith(MockitoExtension.class)
class ForgotPasswordControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private ForgotPasswordController forgotPasswordController;

    @Test
    void forgotPasswordForUnknownUserShowsGenericSuccessMessage() {
        Model model = new ExtendedModelMap();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/forgot-password");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setScheme("http");
        request.setRequestURI("/forgot-password");

        when(userRepository.findByUsername("missing@example.com")).thenReturn(Optional.empty());

        String view = forgotPasswordController.processForgotPassword(" missing@example.com ", model, request);

        assertEquals("forgot-password", view);
        assertEquals("If an account exists for that email, a reset link has been sent.", model.getAttribute("message"));
        verify(emailService, never()).sendResetEmail(any(), any());
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void forgotPasswordForExistingUserCreatesTokenAndSendsEmail() {
        Model model = new ExtendedModelMap();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/forgot-password");
        request.setScheme("http");
        request.setServerName("demo.example.com");
        request.setServerPort(8080);
        request.setRequestURI("/forgot-password");

        User user = testUser("owner@example.com");
        when(userRepository.findByUsername("owner@example.com")).thenReturn(Optional.of(user));

        String view = forgotPasswordController.processForgotPassword("owner@example.com", model, request);

        assertEquals("forgot-password", view);
        assertEquals("If an account exists for that email, a reset link has been sent.", model.getAttribute("message"));
        verify(passwordResetTokenRepository).deleteByUser(user);

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertEquals(user, savedToken.getUser());
        assertTrue(savedToken.getToken() != null && !savedToken.getToken().isBlank());
        assertTrue(savedToken.getExpiryDate().isAfter(LocalDateTime.now().plusMinutes(14)));

        ArgumentCaptor<String> resetLinkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendResetEmail(eq("owner@example.com"), resetLinkCaptor.capture());
        assertTrue(resetLinkCaptor.getValue().contains("/reset-password?token="));
    }

    @Test
    void showResetPasswordWithoutTokenReturnsStyled404() {
        Model model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = forgotPasswordController.showResetPassword(null, model, response);

        assertEquals("error/404", view);
        assertEquals(404, response.getStatus());
        assertEquals("This password reset link is invalid or has expired.", model.getAttribute("message"));
    }

    @Test
    void processResetPasswordRejectsShortPassword() {
        Model model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();
        PasswordResetToken token = validToken("reset-1", testUser("owner@example.com"));

        when(passwordResetTokenRepository.findByToken("reset-1")).thenReturn(Optional.of(token));

        String view = forgotPasswordController.processResetPassword("reset-1", "123", "123", model, response);

        assertEquals("reset-password", view);
        assertEquals("Password must be at least 6 characters.", model.getAttribute("error"));
        assertEquals("reset-1", model.getAttribute("token"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void processResetPasswordRejectsCurrentPasswordReuse() {
        Model model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();
        User user = testUser("owner@example.com");
        PasswordResetToken token = validToken("reset-2", user);

        when(passwordResetTokenRepository.findByToken("reset-2")).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("secret123", "encoded-password")).thenReturn(true);

        String view = forgotPasswordController.processResetPassword(
                "reset-2",
                "secret123",
                "secret123",
                model,
                response);

        assertEquals("reset-password", view);
        assertEquals("New password must be different from the current password.", model.getAttribute("error"));
        verify(userRepository, never()).save(any());
        verify(passwordResetTokenRepository, never()).delete(token);
    }

    @Test
    void processResetPasswordUpdatesPasswordAndDeletesToken() {
        Model model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();
        User user = testUser("owner@example.com");
        PasswordResetToken token = validToken("reset-3", user);

        when(passwordResetTokenRepository.findByToken("reset-3")).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("newsecret", "encoded-password")).thenReturn(false);
        when(passwordEncoder.encode("newsecret")).thenReturn("new-encoded-password");

        String view = forgotPasswordController.processResetPassword(
                "reset-3",
                "newsecret",
                "newsecret",
                model,
                response);

        assertEquals("redirect:/login?resetSuccess", view);
        assertEquals("new-encoded-password", user.getPassword());
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).delete(token);
    }

    private PasswordResetToken validToken(String tokenValue, User user) {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(tokenValue);
        token.setUser(user);
        token.setExpiryDate(LocalDateTime.now().plusMinutes(10));
        return token;
    }

    private User testUser(String username) {
        Shop shop = Shop.builder()
                .id(1L)
                .name("Recovery Shop")
                .panNumber("ABCDE1234F")
                .planType(PlanType.PREMIUM)
                .active(true)
                .build();

        return User.builder()
                .id(1L)
                .name("Owner")
                .username(username)
                .password("encoded-password")
                .phone("9999999999")
                .role(Role.OWNER)
                .shop(shop)
                .active(true)
                .approved(true)
                .currentPlan(PlanType.PREMIUM)
                .build();
    }
}
