package io.kestra.plugin.scripts.runner.docker;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.core.runners.RunContext;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DockerHostConfigGateTest {
    private static RunContext runContext(Map<String, Object> pluginConfiguration) {
        RunContext runContext = Mockito.mock(RunContext.class);
        Mockito.when(runContext.pluginConfiguration(Mockito.anyString()))
            .thenAnswer(invocation -> Optional.ofNullable(pluginConfiguration.get(invocation.<String> getArgument(0))));
        return runContext;
    }

    @Test
    void shouldAllowDefaultOptionsWithoutConfiguration() {
        RunContext runContext = runContext(Map.of());

        assertThatCode(() -> Docker.checkHostConfigAllowed(runContext, null, null, false)).doesNotThrowAnyException();
        assertThatCode(() -> Docker.checkHostConfigAllowed(runContext, false, "", false)).doesNotThrowAnyException();
        for (String mode : List.of("default", "bridge", "none")) {
            assertThatCode(() -> Docker.checkHostConfigAllowed(runContext, false, mode, false)).doesNotThrowAnyException();
        }
    }

    @Test
    void shouldRejectPrivilegedByDefault() {
        assertThatThrownBy(() -> Docker.checkHostConfigAllowed(runContext(Map.of()), true, null, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(Docker.PRIVILEGED_ENABLED_CONFIG);
        assertThatThrownBy(() -> Docker.checkHostConfigAllowed(runContext(Map.of(Docker.PRIVILEGED_ENABLED_CONFIG, false)), true, null, false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowPrivilegedWhenEnabled() {
        assertThatCode(() -> Docker.checkHostConfigAllowed(runContext(Map.of(Docker.PRIVILEGED_ENABLED_CONFIG, true)), true, null, false))
            .doesNotThrowAnyException();
        assertThatCode(() -> Docker.checkHostConfigAllowed(runContext(Map.of(Docker.PRIVILEGED_ENABLED_CONFIG, "true")), true, null, false))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDeviceRequestsByDefault() {
        assertThatThrownBy(() -> Docker.checkHostConfigAllowed(runContext(Map.of()), null, null, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(Docker.DEVICE_REQUESTS_ENABLED_CONFIG);
        assertThatCode(() -> Docker.checkHostConfigAllowed(runContext(Map.of(Docker.DEVICE_REQUESTS_ENABLED_CONFIG, true)), null, null, true))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNonDefaultNetworkModesByDefault() {
        for (String mode : List.of("host", "Host", "container:abc", "my-network")) {
            assertThatThrownBy(() -> Docker.checkHostConfigAllowed(runContext(Map.of()), null, mode, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(Docker.ALLOWED_NETWORK_MODES_CONFIG);
        }
    }

    @Test
    void shouldUseConfiguredNetworkModeAllowlist() {
        RunContext runContext = runContext(Map.of(Docker.ALLOWED_NETWORK_MODES_CONFIG, List.of("host", "my-network")));

        assertThatCode(() -> Docker.checkHostConfigAllowed(runContext, null, "host", false)).doesNotThrowAnyException();
        assertThatCode(() -> Docker.checkHostConfigAllowed(runContext, null, "my-network", false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> Docker.checkHostConfigAllowed(runContext, null, "bridge", false))
            .isInstanceOf(IllegalArgumentException.class);

        RunContext commaSeparated = runContext(Map.of(Docker.ALLOWED_NETWORK_MODES_CONFIG, "bridge, host"));
        assertThatCode(() -> Docker.checkHostConfigAllowed(commaSeparated, null, "host", false)).doesNotThrowAnyException();

        RunContext allowAll = runContext(Map.of(Docker.ALLOWED_NETWORK_MODES_CONFIG, List.of("*")));
        assertThatCode(() -> Docker.checkHostConfigAllowed(allowAll, null, "container:abc", false)).doesNotThrowAnyException();
    }
}
