package com.relic.backend.controller;

import com.relic.backend.messaging.DeadLetterReplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/events")
@RequiredArgsConstructor
public class AdminEventsController {

    private final DeadLetterReplayService deadLetterReplayService;

    @PostMapping("/replay-dead-letters")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Integer> replayDeadLetters() {
        int replayed = deadLetterReplayService.replayTaskCompletedDeadLetters();
        return Map.of("replayed", replayed);
    }
}
