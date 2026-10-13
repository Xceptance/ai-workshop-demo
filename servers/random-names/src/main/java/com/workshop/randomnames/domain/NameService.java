package com.workshop.randomnames.domain;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class NameService {

    public static final List<String> NAMES = List.of(
            "Alex",
            "Rene",
            "Ray",
            "Jane",
            "Katja",
            "Katrin",
            "Johannes",
            "Bernd",
            "Dude",
            "Mario"
    );

    public List<String> getNames() {
        return NAMES;
    }

    public int getNamesCount() {
        return NAMES.size();
    }

    public String getRandomName() {
        return getRandomName(null);
    }

    public String getRandomName(String exclude) {
        List<String> candidates = new ArrayList<>(NAMES);
        if (exclude != null && !exclude.isBlank()) {
            candidates.removeIf(n -> n.equalsIgnoreCase(exclude.trim()));
        }
        if (candidates.isEmpty()) {
            candidates = new ArrayList<>(NAMES);
        }
        int index = ThreadLocalRandom.current().nextInt(candidates.size());
        return candidates.get(index);
    }

    public RandomNameResult selectRandomName(String exclude) {
        String selected = getRandomName(exclude);
        String confirmation = String.format(
                "[MCP Server: random-names-server (Port 9000)] Successfully selected random participant: '%s' from fixed workshop pool: %s",
                selected, NAMES
        );
        return new RandomNameResult(
                selected,
                "random-names-server",
                9000,
                Instant.now(),
                NAMES.size(),
                NAMES,
                confirmation
        );
    }

    public List<String> selectPair() {
        List<String> shuffled = new ArrayList<>(NAMES);
        Collections.shuffle(shuffled);
        return List.of(shuffled.get(0), shuffled.get(1));
    }
}
