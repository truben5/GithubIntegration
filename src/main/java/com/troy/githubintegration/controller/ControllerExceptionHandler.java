package com.troy.githubintegration.controller;

import com.troy.githubintegration.exception.GithubError;
import com.troy.githubintegration.exception.GithubException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ControllerExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ControllerExceptionHandler.class);

    /**
     * Handles exceptions thrown by invalid input to api
     *
     * @return HttpStatus Bad Request with first reason
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleInvalidInput(HandlerMethodValidationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                ex.getAllErrors().getFirst().getDefaultMessage());
    }

    /**
     * Handles exceptions that have been translated from the GitHub api
     *
     * @param ex contains the type of GitHub error and message to send back
     */
    @ExceptionHandler(GithubException.class)
    ResponseEntity<ProblemDetail> handleGithubException(GithubException ex) {
        GithubError error = ex.error();
        if (error != GithubError.USER_NOT_FOUND) {
            log.warn("GitHub call failed: {}", error, ex);
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(error.status());
        if (error == GithubError.RATE_LIMIT_REACHED) {
            response.header(HttpHeaders.RETRY_AFTER, "60");
        }
        return response.body(ProblemDetail.forStatusAndDetail(error.status(), error.message()));
    }

    /**
     * Catch-all: passes Spring's own errors (404, 405, ...) through with their status and headers, and turns
     * anything else into a generic 500. Details are logged but never sent to the client.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpectedException(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            // Spring's own 400/404/405/415s: keep their status, body and headers (e.g. Allow on 405)
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .headers(errorResponse.getHeaders())
                    .body(errorResponse.getBody());
        }
        log.error("Unhandled error", ex);
        return ResponseEntity.internalServerError()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"));
    }
}
