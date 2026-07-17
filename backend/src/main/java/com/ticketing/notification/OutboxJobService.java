package com.ticketing.notification;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.shared.port.IdGenerator;

import tools.jackson.databind.ObjectMapper;

/** Records background jobs. Runs inside the caller's transaction so a job exists only if the business change committed. */
@Service
public class OutboxJobService {

    private final OutboxJobRepository repository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    OutboxJobService(OutboxJobRepository repository, IdGenerator idGenerator, Clock clock,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String jobType, String jobKey, Object payload) {
        String json = objectMapper.writeValueAsString(payload);
        repository.save(new OutboxJob(idGenerator.newId(), jobType, jobKey, json, Instant.now(clock)));
    }
}
