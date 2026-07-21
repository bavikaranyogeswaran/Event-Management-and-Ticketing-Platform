package com.ticketing.checkin;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.checkin.dto.CheckInRequest;
import com.ticketing.checkin.dto.CheckInValidationResponse;
import com.ticketing.shared.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/check-ins")
class CheckInController {

    private final CheckInService checkInService;

    CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    @PostMapping("/validate")
    CheckInValidationResponse validate(CurrentUser currentUser, @Valid @RequestBody CheckInRequest request) {
        return CheckInValidationResponse.from(checkInService.validate(request.toCommand(), currentUser));
    }
}
