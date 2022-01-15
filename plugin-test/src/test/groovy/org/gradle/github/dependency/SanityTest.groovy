package org.gradle.github.dependency

import org.gradle.github.dependency.fixture.TestConfig
import spock.lang.Specification

class SanityTest extends Specification {
    def "load TestConfig"() {
        when:
        new TestConfig()
        then:
        noExceptionThrown()
    }
}
