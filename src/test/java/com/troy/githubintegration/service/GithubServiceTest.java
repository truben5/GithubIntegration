package com.troy.githubintegration.service;

import com.troy.githubintegration.client.GithubClient;
import com.troy.githubintegration.client.GithubRepo;
import com.troy.githubintegration.client.GithubUser;
import com.troy.githubintegration.config.GithubProperties;
import com.troy.githubintegration.data.GithubUserInfoResponse;
import com.troy.githubintegration.exception.GithubError;
import com.troy.githubintegration.exception.GithubException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubServiceTest {

    private static final Duration TTL = Duration.ofMinutes(15);

    @Mock
    private GithubClient client;

    private GithubService service;

    @BeforeEach
    void setUp() {
        service = newService(10, TTL);
    }

    @Test
    void cacheMissFetchesUserThenReposByCanonicalLogin() {
        stubUser("OctoCat", "octocat");

        GithubUserInfoResponse response = service.getUserInfo("OctoCat");

        assertThat(response.userName()).isEqualTo("octocat");
        assertThat(response.repos()).extracting("name").containsExactly("octocat-repo");
        // repos are fetched with the login GitHub returned, not the raw input
        verify(client).getUserRepositories("octocat");
    }

    @Test
    void secondRequestIsServedFromCache() {
        stubUser("octocat", "octocat");

        GithubUserInfoResponse first = service.getUserInfo("octocat");
        GithubUserInfoResponse second = service.getUserInfo("octocat");

        assertThat(second).isSameAs(first);
        verify(client, times(1)).getUser("octocat");
        verify(client, times(1)).getUserRepositories("octocat");
    }

    @Test
    void cacheKeyIsCaseInsensitive() {
        stubUser("OctoCat", "octocat");

        service.getUserInfo("OctoCat");
        service.getUserInfo("octocat");
        service.getUserInfo("OCTOCAT");

        verify(client, times(1)).getUser("OctoCat");
        verify(client, never()).getUser("octocat");
        verify(client, never()).getUser("OCTOCAT");
    }

    @Test
    void failuresAreNotCached() {
        when(client.getUser("octocat"))
                .thenThrow(new GithubException(GithubError.UNAVAILABLE, new RuntimeException("boom")))
                .thenReturn(user("octocat"));
        when(client.getUserRepositories("octocat")).thenReturn(List.of());

        assertThatThrownBy(() -> service.getUserInfo("octocat")).isInstanceOf(GithubException.class);
        GithubUserInfoResponse retry = service.getUserInfo("octocat");

        assertThat(retry.userName()).isEqualTo("octocat");
        verify(client, times(2)).getUser("octocat");
    }

    @Test
    void userNotFoundIsPropagated() {
        when(client.getUser("ghost"))
                .thenThrow(new GithubException(GithubError.USER_NOT_FOUND, new RuntimeException("404")));

        assertThatThrownBy(() -> service.getUserInfo("ghost"))
                .isInstanceOfSatisfying(GithubException.class,
                        ex -> assertThat(ex.error()).isEqualTo(GithubError.USER_NOT_FOUND));
        verify(client, never()).getUserRepositories("ghost");
    }

    @Test
    void cacheMissClearsLibraryCacheBeforeFetching() {
        stubUser("octocat", "octocat");

        service.getUserInfo("octocat");

        // Must happen before getUser, otherwise kohsuke could return its stale copy of the user
        InOrder inOrder = inOrder(client);
        inOrder.verify(client).clearLibraryCache();
        inOrder.verify(client).getUser("octocat");
    }

    @Test
    void cacheHitDoesNotClearLibraryCache() {
        stubUser("octocat", "octocat");

        service.getUserInfo("octocat");
        service.getUserInfo("octocat");

        verify(client, times(1)).clearLibraryCache();
    }

    @Test
    void expiredEntryIsRefetchedWithLibraryCacheCleared() throws InterruptedException {
        service = newService(10, Duration.ofMillis(50));
        stubUser("octocat", "octocat");

        service.getUserInfo("octocat");
        // Sleeping past the TTL is safe: Caffeine checks expiry on read and never returns an expired entry
        Thread.sleep(150);
        service.getUserInfo("octocat");

        verify(client, times(2)).clearLibraryCache();
        verify(client, times(2)).getUser("octocat");
        verify(client, times(2)).getUserRepositories("octocat");
    }

    private GithubService newService(int cacheSize, Duration cacheTtl) {
        GithubProperties properties = new GithubProperties("https://api.github.com", null, Duration.ofSeconds(5),
                new GithubProperties.Cache(cacheSize, cacheTtl));
        return new GithubService(client, new GithubUserMapper(), properties);
    }

    private void stubUser(String requested, String login) {
        when(client.getUser(requested)).thenReturn(user(login));
        when(client.getUserRepositories(login))
                .thenReturn(List.of(new GithubRepo(login + "-repo", "https://api.github.com/repos/" + login + "/" + login + "-repo")));
    }

    private static GithubUser user(String login) {
        return new GithubUser(login, "Display " + login, "https://avatars.example/" + login, "Earth", null,
                "https://api.github.com/users/" + login, Instant.parse("2011-01-25T18:44:36Z"));
    }
}
