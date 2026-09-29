package com.troy.githubintegration;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the full application with only GitHub replaced by a WireMock stub, so the real
 * kohsuke HTTP calls, JSON parsing and exception types are exercised. Each test uses its own username
 * because the Spring context, and therefore the cache, is shared between tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GithubUserEndpointIntegrationTest {

    @RegisterExtension
    static WireMockExtension github = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void githubProperties(DynamicPropertyRegistry registry) {
        registry.add("app.github.api-url", github::baseUrl);
        // Ignore any GITHUB_TOKEN in the environment so the test is deterministic
        registry.add("app.github.token", () -> "");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mergesUserAndReposIntoSpecFormat() throws Exception {
        stubUser("octocat");

        mockMvc.perform(get("/api/v1/users/octocat"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "user_name": "octocat",
                          "display_name": "The Octocat",
                          "avatar": "https://avatars.githubusercontent.com/u/583231?v=4",
                          "geo_location": "San Francisco",
                          "email": null,
                          "url": "https://api.github.com/users/octocat",
                          "created_at": "Tue, 25 Jan 2011 18:44:36 GMT",
                          "repos": [
                            {
                              "name": "boysenberry-repo-1",
                              "url": "https://api.github.com/repos/octocat/boysenberry-repo-1"
                            }
                          ]
                        }
                        """, true));
    }

    @Test
    void unknownGithubUserReturns404() throws Exception {
        github.stubFor(WireMock.get("/users/ghost").willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"message": "Not Found", "documentation_url": "https://docs.github.com/rest"}
                        """)));

        mockMvc.perform(get("/api/v1/users/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("GitHub user was not found"));
    }

    @Test
    void repeatedRequestIsServedFromCache() throws Exception {
        stubUser("cached-user");

        mockMvc.perform(get("/api/v1/users/cached-user")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/CACHED-USER")).andExpect(status().isOk());

        github.verify(1, getRequestedFor(urlPathEqualTo("/users/cached-user")));
        github.verify(1, getRequestedFor(urlPathEqualTo("/users/cached-user/repos")));
    }

    /** Minimal versions of GitHub's /users/{u} and /users/{u}/repos payloads, with only the fields we read. */
    private static void stubUser(String login) {
        github.stubFor(WireMock.get("/users/" + login).willReturn(okJson("""
                {
                  "login": "%s",
                  "name": "The Octocat",
                  "avatar_url": "https://avatars.githubusercontent.com/u/583231?v=4",
                  "location": "San Francisco",
                  "email": null,
                  "url": "https://api.github.com/users/%s",
                  "created_at": "2011-01-25T18:44:36Z"
                }
                """.formatted(login, login))));
        github.stubFor(WireMock.get(urlPathEqualTo("/users/" + login + "/repos")).willReturn(okJson("""
                [
                  {
                    "name": "boysenberry-repo-1",
                    "url": "https://api.github.com/repos/%s/boysenberry-repo-1"
                  }
                ]
                """.formatted(login))));
    }
}
