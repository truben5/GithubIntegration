package com.troy.githubintegration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test that the full context wires together (config properties, cache settings, GitHub client).
 * Runs with a mock servlet environment, so no HTTPS server is started and GitHub is never called.
 */
@SpringBootTest
class ApplicationTests {

    @Test
    void contextLoads() {
    }
}
