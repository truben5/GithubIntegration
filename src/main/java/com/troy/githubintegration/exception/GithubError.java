package com.troy.githubintegration.exception;

import org.springframework.http.HttpStatus;

public enum GithubError {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "GitHub user was not found"),
    RATE_LIMIT_REACHED(HttpStatus.SERVICE_UNAVAILABLE, "GitHub rate limit reached"),
    UNAVAILABLE(HttpStatus.BAD_GATEWAY, "GitHub is unavailable");

    private final HttpStatus httpStatus;
    private final String message;

    GithubError(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus status() { return httpStatus; }
    public String message() { return message; }
}
