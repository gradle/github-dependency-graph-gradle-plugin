package org.gradle.github.dependencygraph.internal

import org.gradle.dependencygraph.extractor.ResolvedConfigurationFilter
import spock.lang.Specification

class ResolvedConfigurationTest extends Specification {
    def "null filter includes everything"() {
        when:
        def filter = new ResolvedConfigurationFilter(null, null)

        then:
        filter.include("foo", "bar")
        filter.include(":foo:bar:baz", "not a real name")
        filter.include("", "")
    }

    def "filters on exact configuration name"() {
        when:
        def filter = new ResolvedConfigurationFilter(null, "compileClasspath")

        then:
        filter.include("", "compileClasspath")

        !filter.include("", "classpath")
        !filter.include("", "runtimeClasspath")
        !filter.include("", "testCompileClasspath")
    }

    def "filters on exact configuration names"() {
        when:
        def filter = new ResolvedConfigurationFilter(null, "compileClasspath|runtimeClasspath")

        then:
        filter.include("", "compileClasspath")
        filter.include("", "runtimeClasspath")

        !filter.include("", "classpath")
        !filter.include("", "testCompileClasspath")
        !filter.include("", "runtimeElements")
    }

    def "filters on configuration name match"() {
        when:
        def filter = new ResolvedConfigurationFilter(null, ".*[cC]lasspath")

        then:
        filter.include("", "compileClasspath")
        filter.include("", "runtimeClasspath")
        filter.include("", "testCompileClasspath")
        filter.include("", "classpath")

        !filter.include("", "runtimeElements")
        !filter.include("", "compileClasspathOnly")
    }

    def "filters on exact project path"() {
        when:
        def filter = new ResolvedConfigurationFilter(":parent-proj:proj", null)

        then:
        filter.include(":parent-proj:proj", "")

        !filter.include(":parent-proj", "")
        !filter.include(":parent-proj:", "")
        !filter.include(":proj", "")
        !filter.include(":", "")
    }

    def "filters on exact project paths"() {
        when:
        def filter = new ResolvedConfigurationFilter(":proj-a|:proj-b", null)

        then:
        filter.include(":proj-a", "")
        filter.include(":proj-b", "")

        !filter.include(":parent-proj:proj-a", "")
        !filter.include(":proj", "")
        !filter.include(":", "")
    }

    def "filters on project path match"() {
        when:
        def filter = new ResolvedConfigurationFilter(/(:[\w-]+)*:proj-a(:[\w-]+)*/, null)

        then:
        filter.include(":proj-a", "")
        filter.include(":proj-a:proj-b", "")
        filter.include(":proj-a:proj-b:proj-c", "")
        filter.include(":parent-proj:proj-a", "")
        filter.include(":parent-proj:proj-a:proj-b", "")

        !filter.include(":proj-another", "")
        !filter.include(":proj-a:", "")
        !filter.include(":proj-a:proj-b:", "")
        !filter.include("parent-proj:proj-a", "")
    }
}
