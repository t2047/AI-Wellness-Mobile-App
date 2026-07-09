/**
 * @author Tao Yuchen
 * @author Zhao Lei
 */
package com.wellnessapp.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Loads the project-root {@code .env} file into Spring's {@code Environment}
 * <em>before</em> any {@code ${…}} placeholder resolution.
 *
 * <p>Runs as an {@link EnvironmentPostProcessor}, which is the earliest hook
 * in the Spring Boot lifecycle — before {@code @Value} injection, before
 * {@code application.yml} property resolution, before any bean creation.</p>
 *
 * <p>Resolution order for the {@code .env} file:</p>
 * <ol>
 *   <li>{@code .env} in the project root (detected by walking up from cwd)</li>
 *   <li>If not found, skips silently (OS env vars or defaults take over)</li>
 * </ol>
 *
 * @author ZHAO LEI
 */
public class DotenvConfig implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DotenvConfig.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                        SpringApplication application) {
        Path envFile = findEnvFile();
        if (envFile == null) {
            log.debug("No .env file found — skipping dotenv loading");
            return;
        }

        log.info("Loading .env from: {}", envFile.toAbsolutePath());

        Dotenv dotenv = Dotenv.configure()
                .directory(envFile.getParent().toString())
                .filename(envFile.getFileName().toString())
                .ignoreIfMissing()
                .load();

        Map<String, Object> props = new HashMap<>();
        for (var entry : dotenv.entries()) {
            String key = entry.getKey();
            // Only add if not already present in the Environment
            // (OS env vars and command-line args take precedence)
            if (!environment.containsProperty(key)) {
                props.put(key, entry.getValue());
            }
        }

        if (!props.isEmpty()) {
            environment.getPropertySources()
                    .addLast(new MapPropertySource("dotenv", props));
            log.info("Loaded {} keys from .env into Spring Environment", props.size());
        }
    }

    /**
     * Walk up from the current working directory to find the project-root {@code .env}.
     */
    private Path findEnvFile() {
        Path dir = Paths.get(System.getProperty("user.dir"));

        // Walk up at most 3 levels looking for .env
        for (int i = 0; i < 3; i++) {
            Path candidate = dir.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
            if (dir == null) break;
        }
        return null;
    }
}
