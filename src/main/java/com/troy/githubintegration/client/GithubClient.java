package com.troy.githubintegration.client;

import com.troy.githubintegration.exception.GithubError;
import com.troy.githubintegration.exception.GithubException;
import org.kohsuke.github.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
public class GithubClient {

    private final GitHub gitHub;

    public GithubClient(GitHub gitHub) {
        this.gitHub = gitHub;
    }

    /**
     * Clears kohsuke's internal user cache. The library keeps every user it has fetched and returns that copy
     * instead of calling GitHub again, so the cache must be cleared before a fetch to get a fresh profile.
     */
    public void clearLibraryCache() {
        gitHub.refreshCache();
    }

    public GithubUser getUser(String userName) {
        try {
            GHUser ghUser = gitHub.getUser(userName);

            Instant createdAt = null;
            if (ghUser.getCreatedAt() != null) {
                createdAt = ghUser.getCreatedAt().toInstant();
            }

            return new GithubUser(ghUser.getLogin(), ghUser.getName(), ghUser.getAvatarUrl(),
                    ghUser.getLocation(), ghUser.getEmail(), ghUser.getUrl().toString(), createdAt);
        } catch (IOException ex) {
            throw translateException(ex);
        }
    }

    /**
     * Fetches all public repositories for a user, 100 per page.
     * toList() unwraps paging failures back into IOException, so they are translated like any other call.
     */
    public List<GithubRepo> getUserRepositories(String login) {
        try {
            // Usually no extra HTTP call: kohsuke caches users by canonical login, and getUser() just fetched this
            // one. If a concurrent load for another user cleared that cache in between, this refetches the user.
            return gitHub.getUser(login).listRepositories(100).toList().stream()
                    .map(repo -> new GithubRepo(repo.getName(), repo.getUrl().toString()))
                    .toList();
        } catch (IOException ex) {
            throw translateException(ex);
        }
    }

    /**
     * Translates exceptions returned when talking with GitHub API into GithubException to be handled
     */
    private GithubException translateException(IOException ex) {
        if (ex instanceof GHFileNotFoundException) {
            return new GithubException(GithubError.USER_NOT_FOUND, ex);
        }
        if (ex instanceof HttpException http && (http.getResponseCode() == 403 || http.getResponseCode() == 429)) {
            return new GithubException(GithubError.RATE_LIMIT_REACHED, ex);
        }
        return new GithubException(GithubError.UNAVAILABLE, ex);
    }
}
