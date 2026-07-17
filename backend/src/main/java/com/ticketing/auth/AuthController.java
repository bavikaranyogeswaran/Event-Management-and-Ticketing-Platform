package com.ticketing.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.auth.dto.RegisterRequest;
import com.ticketing.auth.dto.RegisterResponse;
import com.ticketing.auth.dto.VerifyEmailRequest;
import com.ticketing.user.User;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
class AuthController {

    private final RegistrationService registrationService;
    private final EmailVerificationService emailVerificationService;

    AuthController(RegistrationService registrationService,
            EmailVerificationService emailVerificationService) {
        this.registrationService = registrationService;
        this.emailVerificationService = emailVerificationService;
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
}
