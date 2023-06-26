package org.gradle.github.dependencygraph.fixture;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class TestConfig {

    public static final String TASK = ":plugin-test:writeTestConfig";
    private final Properties properties;

    public TestConfig() throws IOException {
        properties = new Properties();
        String testConfigResourcePath = "/test-config.properties";
        InputStream testConfigResourceStream = getClass().getResourceAsStream(testConfigResourcePath);
        if (testConfigResourceStream == null) {
            throw new RuntimeException(
                    "Test config properties resource file not found at " + testConfigResourcePath
                            + ". Run '" + TASK + "' task to generate it.");
        }
        properties.load(testConfigResourceStream);
    }

    public File asFile(String propertyName) {
        return new File(asString(propertyName));
    }

    String asString(String propertyName) {
        if (properties.containsKey(propertyName)) {
            return properties.getProperty(propertyName);
        } else {
            throw new IllegalStateException(
                    "Config property '" + propertyName + "' not set - rebuild config via `" + TASK + "`");
        }
    }

}
