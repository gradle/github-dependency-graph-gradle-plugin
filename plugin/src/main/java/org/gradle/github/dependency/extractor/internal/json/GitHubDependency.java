package org.gradle.github.dependency.extractor.internal.json;

import com.github.packageurl.PackageURL;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class GitHubDependency {
    private final PackageURL purl;
    private final Relationship relationship;
    private final List<String> dependencies;

    public GitHubDependency(
            PackageURL purl,
            Relationship relationship,
            List<String> dependencies
    ) {
        this.purl = requireNonNull(purl, "purl");
        this.relationship = requireNonNull(relationship, "relationship");
        this.dependencies = requireNonNull(dependencies, "dependencies");
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GitHubDependency that = (GitHubDependency) o;
        return purl.equals(that.purl) && relationship == that.relationship && dependencies.equals(that.dependencies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(purl, relationship, dependencies);
    }

    @Override
    public String toString() {
        return "GitHubDependency{" +
                "purl=" + purl +
                ", relationship=" + relationship +
                ", dependencies=" + dependencies +
                '}';
    }


    public enum Relationship {
        indirect, direct
    }
}
