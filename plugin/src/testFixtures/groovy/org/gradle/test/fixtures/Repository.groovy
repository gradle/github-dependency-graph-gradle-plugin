package org.gradle.test.fixtures

public interface Repository {
    URI getUri()

    /**
     * Defaults version to '1.0'
     */
    Module module(String group, String module)

    Module module(String group, String module, String version)
}
