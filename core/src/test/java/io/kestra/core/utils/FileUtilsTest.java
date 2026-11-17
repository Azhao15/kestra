package io.kestra.core.utils;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUtilsTest {

    @Test
    void shouldGetExtension() {
        assertThat(FileUtils.getExtension((String) null)).isNull();
        assertThat(FileUtils.getExtension("")).isNull();
        assertThat(FileUtils.getExtension("/file/hello")).isNull();
        assertThat(FileUtils.getExtension("/file/hello.txt")).isEqualTo(".txt");
        assertThat(FileUtils.getExtension("/file/hello.file with spaces.txt")).isEqualTo(".txt");
        assertThat(FileUtils.getExtension("/file/hello.file.with.multiple.dots.txt")).isEqualTo(".txt");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "",
            "kestra:///ns/flow/exec/abc/output",
            "kestra:///ns/flow/exec/abc/.output",
            "kestra:///ns/flow/exec/abc/..output",
            "kestra:///ns/flow/exec/abc/output..",
        }
    )
    void isParentTraversal_false(String path) {
        assertThat(FileUtils.isParentTraversal(URI.create(path))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "kestra:///ns/flow/exec/abc/../../../etc/passwd",
            "kestra:///ns/flow/exec/abc/%2E%2E/%2E%2E/%2E%2E/etc/passwd",
            "kestra:///ns/flow/exec/abc/%2e%2e/%2e%2e/%2e%2e/etc/passwd",
            "kestra:///ns/flow/exec/abc%2F%2E%2E%2Fetc%2Fpasswd",
            "kestra:///ns/flow/exec/abc/%5C..%5C..%5C..%5Cetc/passwd",
            "kestra:///ns/flow/exec/abc/%2e%2e%5C%2e%2e%5Cetc%5Cpasswd",
            "kestra:///ns/_files/.%5C..%5C..%5Ctmp%5Cx",
            "kestra:///ns/flow/exec/abc/..",
            "kestra:///ns/flow/exec/abc/%00file"
        }
    )
    void isParentTraversal_true(String path) {
        assertThat(FileUtils.isParentTraversal(URI.create(path))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "file.txt", "dir/file.txt", "a\\b\\file.txt", "..\\..\\file.txt", "../../file.txt" })
    void toSafeFileName_shouldKeepBaseName(String fileName) {
        assertThat(FileUtils.toSafeFileName(fileName)).isEqualTo("file.txt");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "..", ".", "a/..", "a\\..", "a/", "a\\" })
    void toSafeFileName_shouldRejectInvalidNames(String fileName) {
        assertThatThrownBy(() -> FileUtils.toSafeFileName(fileName)).isInstanceOf(IllegalArgumentException.class);
    }
}
