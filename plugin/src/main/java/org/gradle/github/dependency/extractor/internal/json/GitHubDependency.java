package org.gradle.github.dependency.extractor.internal.json;

import com.github.packageurl.PackageURL;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class GitHubDependency {
    private final PackageURL purl;
    private final Relationship relationship;
    private final List<String> dependencies;
    private final Map<String, Object> metadata;

    public GitHubDependency(
            PackageURL purl,
            Relationship relationship,
            List<String> dependencies,
            Map<String, Object> metadata
    ) {
        this.purl = requireNonNull(purl, "purl");
        this.relationship = requireNonNull(relationship, "relationship");
        this.dependencies = requireNonNull(dependencies, "dependencies");
        this.metadata = metadata;
    }

    @Nonnull
    public PackageURL getPurl() {
        return purl;
    }

    @Nonnull
    public Relationship getRelationship() {
        return relationship;
    }

    @Nonnull
    public List<String> getDependencies() {
        return dependencies;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GitHubDependency that = (GitHubDependency) o;
        return purl.equals(that.purl) && relationship == that.relationship && dependencies.equals(that.dependencies) && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(purl, relationship, dependencies, metadata);
    }

    @Override
    public String toString() {
        return "GitHubDependency{" +
                "purl=" + purl +
                ", relationship=" + relationship +
                ", dependencies=" + dependencies +
                ", metadata=" + metadata +
                '}';
    }


    public enum Relationship {
        indirect, direct
    }
}
