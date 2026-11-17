package io.kestra.core.utils;

import java.net.URI;
import java.util.Optional;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Utility methods for manipulating files.
 */
public final class FileUtils {

    /**
     * Get the file extension prefixed the '.' from the given file URI.
     *
     * @param file the name or path of the file.
     * @return the file extension prefixed with the '.' or {@code null}.
     */
    public static String getExtension(final URI file) {
        return file == null ? null : getExtension(file.toString());
    }

    /**
     * Get the file extension prefixed the '.' from the given file name or file path.
     *
     * @param file the name or path of the file.
     * @return the file extension prefixed with the '.' or {@code null}.
     */
    public static String getExtension(final String file) {
        if (file == null)
            return null;
        String extension = FilenameUtils.getExtension(file);
        return StringUtils.isEmpty(extension) ? null : "." + extension;
    }

    /**
     * Creates a new {@link URI} from the given string path.
     *
     * @param path the string path - may be {@code null}.
     * @return an optional URI, or {@link Optional#empty()} if the given path represent an invalid URI.
     */
    public static Optional<URI> getURI(final String path) {
        if (path == null)
            return Optional.empty();
        try {
            return Optional.of(URI.create(path));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Extracts the file name from the given URI.
     *
     * @param uri the file URI.
     * @return the string file name.
     */
    public static String getFileName(final URI uri) {
        String path = uri.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * Check if the provided URI does not contain relative parent path traversal (i.e., "..").
     *
     * @param uri the URI to validate
     * @return true if there is a relative parent path traversal
     */
    public static boolean isParentTraversal(URI uri) {
        if (uri == null) {
            return false;
        }
        var path = uri.getPath();
        if (path == null) {
            return false;
        }
        if (path.indexOf('\0') >= 0) {
            return true;
        }
        for (String segment : path.replace('\\', '/').split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reduces a client-provided file name to a safe base name: any directory part (using either '/' or '\\' as separator)
     * is removed, and names that would resolve to the current or parent directory are rejected.
     *
     * @param fileName the client-provided file name
     * @return the safe base name
     * @throws IllegalArgumentException if no safe base name can be extracted
     */
    public static String toSafeFileName(String fileName) {
        if (fileName == null) {
            throw new IllegalArgumentException("File name cannot be null");
        }
        String normalized = fileName.replace('\\', '/');
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (baseName.isBlank() || baseName.equals(".") || baseName.equals("..") || baseName.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid file name '" + fileName + "'");
        }
        return baseName;
    }
}
