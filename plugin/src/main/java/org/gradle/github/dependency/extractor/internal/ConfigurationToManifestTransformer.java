package org.gradle.github.dependency.extractor.internal;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import com.github.packageurl.PackageURLBuilder;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.github.dependency.extractor.internal.json.GitHubDependency;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfigurationToManifestTransformer {

    private final Map<ModuleVersionIdentifier, String> moduleVersionToRepository;

    public ConfigurationToManifestTransformer(Map<ModuleVersionIdentifier, String> moduleVersionToRepository) {
        this.moduleVersionToRepository = moduleVersionToRepository;
    }

    public void transformToGitHubDependency(Configuration configuration) {
        configuration.getResolvedConfiguration().getFirstLevelModuleDependencies().forEach(dependency -> {
            System.out.println(transformToGitHubDependency(dependency, GitHubDependency.Relationship.direct));
        });
    }

    static PackageURL createPurl(ResolvedDependency resolvedDependency) {
        try {
            return PackageURLBuilder.aPackageURL()
                    .withType("maven")
                    .withNamespace(resolvedDependency.getModuleGroup())
                    .withName(resolvedDependency.getModuleName())
                    .withVersion(resolvedDependency.getModuleVersion())
                    .build();
        } catch (MalformedPackageURLException e) {
            throw new RuntimeException("Failed to create PURL for ResolvedDependency " + resolvedDependency, e);
        }
    }

    static GitHubDependency transformToGitHubDependency(ResolvedDependency resolvedDependency, GitHubDependency.Relationship relationship) {
        List<String> dependencies =
                resolvedDependency
                        .getChildren()
                        .stream()
                        .map(child -> createPurl(child).toString())
                        .collect(Collectors.toList());
        return new GitHubDependency(createPurl(resolvedDependency).toString(), relationship, dependencies);
    }
}
