package com.troy.githubintegration.controller;

import com.troy.githubintegration.service.GithubService;
import com.troy.githubintegration.data.GithubUserInfoResponse;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GithubController {

    private final GithubService githubService;

    public GithubController(GithubService githubService) {
        this.githubService = githubService;
    }

    /**
     * Gets the GitHub user info merged with repositories owned by that user
     */
    @GetMapping("/users/{username}")
    public GithubUserInfoResponse getUser(@PathVariable
                                          @Pattern(regexp = "^[A-Za-z0-9-]{1,39}$",
                                                  message = "Username must be 1-39 characters using only letters, digits, or hyphens")
                                          String username) {
        return githubService.getUserInfo(username);
    }
}
