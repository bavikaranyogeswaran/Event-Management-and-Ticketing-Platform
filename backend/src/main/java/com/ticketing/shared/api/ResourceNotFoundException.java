package com.ticketing.shared.api;

import org.springframework.http.HttpStatus;

/** 404 for missing AND not-owned resources — never reveals which one it was. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException() {
        super(HttpStatus.NOT_FOUND, ErrorCodes.RESOURCE_NOT_FOUND, "The requested resource was not found.");
    }
}
