package com.ticketing.shared.web;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.ticketing.shared.api.ApiErrorResponse;
import com.ticketing.shared.api.ErrorCodes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import tools.jackson.databind.ObjectMapper;

/** Returns the standard error envelope (401) when an unauthenticated request hits a protected endpoint. */
@Component
class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    RestAuthenticationEntryPoint(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        ApiErrorResponse body = new ApiErrorResponse(Instant.now(clock), HttpStatus.UNAUTHORIZED.value(),
                ErrorCodes.AUTHENTICATION_REQUIRED, "Authentication is required.", List.of(),
                java.util.Map.of(), MDC.get(RequestIdFilter.MDC_KEY));
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
