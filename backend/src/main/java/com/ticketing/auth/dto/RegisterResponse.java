package com.ticketing.auth.dto;

import java.util.UUID;

public record RegisterResponse(UUID id, String email, String displayName, boolean emailVerified) {
}
