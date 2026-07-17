package com.ticketing.auth;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/** Ends all active sessions for a user, e.g. after a password reset. */
@Service
public class UserSessionService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    UserSessionService(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public void invalidateAll(String principalName) {
        sessionRepository.findByPrincipalName(principalName).keySet()
                .forEach(sessionRepository::deleteById);
    }
}
