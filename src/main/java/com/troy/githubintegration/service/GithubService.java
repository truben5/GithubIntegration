package com.troy.githubintegration.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.troy.githubintegration.client.GithubClient;
import com.troy.githubintegration.client.GithubRepo;
import com.troy.githubintegration.client.GithubUser;
import com.troy.githubintegration.config.GithubProperties;
import com.troy.githubintegration.data.GithubUserInfoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class GithubService {

    private static final Logger log = LoggerFactory.getLogger(GithubService.class);

    private final GithubClient githubClient;
    private final GithubUserMapper mapper;

    private final Cache<String, GithubUserInfoResponse> cache;

    public GithubService(GithubClient githubClient, GithubUserMapper mapper,
                         GithubProperties properties) {
        this.githubClient = githubClient;
        this.mapper = mapper;
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.cache().size())
                .expireAfterWrite(properties.cache().ttl())
                .build();
    }

    /**
     * Attempts to get GitHub user info from the cache. On miss, makes call to client
     */
    public GithubUserInfoResponse getUserInfo(String userName) {
        log.debug("Retrieving GitHub info for username {}", userName);
        // Locale.ROOT so the key doesn't change with the server's locale (e.g. Turkish dotless i)
        return cache.get(userName.toLowerCase(Locale.ROOT), key -> fetchUserInfo(userName));
    }

    /**
     * Cache miss: fetches the user, then their repos using GitHub's canonical login rather than the raw input.
     * <p>
     * kohsuke keeps every fetched user in its own internal map and returns that copy instead of calling GitHub,
     * so without clearing it first an expired entry would be reloaded with the stale profile. Clearing it here,
     * synchronously before the fetch, guarantees fresh data and keeps that map from growing.
     */
    private GithubUserInfoResponse fetchUserInfo(String userName) {
        log.debug("Cache miss, calling GitHub for {}", userName);
        githubClient.clearLibraryCache();
        GithubUser user = githubClient.getUser(userName);
        List<GithubRepo> repos = githubClient.getUserRepositories(user.login());
        return mapper.toResponse(user, repos);
    }
}
