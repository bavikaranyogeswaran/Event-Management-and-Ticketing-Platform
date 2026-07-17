package com.ticketing.testsupport;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.shared.security.CurrentUser;

/** Test-only endpoints for exercising the CurrentUser resolver and role gates. */
@RestController
@RequestMapping("/test-security")
class TestSecuredController {

    @GetMapping("/me")
    CurrentUser me(CurrentUser currentUser) {
        return currentUser;
    }

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    String adminOnly() {
        return "ok";
    }

    @GetMapping("/verified-only")
    String verifiedOnly(CurrentUser currentUser) {
        currentUser.requireVerifiedEmail();
        return "ok";
    }
}
