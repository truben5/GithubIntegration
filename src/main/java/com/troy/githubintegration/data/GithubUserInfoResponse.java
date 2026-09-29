package com.troy.githubintegration.data;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Response body, serialized in snake_case (user_name, display_name, ...) as required by the spec.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GithubUserInfoResponse(String userName, String displayName, String avatar, String geoLocation,
                                     String email, String url, String createdAt, List<GithubRepoInfoResponse> repos) {

    public GithubUserInfoResponse {
        // Defensive copy so cached responses can't be modified
        repos = repos == null ? List.of() : List.copyOf(repos);
    }
}
