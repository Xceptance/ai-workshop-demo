package com.workshop.randomnames.domain;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ============================================================================
 * Domain Service: Workshop Participant Names Management
 * ============================================================================
 *
 * Core Responsibilities:
 * -----------------------
 * 1. Maintains the canonical list of registered attendees for the AI Agent workshop.
 * 2. Provides thread-safe mutations (adding and removing participants).
 * 3. Provides case-insensitive filtering by starting letter / prefix.
 * 4. Supplies cryptographically pseudo-random participant selection for demo exercises.
 * 5. Facilitates pair-programming team assignments (Driver / Navigator).
 *
 * Thread-Safety Model:
 * --------------------
 * MCP tool requests and HTTP REST calls execute concurrently across different Tomcat
 * worker threads. To eliminate race conditions without heavyweight synchronization blocks,
 * this service employs a {@link CopyOnWriteArrayList}. Read operations (which dominate
 * workshop traffic) are wait-free, and list modifications produce atomic array snapshots.
 *
 * Baseline Participant Pool (10 Names):
 * -------------------------------------
 *   1. Alex       6. Katrin
 *   2. Rene       7. Johannes
 *   3. Ray        8. Bernd
 *   4. Jane       9. Dude
 *   5. Katja     10. Mario
 */
@Service
public class NameService
{
    /**
     * Canonical immutable baseline list of the 10 initial workshop participants.
     */
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

    /**
     * Backwards-compatible public alias for tests and external callers.
     */
    public static final List<String> NAMES = INITIAL_NAMES;

    /**
     * Dynamic, thread-safe collection holding the active participant pool.
     */
    private final List<String> names = new CopyOnWriteArrayList<>(INITIAL_NAMES);

    /**
     * Returns a snapshot copy of all active participant names.
     *
     * @return Fresh list containing all currently registered participants.
     */
    public List<String> getNames()
    {
        return new ArrayList<>(names);
    }

    /**
     * Returns the total count of registered participants in the active pool.
     *
     * @return Active pool size.
     */
    public int getNamesCount()
    {
        return names.size();
    }

    /**
     * Adds a new participant to the workshop pool.
     *
     * <p>Validation rules:
     * - Name cannot be null, empty, or whitespace-only.
     * - Case-insensitive uniqueness: if "Zoe" is in the pool, adding "zoe" is rejected.
     *
     * @param name The participant's display name.
     * @return {@code true} if successfully added, {@code false} if the participant already exists.
     * @throws IllegalArgumentException if the provided name is null or blank.
     */
    public boolean addName(String name)
    {
        if (name == null || name.trim().isBlank())
        {
            throw new IllegalArgumentException("Participant name cannot be null or blank");
        }
        String cleanName = name.trim();
        boolean exists = names.stream().anyMatch(n -> n.equalsIgnoreCase(cleanName));
        if (!exists)
        {
            names.add(cleanName);
            return true;
        }
        return false;
    }

    /**
     * Removes an existing participant from the pool using a case-insensitive match.
     *
     * @param name The name to remove (e.g. "Mario" or "mario").
     * @return {@code true} if the name was found and removed, {@code false} otherwise.
     */
    public boolean removeName(String name)
    {
        if (name == null || name.trim().isBlank())
        {
            return false;
        }
        String cleanName = name.trim();
        return names.removeIf(n -> n.equalsIgnoreCase(cleanName));
    }

    /**
     * Searches and filters the participant list by starting letter or prefix.
     *
     * <p>Matching is completely case-insensitive:
     * - "k" or "K" matches ["Katja", "Katrin"]
     * - "b" or "B" matches ["Bernd"]
     * - null, empty, or whitespace-only prefix returns an empty list.
     *
     * @param letterOrPrefix The starting letter or multi-character prefix to search for.
     * @return List of matching participant names, or an empty list if none match.
     */
    public List<String> searchByStartingLetter(String letterOrPrefix)
    {
        if (letterOrPrefix == null || letterOrPrefix.trim().isBlank())
        {
            return Collections.emptyList();
        }
        String prefix = letterOrPrefix.trim().toLowerCase();
        return names.stream()
                .filter(n -> n.toLowerCase().startsWith(prefix))
                .toList();
    }

    /**
     * Resets the active participant pool back to the 10 original workshop attendees.
     * Used by unit tests and workshop demos to guarantee deterministic starting states.
     */
    public void resetNames()
    {
        names.clear();
        names.addAll(INITIAL_NAMES);
    }

    /**
     * Selects a single random participant with no exclusion criteria.
     *
     * @return A randomly selected name, or a fallback warning if the pool is empty.
     */
    public String getRandomName()
    {
        return getRandomName(null);
    }

    /**
     * Selects a single random participant from the pool, optionally excluding
     * a specific person (e.g. the current speaker or instructor).
     *
     * @param exclude Optional participant name to exclude from selection (case-insensitive).
     * @return The randomly selected participant name.
     */
    public String getRandomName(String exclude)
    {
        if (names.isEmpty())
        {
            return "No participants available";
        }
        List<String> candidates = new ArrayList<>(names);
        if (exclude != null && !exclude.isBlank())
        {
            candidates.removeIf(n -> n.equalsIgnoreCase(exclude.trim()));
        }
        // If exclusion removed all candidates, fall back to the complete pool
        if (candidates.isEmpty())
        {
            candidates = new ArrayList<>(names);
        }
        int index = ThreadLocalRandom.current().nextInt(candidates.size());
        return candidates.get(index);
    }

    /**
     * Selects a random participant and packages the outcome into an immutable
     * {@link RandomNameResult} snapshot complete with audit metadata and human-readable confirmation.
     *
     * @param exclude Optional name to exclude from the draw.
     * @return Audit result detailing the selection, pool size, port, and timestamp.
     */
    public RandomNameResult selectRandomName(String exclude)
    {
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

    /**
     * Selects two distinct participants to form a pair-programming team
     * (the first assigned as Driver and the second as Navigator).
     *
     * @return Two-element list containing distinct participant names, or whatever
     *         names are available if pool size is less than 2.
     */
    public List<String> selectPair()
    {
        if (names.size() < 2)
        {
            return new ArrayList<>(names);
        }
        List<String> shuffled = new ArrayList<>(names);
        Collections.shuffle(shuffled);
        return List.of(shuffled.get(0), shuffled.get(1));
    }
}
