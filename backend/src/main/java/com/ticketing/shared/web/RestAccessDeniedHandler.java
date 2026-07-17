package com.ticketing.shared.web;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.ticketing.shared.api.ApiErrorResponse;
import com.ticketing.shared.api.ErrorCodes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import tools.jackson.databind.ObjectMapper;

/** Returns the standard error envelope (403) for authenticated-but-forbidden requests, including CSRF failures. */
@Component
class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    RestAccessDeniedHandler(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ApiErrorResponse body = new ApiErrorResponse(Instant.now(clock), HttpStatus.FORBIDDEN.value(),
                ErrorCodes.FORBIDDEN, "You do not have permission for this action.", List.of(),
                MDC.get(RequestIdFilter.MDC_KEY));
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
