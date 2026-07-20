package com.ticketing.payment;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.payment.dto.CheckoutResponse;
import com.ticketing.shared.security.CurrentUser;

@RestController
class CheckoutController {

    private final CheckoutService checkoutService;

    CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/orders/{orderId}/checkout")
    CheckoutResponse startCheckout(CurrentUser currentUser, @PathVariable UUID orderId) {
        currentUser.requireVerifiedEmail();
        return CheckoutResponse.from(
                checkoutService.startCheckout(orderId, currentUser.userId(), currentUser.email()));
    }
}
