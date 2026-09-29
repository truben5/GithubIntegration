package com.troy.githubintegration.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * All {@code app.github.*} settings, validated at startup so a bad value fails fast with a clear message.
 *
 * @param apiUrl         base URL of the GitHub API. Overridable for GitHub Enterprise or a stub server in tests.
 * @param token          optional GitHub personal access token. Without one GitHub allows 60 requests/hour, with one 5,000.
 * @param connectTimeout how long to wait when connecting to GitHub before giving up
 * @param cache          response cache settings
 */
@Validated
@ConfigurationProperties(prefix = "app.github")
public record GithubProperties(@NotBlank String apiUrl,
                               String token,
                               @NotNull Duration connectTimeout,
                               @Valid @NotNull Cache cache) {

    /**
     * Masks the token so it can't leak if this record is ever logged (a record's default toString includes every field).
     */
    @Override
    public String toString() {
        String maskedToken = (token == null || token.isBlank()) ? "<none>" : "****";
        return "GithubProperties[apiUrl=" + apiUrl + ", token=" + maskedToken + ", connectTimeout=" + connectTimeout
                + ", cache=" + cache + "]";
    }

    /**
     * @param size maximum number of cached users
     * @param ttl  how long an entry stays cached after it is fetched
     */
    public record Cache(@Positive int size, @NotNull Duration ttl) {}
}
