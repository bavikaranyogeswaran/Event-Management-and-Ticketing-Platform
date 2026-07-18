package com.ticketing.order;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.order.dto.OrderRequest;
import com.ticketing.order.dto.OrderResponse;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ErrorCodes;
import com.ticketing.shared.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/orders")
class OrderController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final int MAX_KEY_LENGTH = 80; // matches the orders.idempotency_key column

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    ResponseEntity<OrderResponse> place(CurrentUser currentUser,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody OrderRequest request) {
        currentUser.requireVerifiedEmail();
        OrderResult result = orderService.place(
                currentUser.userId(), requireUsableKey(idempotencyKey), request.toCommand());
        // a retry answers with the order that already exists instead of reporting a new one
        HttpStatus status = result.replay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(OrderResponse.from(result));
    }

    @GetMapping("/{orderId}")
    OrderResponse get(CurrentUser currentUser, @PathVariable UUID orderId) {
        return OrderResponse.from(orderService.getOwnedOrder(orderId, currentUser.userId()));
    }

    private String requireUsableKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, OrderErrorCodes.IDEMPOTENCY_KEY_REQUIRED,
                    "Send an Idempotency-Key header so a repeated request cannot create a second order.");
        }
        if (idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED,
                    "Idempotency-Key must be at most %d characters.".formatted(MAX_KEY_LENGTH));
        }
        return idempotencyKey;
    }
}
