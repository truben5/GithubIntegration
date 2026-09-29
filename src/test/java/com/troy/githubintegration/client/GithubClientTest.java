package com.troy.githubintegration.client;

import com.troy.githubintegration.exception.GithubError;
import com.troy.githubintegration.exception.GithubException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHObject;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.HttpException;
import org.kohsuke.github.PagedIterable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubClientTest {

    private static final String USERS_URL = "https://api.github.com/users/octocat";

    @Mock
    private GitHub gitHub;

    @Mock
    private GHUser ghUser;

    @InjectMocks
    private GithubClient client;

    @Test
    void getUserMapsLibraryUserToRecord() throws IOException {
        Instant createdAt = Instant.parse("2011-01-25T18:44:36Z");
        when(gitHub.getUser("octocat")).thenReturn(ghUser);
        when(ghUser.getLogin()).thenReturn("octocat");
        when(ghUser.getName()).thenReturn("The Octocat");
        when(ghUser.getAvatarUrl()).thenReturn("https://avatars.githubusercontent.com/u/583231?v=4");
        when(ghUser.getLocation()).thenReturn("San Francisco");
        when(ghUser.getEmail()).thenReturn(null);
        stubUrl(ghUser, USERS_URL);
        when(ghUser.getCreatedAt()).thenReturn(Date.from(createdAt));

        GithubUser user = client.getUser("octocat");

        assertThat(user).isEqualTo(new GithubUser("octocat", "The Octocat",
                "https://avatars.githubusercontent.com/u/583231?v=4", "San Francisco", null, USERS_URL, createdAt));
    }

    @Test
    void getUserHandlesMissingCreatedAt() throws IOException {
        when(gitHub.getUser("octocat")).thenReturn(ghUser);
        stubUrl(ghUser, USERS_URL);
        when(ghUser.getCreatedAt()).thenReturn(null);

        assertThat(client.getUser("octocat").createdAt()).isNull();
    }

    @Test
    void getUserRepositoriesUsesApiUrlNotHtmlUrl() throws IOException {
        GHRepository repo = mock(GHRepository.class);
        when(repo.getName()).thenReturn("boysenberry-repo-1");
        stubUrl(repo, "https://api.github.com/repos/octocat/boysenberry-repo-1");
        stubRepositories(List.of(repo));

        List<GithubRepo> repos = client.getUserRepositories("octocat");

        assertThat(repos).containsExactly(
                new GithubRepo("boysenberry-repo-1", "https://api.github.com/repos/octocat/boysenberry-repo-1"));
    }

    @Test
    void getUserRepositoriesReturnsEmptyListWhenUserHasNoRepos() throws IOException {
        stubRepositories(List.of());

        assertThat(client.getUserRepositories("octocat")).isEmpty();
    }

    static Stream<Arguments> libraryExceptions() {
        return Stream.of(
                Arguments.of(new GHFileNotFoundException("Not Found"), GithubError.USER_NOT_FOUND),
                Arguments.of(httpException(403), GithubError.RATE_LIMIT_REACHED),
                Arguments.of(httpException(429), GithubError.RATE_LIMIT_REACHED),
                Arguments.of(httpException(500), GithubError.UNAVAILABLE),
                Arguments.of(new IOException("connection timed out"), GithubError.UNAVAILABLE));
    }

    @ParameterizedTest
    @MethodSource("libraryExceptions")
    void getUserTranslatesLibraryExceptions(IOException thrown, GithubError expected) throws IOException {
        when(gitHub.getUser("octocat")).thenThrow(thrown);

        assertThatThrownBy(() -> client.getUser("octocat"))
                .isInstanceOfSatisfying(GithubException.class, ex -> assertThat(ex.error()).isEqualTo(expected))
                .hasCause(thrown);
    }

    @ParameterizedTest
    @MethodSource("libraryExceptions")
    void getUserRepositoriesTranslatesPagingExceptions(IOException thrown, GithubError expected) throws IOException {
        PagedIterable<GHRepository> pages = stubRepositoryPages();
        when(pages.toList()).thenThrow(thrown);

        assertThatThrownBy(() -> client.getUserRepositories("octocat"))
                .isInstanceOfSatisfying(GithubException.class, ex -> assertThat(ex.error()).isEqualTo(expected))
                .hasCause(thrown);
    }

    private void stubRepositories(List<GHRepository> repos) throws IOException {
        when(stubRepositoryPages().toList()).thenReturn(repos);
    }

    @SuppressWarnings("unchecked")
    private PagedIterable<GHRepository> stubRepositoryPages() throws IOException {
        PagedIterable<GHRepository> pages = mock(PagedIterable.class);
        when(gitHub.getUser("octocat")).thenReturn(ghUser);
        when(ghUser.listRepositories(100)).thenReturn(pages);
        return pages;
    }

    /**
     * kohsuke's getUrl() is annotated @WithBridgeMethods, so the class has both a URL and a String getUrl().
     * Mockito validates when(..).thenReturn(URL) against the String bridge and rejects it; doAnswer skips that check.
     */
    private static void stubUrl(GHObject object, String url) {
        doAnswer(invocation -> url(url)).when(object).getUrl();
    }

    private static HttpException httpException(int status) {
        return new HttpException("GitHub error", status, "status " + status, USERS_URL);
    }

    private static URL url(String url) {
        try {
            return URI.create(url).toURL();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
