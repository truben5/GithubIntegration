package com.troy.githubintegration.exception;

public class GithubException extends RuntimeException {
    private final GithubError error;

    public GithubException(GithubError error, Throwable cause) {
        super(error.message(), cause);
        this.error = error;
    }

    public GithubError error() { return error; }
}
