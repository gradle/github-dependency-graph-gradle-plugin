package org.gradle.github.dependency.extractor;

import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.artifacts.ResolvableDependencies;

public class ExtractorDependencyResolutionListener implements DependencyResolutionListener {
    @Override
    public void beforeResolve(ResolvableDependencies dependencies) {

    }

    @Override
    public void afterResolve(ResolvableDependencies dependencies) {
        System.out.println("Resolution Result: " + dependencies.getResolutionResult());
        System.out.println("Root: " + dependencies.getResolutionResult().getRoot());
        System.out.println("ID: " + dependencies.getResolutionResult().getRoot().getId());
        System.out.println("Display name: " + dependencies.getResolutionResult().getRoot().getId().getDisplayName());
        System.out.println("Path: " + dependencies.getPath());
    }
}
