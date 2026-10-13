package com.workshop.randomnames.domain;

import java.time.Instant;
import java.util.List;

public record RandomNameResult(
        String selectedName,
        String source,
        int port,
        Instant timestamp,
        int poolSize,
        List<String> pool,
        String confirmationMessage
) {}
