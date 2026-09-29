package com.troy.githubintegration.config;

import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAbuseLimitHandler;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.GitHubRateLimitHandler;
import org.kohsuke.github.extras.HttpClientGitHubConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(GithubProperties.class)
public class GithubClientConfig {

    private static final Logger log = LoggerFactory.getLogger(GithubClientConfig.class);

    @Bean
    GitHub gitHub(GithubProperties properties) throws IOException {
        // kohsuke's default connector sets no timeout, so an unreachable GitHub would hang the request thread
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        GitHubBuilder builder = new GitHubBuilder()
                .withEndpoint(properties.apiUrl())
                .withConnector(new HttpClientGitHubConnector(httpClient))
                .withRateLimitHandler(GitHubRateLimitHandler.FAIL) // fails call instead of blocking until limit reset
                .withAbuseLimitHandler(GitHubAbuseLimitHandler.FAIL);

        boolean hasToken = false;
        if (properties.token() != null && !properties.token().isBlank()) {
            builder.withOAuthToken(properties.token());
            hasToken = true;
        } else {
            log.warn("No GitHub token configured, requests are limited to 60/hour. Set GITHUB_TOKEN to raise the limit.");
        }

        log.info("GitHub client: endpoint={}, authenticated={}, connectTimeout={}, cache size={} ttl={}",
                properties.apiUrl(), hasToken, properties.connectTimeout(),
                properties.cache().size(), properties.cache().ttl());
        return builder.build();
    }
}
