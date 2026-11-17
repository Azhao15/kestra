package io.kestra.storage.local;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.storage.StorageTestSuite;
import io.kestra.core.utils.IdUtils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalStorageTest extends StorageTestSuite {
    // Launch test from StorageTestSuite

    @Test
    void putLongObjectName() throws URISyntaxException, IOException {
        String longObjectName = "/" + RandomStringUtils.insecure().nextAlphanumeric(260).toLowerCase();

        URI put = storageInterface.put(
            IdUtils.create(),
            null,
            new URI(longObjectName),
            new ByteArrayInputStream("Hello World".getBytes())
        );

        assertThat(put.getPath(), not(longObjectName));
        String suffix = put.getPath().substring(7); // we remove the random 5 char + '-'
        assertTrue(longObjectName.endsWith(suffix));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "kestra:///ns/flow/executions/id/%5C..%5C..%5C..%5C..%5C..%5C..%5C..%5C..%5Cetc/passwd",
            "kestra:///ns/flow/executions/id/%2e%2e%5C%2e%2e%5C%2e%2e%5C%2e%2e%5C%2e%2e%5C%2e%2e%5Cetc%5Cpasswd",
            "kestra:///ns/_files/.%5C..%5C..%5Ctmp%5Cx"
        }
    )
    void shouldRejectBackslashParentTraversal(String uri) {
        String tenantId = IdUtils.create();
        assertThrows(IllegalArgumentException.class, () -> storageInterface.get(tenantId, null, URI.create(uri)));
        assertThrows(IllegalArgumentException.class, () -> storageInterface.exists(tenantId, null, URI.create(uri)));
        assertThrows(IllegalArgumentException.class, () -> storageInterface.put(tenantId, null, URI.create(uri), new ByteArrayInputStream("evil".getBytes())));
    }
}
