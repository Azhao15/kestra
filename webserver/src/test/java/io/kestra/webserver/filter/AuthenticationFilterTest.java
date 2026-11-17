package io.kestra.webserver.filter;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.BasicAuthCredentials;
import io.kestra.webserver.services.BasicAuthService;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

import static io.kestra.webserver.services.BasicAuthService.BASIC_AUTH_SETTINGS_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class AuthenticationFilterTest {
    @Inject
    @Client("/")
    private ReactorHttpClient client;

    @Inject
    private BasicAuthService basicAuthService;

    @Inject
    private BasicAuthService.BasicAuthConfiguration basicAuthConfiguration;

    @Inject
    private AuthenticationFilter filter;

    @Inject
    private SettingRepositoryInterface settingRepository;

    @Test
    void testConfigEndpointAlwaysOpen() {
        var response = client.toBlocking()
            .exchange(HttpRequest.GET("/api/v1/configs").basicAuth("anonymous", "hacker"));
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void testBasicAuthOpenedBeforeSetupOnly() {
        TestAuthFilter.ENABLED = false;

        HttpClientResponseException httpClientResponseException = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking()
                .exchange(HttpRequest.GET("/api/v1/basicAuthValidationErrors"))
        );
        assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());

        httpClientResponseException = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking()
                .exchange(
                    HttpRequest.POST(
                        "/api/v1/basicAuth", new BasicAuthCredentials(
                            IdUtils.create(),
                            "anonymous",
                            "hacker"
                        )
                    )
                )
        );
        assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());

        HttpResponse<?> response = client.toBlocking()
            .exchange(
                HttpRequest.POST(
                    "/api/v1/basicAuth", new BasicAuthCredentials(
                        IdUtils.create(),
                        "anonymous@hacker",
                        "hackerPassword1"
                    )
                ).basicAuth(basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword())
            );
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());

        response = client.toBlocking()
            .exchange(HttpRequest.GET("/api/v1/basicAuthValidationErrors").basicAuth("anonymous@hacker", "hackerPassword1"));
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

        // Only 1 basic auth user is allowed so the previous one is overridden
        httpClientResponseException = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking()
                .exchange(HttpRequest.GET("/api/v1/basicAuthValidationErrors").basicAuth(basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword()))
        );
        assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());

        assertThat(basicAuthService.isBasicAuthInitialized()).isTrue();
        settingRepository.delete(Setting.builder().key(BASIC_AUTH_SETTINGS_KEY).build());
        assertThat(basicAuthService.isBasicAuthInitialized()).isFalse();

        response = client.toBlocking()
            .exchange(HttpRequest.GET("/api/v1/basicAuthValidationErrors"));
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

        var setupCredentials = new BasicAuthCredentials(
            IdUtils.create(),
            basicAuthConfiguration.getUsername(),
            basicAuthConfiguration.getPassword()
        );

        httpClientResponseException = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking()
                .exchange(HttpRequest.POST("/api/v1/basicAuth", setupCredentials))
        );
        assertThat(httpClientResponseException.getStatus().getCode()).as("setup without token is rejected").isEqualTo(HttpStatus.FORBIDDEN.getCode());

        httpClientResponseException = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking()
                .exchange(HttpRequest.POST("/api/v1/basicAuth", setupCredentials).header(BasicAuthService.SETUP_TOKEN_HEADER, "wrong-token"))
        );
        assertThat(httpClientResponseException.getStatus().getCode()).as("setup with wrong token is rejected").isEqualTo(HttpStatus.FORBIDDEN.getCode());
        assertThat(httpClientResponseException.getResponse().getHeaders().get("WWW-Authenticate")).as("no browser basic auth prompt during setup").isNull();
        assertThat(basicAuthService.isBasicAuthInitialized()).isFalse();

        String setupToken = basicAuthService.getOrCreateSetupToken();
        response = client.toBlocking()
            .exchange(HttpRequest.POST("/api/v1/basicAuth", setupCredentials).header(BasicAuthService.SETUP_TOKEN_HEADER, setupToken));
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());

        assertThat(basicAuthService.isBasicAuthInitialized()).isTrue();

        httpClientResponseException = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking()
                .exchange(
                    HttpRequest.POST("/api/v1/basicAuth", new BasicAuthCredentials(IdUtils.create(), "anonymous@hacker", "hackerPassword1"))
                        .header(BasicAuthService.SETUP_TOKEN_HEADER, setupToken)
                )
        );
        assertThat(httpClientResponseException.getStatus().getCode()).as("setup token cannot be reused once initialized").isEqualTo(HttpStatus.UNAUTHORIZED.getCode());

        TestAuthFilter.ENABLED = true;
    }

    @Test
    void shouldWorkWithoutPersistedConfiguration() {
        settingRepository.delete(Setting.builder().key(BASIC_AUTH_SETTINGS_KEY).build());
        var response = client.toBlocking()
            .exchange(HttpRequest.GET("/api/v1/configs").basicAuth("anonymous", "hacker"));
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void testUnauthorized() {
        HttpClientResponseException httpClientResponseException = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking()
                .exchange(HttpRequest.GET("/api/v1/main/dashboards").header("Authorization", ""))
        );
        assertThat(httpClientResponseException.getResponse().getHeaders().get("WWW-Authenticate")).isEqualTo("Basic");

        httpClientResponseException = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking()
                .exchange(HttpRequest.GET("/api/v1/main/dashboards").basicAuth("anonymous", "hacker"))
        );
        assertThat(httpClientResponseException.getResponse().getHeaders().get("WWW-Authenticate")).isEqualTo("Basic");

        httpClientResponseException = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking()
                .exchange(HttpRequest.GET("/api/v1/main/dashboards").header("Authorization", "").header("Referer", "http://localhost/login"))
        );
        assertThat(httpClientResponseException.getResponse().getHeaders().get("WWW-Authenticate")).isNull();
    }

    @Test
    void testAnonymous() {
        var response = client.toBlocking().exchange("/ping");

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void testManagementEndpoint() {
        var response = client.toBlocking().exchange("/health");

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void testAuthenticated() {
        var response = client.toBlocking()
            .exchange(
                HttpRequest.GET("/api/v1/configs").basicAuth(
                    basicAuthConfiguration.getUsername(),
                    basicAuthConfiguration.getPassword()
                )
            );

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void should_unauthorized_with_wrong_username() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking()
                .exchange(
                    HttpRequest.GET("/api/v1/main/dashboards").basicAuth(
                        "incorrect",
                        basicAuthConfiguration.getPassword()
                    )
                )
        );

        assertThat(e.getResponse().getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
    }

    @Test
    void should_unauthorized_with_wrong_password() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking()
                .exchange(
                    HttpRequest.GET("/api/v1/main/dashboards").basicAuth(
                        basicAuthConfiguration.getUsername(),
                        "incorrect"
                    )
                )
        );

        assertThat(e.getResponse().getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
    }

    @Test
    void should_unauthorized_without_token() {
        MutableHttpResponse<?> response = Mono.from(
            filter.doFilter(
                HttpRequest.GET("/api/v1/main/dashboards"), null
            )
        ).block();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
    }
}
