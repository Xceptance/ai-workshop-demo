package com.workshop.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Resolves the Google Gemini API key from multiple candidate sources:
 * 1. System Environment Variable: GEMINI_API_KEY or GOOGLE_API_KEY
 * 2. JVM System Property: -Dgemini.api-key or -Dgoogle.api-key
 * 3. Local File: "api.key" or ".api.key" in the current working directory or user home
 * 4. Spring Application Configuration: gemini.api-key in application.yml
 */
@Component
public class ApiKeyResolver {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyResolver.class);

    public record ResolvedKey(String key, String source, boolean isMock) {
        public String maskedKey() {
            if (isMock) return "[MOCK-MODE-NO-KEY]";
            if (key == null || key.length() < 8) return "********";
            return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
        }
    }

    /**
     * Attempts to resolve the API key across all standard sources.
     *
     * @param configuredKey Optional key configured via Spring Boot properties
     * @return ResolvedKey containing key, discovery source, and whether mock mode is active
     */
    public ResolvedKey resolveApiKey(String configuredKey) {
        // 1. Check Environment Variables
        String envKey = System.getenv("GEMINI_API_KEY");
        if (isValid(envKey)) {
            log.info("Resolved Gemini API key from environment variable GEMINI_API_KEY");
            return new ResolvedKey(envKey.trim(), "Environment Variable (GEMINI_API_KEY)", false);
        }

        String envGoogleKey = System.getenv("GOOGLE_API_KEY");
        if (isValid(envGoogleKey)) {
            log.info("Resolved Gemini API key from environment variable GOOGLE_API_KEY");
            return new ResolvedKey(envGoogleKey.trim(), "Environment Variable (GOOGLE_API_KEY)", false);
        }

        // 2. Check System Properties
        String sysPropKey = System.getProperty("gemini.api-key");
        if (isValid(sysPropKey)) {
            log.info("Resolved Gemini API key from system property 'gemini.api-key'");
            return new ResolvedKey(sysPropKey.trim(), "System Property (gemini.api-key)", false);
        }

        String sysPropGoogleKey = System.getProperty("google.api-key");
        if (isValid(sysPropGoogleKey)) {
            log.info("Resolved Gemini API key from system property 'google.api-key'");
            return new ResolvedKey(sysPropGoogleKey.trim(), "System Property (google.api-key)", false);
        }

        // 3. Check Local File "api.key" in Working Directory
        Optional<String> fileKey = readKeyFromFile(Paths.get("api.key"));
        if (fileKey.isPresent()) {
            log.info("Resolved Gemini API key from local file './api.key'");
            return new ResolvedKey(fileKey.get(), "Local File (./api.key)", false);
        }

        Optional<String> dotFileKey = readKeyFromFile(Paths.get(".api.key"));
        if (dotFileKey.isPresent()) {
            log.info("Resolved Gemini API key from local file './.api.key'");
            return new ResolvedKey(dotFileKey.get(), "Local File (./.api.key)", false);
        }

        // Check user home ~/.gemini/api.key
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            Optional<String> homeKey = readKeyFromFile(Paths.get(userHome, ".gemini", "api.key"));
            if (homeKey.isPresent()) {
                log.info("Resolved Gemini API key from ~/.gemini/api.key");
                return new ResolvedKey(homeKey.get(), "User Home (~/.gemini/api.key)", false);
            }
        }

        // 4. Check Spring Config
        if (isValid(configuredKey)) {
            log.info("Resolved Gemini API key from Spring application.yml");
            return new ResolvedKey(configuredKey.trim(), "Application Config (application.yml)", false);
        }

        // 5. Fallback: Mock Mode
        log.warn("No Gemini API key found in Environment, System Properties, 'api.key' file, or Config. Falling back to MOCK AGENT MODE.");
        return new ResolvedKey("MOCK_KEY", "Mock Fallback (Offline Mode)", true);
    }

    private boolean isValid(String str) {
        return str != null && !str.trim().isBlank() && !str.equalsIgnoreCase("your_gemini_api_key_here");
    }

    private Optional<String> readKeyFromFile(Path path) {
        try {
            File file = path.toFile();
            if (file.exists() && file.isFile() && file.canRead()) {
                String content = Files.readString(path, StandardCharsets.UTF_8).replaceAll("\\s+", "").trim();
                if (isValid(content)) {
                    return Optional.of(content);
                }
            }
        } catch (IOException e) {
            log.debug("Could not read API key from {}: {}", path, e.getMessage());
        }
        return Optional.empty();
    }
}
