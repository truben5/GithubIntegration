package com.troy.githubintegration.service;

import com.troy.githubintegration.client.GithubRepo;
import com.troy.githubintegration.client.GithubUser;
import com.troy.githubintegration.data.GithubRepoInfoResponse;
import com.troy.githubintegration.data.GithubUserInfoResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GithubUserMapperTest {

    private final GithubUserMapper mapper = new GithubUserMapper();

    @Test
    void mapsAllUserFieldsAndFormatsCreatedAtAsRfc1123() {
        GithubUser user = new GithubUser("octocat", "The Octocat", "https://avatars.githubusercontent.com/u/583231?v=4",
                "San Francisco", null, "https://api.github.com/users/octocat", Instant.parse("2011-01-25T18:44:36Z"));

        GithubUserInfoResponse response = mapper.toResponse(user, List.of());

        assertThat(response.userName()).isEqualTo("octocat");
        assertThat(response.displayName()).isEqualTo("The Octocat");
        assertThat(response.avatar()).isEqualTo("https://avatars.githubusercontent.com/u/583231?v=4");
        assertThat(response.geoLocation()).isEqualTo("San Francisco");
        assertThat(response.email()).isNull();
        assertThat(response.url()).isEqualTo("https://api.github.com/users/octocat");
        assertThat(response.createdAt()).isEqualTo("Tue, 25 Jan 2011 18:44:36 GMT");
    }

    @Test
    void createdAtZeroPadsSingleDigitDay() {
        GithubUser user = new GithubUser("octocat", null, null, null, null, null, Instant.parse("2011-01-05T08:04:06Z"));

        assertThat(mapper.toResponse(user, List.of()).createdAt()).isEqualTo("Wed, 05 Jan 2011 08:04:06 GMT");
    }

    @Test
    void nullCreatedAtMapsToNull() {
        GithubUser user = new GithubUser("octocat", null, null, null, null, null, null);

        GithubUserInfoResponse response = mapper.toResponse(user, List.of());

        assertThat(response.createdAt()).isNull();
        assertThat(response.displayName()).isNull();
        assertThat(response.geoLocation()).isNull();
    }

    @Test
    void mapsReposInOrder() {
        GithubUser user = new GithubUser("octocat", null, null, null, null, null, null);
        List<GithubRepo> repos = List.of(
                new GithubRepo("boysenberry-repo-1", "https://api.github.com/repos/octocat/boysenberry-repo-1"),
                new GithubRepo("git-consortium", "https://api.github.com/repos/octocat/git-consortium"));

        GithubUserInfoResponse response = mapper.toResponse(user, repos);

        assertThat(response.repos()).containsExactly(
                new GithubRepoInfoResponse("boysenberry-repo-1", "https://api.github.com/repos/octocat/boysenberry-repo-1"),
                new GithubRepoInfoResponse("git-consortium", "https://api.github.com/repos/octocat/git-consortium"));
    }

    @Test
    void noReposMapsToEmptyList() {
        GithubUser user = new GithubUser("octocat", null, null, null, null, null, null);

        assertThat(mapper.toResponse(user, List.of()).repos()).isEmpty();
    }
}
