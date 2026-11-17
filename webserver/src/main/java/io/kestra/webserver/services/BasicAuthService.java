package io.kestra.webserver.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.google.common.annotations.VisibleForTesting;

import io.kestra.core.exceptions.ValidationErrorException;
import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.AuthUtils;
import io.kestra.webserver.models.events.OssAuthEvent;

import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Context
@Singleton
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
@Requires(property = "micronaut.security.enabled", notEquals = "true") // don't load this in EE
public class BasicAuthService {
    public static final String BASIC_AUTH_SETTINGS_KEY = "kestra.server.basic-auth";
    public static final String BASIC_AUTH_ERROR_CONFIG = "kestra.server.authentication-configuration-error";
    public static final String BASIC_AUTH_SETUP_TOKEN_KEY = "kestra.server.basic-auth-setup-token";
    public static final String SETUP_TOKEN_HEADER = "X-Kestra-Setup-Token";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9_!#$%&’*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?=.{8,})(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).*");
    private static final int EMAIL_PASSWORD_MAX_LEN = 256;

    @Inject
    private SettingRepositoryInterface settingRepository;

    @Inject
    BasicAuthConfiguration basicAuthConfiguration;

    @Inject
    private InstanceService instanceService;

    @Inject
    private ApplicationEventPublisher<OssAuthEvent> ossAuthEventPublisher;

    public BasicAuthService(SettingRepositoryInterface settingRepository, BasicAuthConfiguration basicAuthConfiguration, InstanceService instanceService,
        ApplicationEventPublisher<OssAuthEvent> ossAuthEventPublisher) {
        this.settingRepository = settingRepository;
        this.basicAuthConfiguration = basicAuthConfiguration;
        this.instanceService = instanceService;
        this.ossAuthEventPublisher = ossAuthEventPublisher;
    }

    private volatile boolean configuredCredentialsInvalid = false;

    public BasicAuthService() {
    }

    @VisibleForTesting
    @PostConstruct
    public void init() {
        configuredCredentialsInvalid = false;
        if (
            basicAuthConfiguration == null ||
                (StringUtils.isBlank(basicAuthConfiguration.getUsername()) && StringUtils.isBlank(basicAuthConfiguration.getPassword()))
        ) {
            deleteSettingIfPresent(BASIC_AUTH_ERROR_CONFIG);
            if (!isBasicAuthInitialized()) {
                findSetupToken().ifPresentOrElse(this::logSetupToken, this::getOrCreateSetupToken);
            }
            return;
        }
        try {
            // save configured default credentials
            save(
                new BasicAuthCredentials(null, basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword())
            );
            deleteSettingIfPresent(BASIC_AUTH_ERROR_CONFIG);
            deleteSettingIfPresent(BASIC_AUTH_SETUP_TOKEN_KEY);
        } catch (ValidationErrorException e) {
            configuredCredentialsInvalid = true;
            deleteSettingIfPresent(BASIC_AUTH_SETUP_TOKEN_KEY);
            settingRepository.save(
                Setting.builder()
                    .key(BASIC_AUTH_ERROR_CONFIG)
                    .value(e.getInvalids())
                    .build()
            );
            log.error(
                "The configured 'kestra.server.basic-auth' credentials are invalid: {}. "
                    + "Basic authentication setup from the API is disabled until the configuration is fixed.",
                e.getInvalids()
            );
        }
    }

    /**
     * Saves credentials submitted through the API. While basic authentication is not initialized, the setup token
     * printed in the server logs at startup is required, and setup is refused when the configured credentials are invalid.
     */
    public void saveFromApi(BasicAuthCredentials basicAuthCredentials, @Nullable String setupToken) {
        boolean bootstrap = !isBasicAuthInitialized();
        if (bootstrap && !isValidSetupToken(setupToken)) {
            throw new HttpStatusException(HttpStatus.FORBIDDEN, setupRejectionMessage());
        }

        save(basicAuthCredentials);

        if (bootstrap) {
            deleteSettingIfPresent(BASIC_AUTH_SETUP_TOKEN_KEY);
        }
    }

    public String setupRejectionMessage() {
        return configuredCredentialsInvalid
            ? "The configured basic authentication credentials are invalid. Fix 'kestra.server.basic-auth' in the server configuration."
            : "Invalid or missing setup token. The setup token is printed in the Kestra server logs.";
    }

    public boolean isValidSetupToken(@Nullable String setupToken) {
        if (StringUtils.isBlank(setupToken) || configuredCredentialsInvalid || isBasicAuthInitialized()) {
            return false;
        }
        return findSetupToken()
            .map(
                expected -> MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    setupToken.getBytes(StandardCharsets.UTF_8)
                )
            )
            .orElse(false);
    }

    @VisibleForTesting
    public String getOrCreateSetupToken() {
        Optional<String> existing = findSetupToken();
        if (existing.isPresent()) {
            return existing.get();
        }

        String token = AuthUtils.generateSalt();
        settingRepository.save(Setting.builder().key(BASIC_AUTH_SETUP_TOKEN_KEY).value(token).build());
        logSetupToken(token);
        return token;
    }

    private Optional<String> findSetupToken() {
        return settingRepository.findByKey(BASIC_AUTH_SETUP_TOKEN_KEY)
            .map(Setting::getValue)
            .map(Object::toString)
            .filter(StringUtils::isNotBlank);
    }

    private void logSetupToken(String token) {
        log.warn(
            "\n*************************************************************\n"
                + "Basic authentication is not configured yet.\n"
                + "Use the following setup token to create the admin user:\n\n{}\n\n"
                + "*************************************************************",
            token
        );
    }

    private void deleteSettingIfPresent(String key) {
        if (settingRepository.findByKey(key).isPresent()) {
            settingRepository.delete(Setting.builder().key(key).build());
        }
    }

    public void save(BasicAuthCredentials basicAuthCredentials) {
        List<String> validationErrors = new ArrayList<>();

        if (basicAuthCredentials.getUsername() != null && !EMAIL_PATTERN.matcher(basicAuthCredentials.getUsername()).matches()) {
            validationErrors.add("Invalid username for Basic Authentication. Please provide a valid email address.");
        }

        if (basicAuthCredentials.getUsername() == null) {
            validationErrors.add("No user name set for Basic Authentication. Please provide a user name.");
        }

        if (basicAuthCredentials.getPassword() == null) {
            validationErrors.add("No password set for Basic Authentication. Please provide a password.");
        }

        if (basicAuthCredentials.getPassword() != null && !PASSWORD_PATTERN.matcher(basicAuthCredentials.getPassword()).matches()) {
            validationErrors.add("Invalid password for Basic Authentication. The password must have 8 chars, one upper, one lower and one number");
        }

        if (
            (basicAuthCredentials.getUsername() != null && basicAuthCredentials.getUsername().length() > EMAIL_PASSWORD_MAX_LEN) ||
                (basicAuthCredentials.getPassword() != null && basicAuthCredentials.getPassword().length() > EMAIL_PASSWORD_MAX_LEN)
        ) {
            validationErrors.add("The length of email or password should not exceed 256 characters.");
        }

        if (!validationErrors.isEmpty()) {
            throw new ValidationErrorException(validationErrors);
        }

        var previousConfiguredCredentials = this.configuration().credentials();
        String salt = previousConfiguredCredentials == null
            ? null
            : previousConfiguredCredentials.getSalt();
        SaltedBasicAuthCredentials saltedNewConfiguration = SaltedBasicAuthCredentials.salt(
            salt,
            basicAuthCredentials.getUsername(),
            basicAuthCredentials.getPassword()
        );
        if (!saltedNewConfiguration.equals(previousConfiguredCredentials)) {
            settingRepository.save(
                Setting.builder()
                    .key(BASIC_AUTH_SETTINGS_KEY)
                    .value(saltedNewConfiguration)
                    .build()
            );

            ossAuthEventPublisher.publishEventAsync(
                OssAuthEvent.builder()
                    .uid(basicAuthCredentials.getUid())
                    .iid(instanceService.fetch())
                    .date(Instant.now())
                    .ossAuth(
                        OssAuthEvent.OssAuth.builder()
                            .email(basicAuthCredentials.getUsername())
                            .build()
                    ).build()
            );
        }
    }

    public List<String> validationErrors() {
        return settingRepository.findByKey(BASIC_AUTH_ERROR_CONFIG)
            .map(Setting::getValue)
            .map(JacksonMapper::toList)
            .orElse(List.of());
    }

    public ConfiguredBasicAuth configuration() {
        var credentials = settingRepository.findByKey(BASIC_AUTH_SETTINGS_KEY)
            .map(Setting::getValue)
            .map(value -> JacksonMapper.ofJson(false).convertValue(value, SaltedBasicAuthCredentials.class))
            .orElse(null);
        return new ConfiguredBasicAuth(
            this.basicAuthConfiguration != null ? this.basicAuthConfiguration.realm : null, this.basicAuthConfiguration != null ? this.basicAuthConfiguration.openUrls : null, credentials
        );
    }

    public boolean isBasicAuthInitialized() {
        var configuration = configuration();

        return configuration.credentials() != null &&
            !StringUtils.isBlank(configuration.credentials().getUsername()) &&
            !StringUtils.isBlank(configuration.credentials().getPassword());
    }

    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    @ConfigurationProperties("kestra.server.basic-auth")
    @VisibleForTesting
    public static class BasicAuthConfiguration {
        private String username;
        protected String password;
        private String realm;
        private List<String> openUrls;

        @SuppressWarnings("MnInjectionPoints")
        @ConfigurationInject
        public BasicAuthConfiguration(
            @Nullable String username,
            @Nullable String password,
            @Nullable String realm,
            @Nullable List<String> openUrls) {
            this.username = username;
            this.password = password;
            this.realm = Optional.ofNullable(realm).orElse("Kestra");
            this.openUrls = Optional.ofNullable(openUrls).orElse(Collections.emptyList());
        }
    }

    public record ConfiguredBasicAuth(
        String realm,
        List<String> openUrls,
        SaltedBasicAuthCredentials credentials) {
    }

    @Getter
    @EqualsAndHashCode
    public static class SaltedBasicAuthCredentials {
        private String salt;
        private String username;
        protected String password;

        public SaltedBasicAuthCredentials(String salt, String username, String password) {
            Objects.requireNonNull(salt);
            Objects.requireNonNull(username);
            Objects.requireNonNull(password);
            this.salt = salt;
            this.username = username;
            this.password = password;
        }

        public static SaltedBasicAuthCredentials salt(String salt, String username, String password) {
            var salt1 = salt == null
                ? AuthUtils.generateSalt()
                : salt;
            return new SaltedBasicAuthCredentials(
                salt1,
                username,
                AuthUtils.encodePassword(salt1, password)
            );
        }
    }
}