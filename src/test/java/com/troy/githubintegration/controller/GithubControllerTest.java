package com.troy.githubintegration.controller;

import com.troy.githubintegration.data.GithubRepoInfoResponse;
import com.troy.githubintegration.data.GithubUserInfoResponse;
import com.troy.githubintegration.exception.GithubError;
import com.troy.githubintegration.exception.GithubException;
import com.troy.githubintegration.service.GithubService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GithubController.class)
class GithubControllerTest {

    private static final String URL = "/api/v1/users/{username}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GithubService githubService;

    @Test
    void returnsMergedUserInSpecFormat() throws Exception {
        when(githubService.getUserInfo("octocat")).thenReturn(new GithubUserInfoResponse(
                "octocat", "The Octocat", "https://avatars.githubusercontent.com/u/583231?v=4", "San Francisco",
                null, "https://api.github.com/users/octocat", "Tue, 25 Jan 2011 18:44:36 GMT",
                List.of(new GithubRepoInfoResponse("boysenberry-repo-1",
                        "https://api.github.com/repos/octocat/boysenberry-repo-1"))));

        mockMvc.perform(get(URL, "octocat"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.user_name").value("octocat"))
                .andExpect(jsonPath("$.display_name").value("The Octocat"))
                .andExpect(jsonPath("$.avatar").value("https://avatars.githubusercontent.com/u/583231?v=4"))
                .andExpect(jsonPath("$.geo_location").value("San Francisco"))
                .andExpect(jsonPath("$.email").isEmpty())
                .andExpect(jsonPath("$.url").value("https://api.github.com/users/octocat"))
                .andExpect(jsonPath("$.created_at").value("Tue, 25 Jan 2011 18:44:36 GMT"))
                .andExpect(jsonPath("$.repos[0].name").value("boysenberry-repo-1"))
                .andExpect(jsonPath("$.repos[0].url").value("https://api.github.com/repos/octocat/boysenberry-repo-1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"oct!o", "octo_cat", "a1234567890123456789012345678901234567890"})
    void invalidUsernameReturns400WithoutCallingGithub(String username) throws Exception {
        mockMvc.perform(get(URL, username))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail")
                        .value("Username must be 1-39 characters using only letters, digits, or hyphens"));

        verify(githubService, never()).getUserInfo(anyString());
    }

    @Test
    void unknownUserReturns404() throws Exception {
        whenServiceFails(GithubError.USER_NOT_FOUND);

        mockMvc.perform(get(URL, "ghost"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("GitHub user was not found"));
    }

    @Test
    void rateLimitReturns503WithRetryAfter() throws Exception {
        whenServiceFails(GithubError.RATE_LIMIT_REACHED);

        mockMvc.perform(get(URL, "ghost"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.detail").value("GitHub rate limit reached"));
    }

    @Test
    void githubUnavailableReturns502() throws Exception {
        whenServiceFails(GithubError.UNAVAILABLE);

        mockMvc.perform(get(URL, "ghost"))
                .andExpect(status().isBadGateway())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.detail").value("GitHub is unavailable"));
    }

    @Test
    void unexpectedErrorReturnsGeneric500WithoutLeakingDetails() throws Exception {
        when(githubService.getUserInfo("octocat")).thenThrow(new IllegalStateException("secret internal detail"));

        mockMvc.perform(get(URL, "octocat"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Unexpected error"));
    }

    @Test
    void wrongMethodReturns405WithAllowHeader() throws Exception {
        mockMvc.perform(post(URL, "octocat"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unknownPathReturns404ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/nope"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private void whenServiceFails(GithubError error) {
        when(githubService.getUserInfo("ghost")).thenThrow(new GithubException(error, new IOException("upstream")));
    }
}
