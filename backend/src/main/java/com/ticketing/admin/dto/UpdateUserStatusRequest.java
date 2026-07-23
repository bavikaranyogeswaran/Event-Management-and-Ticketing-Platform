package com.ticketing.admin.dto;

import com.ticketing.user.UserStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull UserStatus status) {
}
