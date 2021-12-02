package org.gradle.github.dependency.extractor

import org.gradle.github.dependency.extractor.fixture.TestConfig
import spock.lang.Specification

class SanityTest extends Specification {
    def "load TestConfig"() {
        when:
        new TestConfig()
        then:
        noExceptionThrown()
    }
}
