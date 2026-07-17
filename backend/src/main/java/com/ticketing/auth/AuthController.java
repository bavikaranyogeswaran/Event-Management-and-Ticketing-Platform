package com.ticketing.auth;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.ticketing.auth.dto.ForgotPasswordRequest;
import com.ticketing.auth.dto.LoginRequest;
import com.ticketing.auth.dto.RegisterRequest;
import com.ticketing.auth.dto.RegisterResponse;
import com.ticketing.auth.dto.ResetPasswordRequest;
import com.ticketing.auth.dto.SessionResponse;
import com.ticketing.auth.dto.VerifyEmailRequest;
import com.ticketing.shared.api.ApiException;
import com.ticketing.user.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
class AuthController {

    private final RegistrationService registrationService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    AuthController(RegistrationService registrationService,
            EmailVerificationService emailVerificationService,
            PasswordResetService passwordResetService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository) {
        this.registrationService = registrationService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = registrationService.register(request.email(), request.password(), request.displayName());
        return new RegisterResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.isEmailVerified());
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.OK)
    void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(request.token(), request.newPassword());
    }

    @PostMapping("/login")
    SessionResponse login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.email().trim().toLowerCase(Locale.ROOT), request.password()));
        } catch (AuthenticationException e) {
            // same message for wrong password, unknown account, or suspended account
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password.");
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        // rotate the session id after the context is stored, to block session fixation
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }

        return SessionResponse.from((AppUserDetails) authentication.getPrincipal());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, authentication);
    }

    @GetMapping("/session")
    SessionResponse session(@AuthenticationPrincipal AppUserDetails user) {
        return SessionResponse.from(user);
    }
}
