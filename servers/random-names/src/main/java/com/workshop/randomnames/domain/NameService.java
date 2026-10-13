package com.workshop.randomnames.domain;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class NameService {

    public static final List<String> INITIAL_NAMES = List.of(
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

    // Backwards-compatible reference
    public static final List<String> NAMES = INITIAL_NAMES;

    private final List<String> names = new CopyOnWriteArrayList<>(INITIAL_NAMES);

    public List<String> getNames() {
        return new ArrayList<>(names);
    }

    public int getNamesCount() {
        return names.size();
    }

    public boolean addName(String name) {
        if (name == null || name.trim().isBlank()) {
            throw new IllegalArgumentException("Participant name cannot be null or blank");
        }
        String cleanName = name.trim();
        boolean exists = names.stream().anyMatch(n -> n.equalsIgnoreCase(cleanName));
        if (!exists) {
            names.add(cleanName);
            return true;
        }
        return false;
    }

    public boolean removeName(String name) {
        if (name == null || name.trim().isBlank()) {
            return false;
        }
        String cleanName = name.trim();
        return names.removeIf(n -> n.equalsIgnoreCase(cleanName));
    }

    public List<String> searchByStartingLetter(String letterOrPrefix) {
        if (letterOrPrefix == null || letterOrPrefix.trim().isBlank()) {
            return Collections.emptyList();
        }
        String prefix = letterOrPrefix.trim().toLowerCase();
        return names.stream()
                .filter(n -> n.toLowerCase().startsWith(prefix))
                .toList();
    }

    public void resetNames() {
        names.clear();
        names.addAll(INITIAL_NAMES);
    }

    public String getRandomName() {
        return getRandomName(null);
    }

    public String getRandomName(String exclude) {
        if (names.isEmpty()) {
            return "No participants available";
        }
        List<String> candidates = new ArrayList<>(names);
        if (exclude != null && !exclude.isBlank()) {
            candidates.removeIf(n -> n.equalsIgnoreCase(exclude.trim()));
        }
        if (candidates.isEmpty()) {
            candidates = new ArrayList<>(names);
        }
        int index = ThreadLocalRandom.current().nextInt(candidates.size());
        return candidates.get(index);
    }

    public RandomNameResult selectRandomName(String exclude) {
        String selected = getRandomName(exclude);
        List<String> currentPool = getNames();
        String confirmation = String.format(
                "[MCP Server: random-names-server (Port 9000)] Successfully selected random participant: '%s' from workshop pool (%d participants): %s",
                selected, currentPool.size(), currentPool
        );
        return new RandomNameResult(
                selected,
                "random-names-server",
                9000,
                Instant.now(),
                currentPool.size(),
                currentPool,
                confirmation
        );
    }

    public List<String> selectPair() {
        if (names.size() < 2) {
            return new ArrayList<>(names);
        }
        List<String> shuffled = new ArrayList<>(names);
        Collections.shuffle(shuffled);
        return List.of(shuffled.get(0), shuffled.get(1));
    }
}
