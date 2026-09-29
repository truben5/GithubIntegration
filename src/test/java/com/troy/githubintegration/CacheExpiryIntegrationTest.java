package com.troy.githubintegration;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test with the real kohsuke library: once our cache entry expires, the next request must return
 * GitHub's current profile rather than the copy kohsuke keeps internally. Uses a short TTL, so it runs in its
 * own Spring context separate from {@link GithubUserEndpointIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CacheExpiryIntegrationTest {

    @RegisterExtension
    static WireMockExtension github = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void githubProperties(DynamicPropertyRegistry registry) {
        registry.add("app.github.api-url", github::baseUrl);
        registry.add("app.github.token", () -> "");
        registry.add("app.github.cache.ttl", () -> "200ms");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void expiredEntryReturnsFreshProfile() throws Exception {
        stubUser("Old Name");
        mockMvc.perform(get("/api/v1/users/octocat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.display_name").value("Old Name"));

        stubUser("New Name");
        Thread.sleep(400);

        mockMvc.perform(get("/api/v1/users/octocat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.display_name").value("New Name"));
        github.verify(2, getRequestedFor(urlPathEqualTo("/users/octocat")));
    }

    private static void stubUser(String name) {
        github.stubFor(WireMock.get("/users/octocat").willReturn(okJson("""
                {
                  "login": "octocat",
                  "name": "%s",
                  "url": "https://api.github.com/users/octocat",
                  "created_at": "2011-01-25T18:44:36Z"
                }
                """.formatted(name))));
        github.stubFor(WireMock.get(urlPathEqualTo("/users/octocat/repos")).willReturn(okJson("[]")));
    }
}
