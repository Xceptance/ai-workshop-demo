package com.workshop.randomnames.domain;

import java.time.Instant;
import java.util.List;

/**
 * Immutable audit snapshot representing the outcome of selecting a random participant.
 *
 * <p>Why This Snapshot Matters in AI Workflows:
 * When an LLM executes a tool, the returned data structure is converted to JSON
 * and passed back into the conversation context as an observation. By returning
 * explicit provenance fields (source server name, network port, timestamp, and
 * full pool array), the LLM can cite undeniable proof to the user that the name
 * was pulled from the remote Java service rather than generated locally.
 *
 * @param selectedName        The participant selected by the cryptographically pseudo-random generator.
 * @param source              Identifier of the providing MCP server ("random-names-server").
 * @param port                Network port on which this server is operating (default 9000).
 * @param timestamp           Precise ISO-8601 UTC instant when the selection occurred.
 * @param poolSize            Number of eligible participants in the pool at selection time.
 * @param pool                Snapshot copy of the active participant list.
 * @param confirmationMessage Human-readable diagnostic confirmation formatted for AI reasoning logs.
 */
public record RandomNameResult(
        String selectedName,
        String source,
        int port,
        Instant timestamp,
        int poolSize,
        List<String> pool,
        String confirmationMessage
)
{
}
