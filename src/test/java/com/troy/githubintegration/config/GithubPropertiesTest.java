package com.troy.githubintegration.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GithubPropertiesTest {

    @Test
    void toStringMasksToken() {
        GithubProperties properties = new GithubProperties("https://api.github.com", "ghp_secret123",
                Duration.ofSeconds(5), new GithubProperties.Cache(10, Duration.ofMinutes(15)));

        assertThat(properties.toString())
                .doesNotContain("ghp_secret123")
                .contains("token=****")
                .contains("apiUrl=https://api.github.com");
    }

    @Test
    void toStringShowsWhenNoTokenIsSet() {
        GithubProperties properties = new GithubProperties("https://api.github.com", null,
                Duration.ofSeconds(5), new GithubProperties.Cache(10, Duration.ofMinutes(15)));

        assertThat(properties.toString()).contains("token=<none>");
    }
}
