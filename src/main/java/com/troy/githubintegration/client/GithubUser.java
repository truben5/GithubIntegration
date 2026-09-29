package com.troy.githubintegration.client;

import java.time.Instant;

public record GithubUser(String login, String name, String avatarUrl, String location,
                         String email, String url, Instant createdAt) {}
