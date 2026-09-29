package com.troy.githubintegration.service;

import com.troy.githubintegration.client.GithubRepo;
import com.troy.githubintegration.client.GithubUser;
import com.troy.githubintegration.data.GithubRepoInfoResponse;
import com.troy.githubintegration.data.GithubUserInfoResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Maps the client's GitHub records into the API response
 */
@Component
public class GithubUserMapper {

    // HTTP-date (RFC 9110 IMF-fixdate) in GMT, e.g. "Tue, 25 Jan 2011 18:44:36 GMT". Unlike RFC_1123_DATE_TIME,
    // this always zero-pads the day ("05", not "5"). DateTimeFormatter is immutable and thread-safe
    private static final DateTimeFormatter CREATED_AT_FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);

    public GithubUserInfoResponse toResponse(GithubUser user, List<GithubRepo> repos) {
        return new GithubUserInfoResponse(user.login(), user.name(), user.avatarUrl(),
                user.location(), user.email(), user.url(), formatDate(user.createdAt()), toRepoResponses(repos));
    }

    private List<GithubRepoInfoResponse> toRepoResponses(List<GithubRepo> repos) {
        return repos.stream()
                .map(repo -> new GithubRepoInfoResponse(repo.name(), repo.url()))
                .toList();
    }

    private String formatDate(Instant date) {
        return date == null ? null : CREATED_AT_FORMAT.format(date);
    }
}
