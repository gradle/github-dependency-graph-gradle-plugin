package org.gradle.github.dependency.extractor.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.github.dependency.extractor.internal.json.GitHubDependency
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class DependencyExtractorServiceTest extends Specification {

    void "test transform configuration"() {
        given:
        Project project = ProjectBuilder.builder().build()
        project.repositories.mavenCentral()
        Configuration testConfiguration = project.getConfigurations().create("test")
        project.getDependencies().add(testConfiguration.getName(), "junit:junit:4.12")
        when:
        ResolvedConfiguration resolvedConfiguration = testConfiguration.getResolvedConfiguration()
        def gitHubDependencies = DependencyExtractorService.extractDependenciesFromResolvedComponentResult(
                testConfiguration.incoming.resolutionResult.root,
                GitHubDependency.Relationship.direct
        )
        assert gitHubDependencies.size() == 2
        def gitHubDependency = gitHubDependencies.get(0)
        then:
        gitHubDependency.purl.toString() == "pkg:maven/junit/junit@4.12"
        gitHubDependency.relationship == GitHubDependency.Relationship.direct
        gitHubDependency.dependencies == ["pkg:maven/org.hamcrest/hamcrest-core@1.3"]
    }
}
