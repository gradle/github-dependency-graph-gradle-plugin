package org.gradle.github.dependency.extractor.internal

import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult

/**
 * Defines various strategies for extracting dependency information based upon the version of Gradle.
 */
interface VariantExtractor {
    fun getSelectedVariant(dependency: ResolvedDependencyResult): ResolvedVariantResult?

    fun getDependencies(
        selectedVariant: ResolvedVariantResult?,
        componentResult: ResolvedComponentResult
    ): Collection<DependencyResult>

    /**
     * Fallback strategy.
     */
    object Default : VariantExtractor {

        override fun getSelectedVariant(dependency: ResolvedDependencyResult): ResolvedVariantResult? =
            null

        override fun getDependencies(
            selectedVariant: ResolvedVariantResult?,
            componentResult: ResolvedComponentResult
        ): Collection<DependencyResult> =
            componentResult.dependencies
    }

    /**
     * Strategy for Gradle 5.6 and higher which supports variants.
     */
    @Suppress("ClassName")
    object VariantExtractor_5_6 : VariantExtractor {
        override fun getSelectedVariant(dependency: ResolvedDependencyResult): ResolvedVariantResult? {
            @Suppress("RedundantNullableReturnType") // is actually nullable but missing annotation
            val variants: List<ResolvedVariantResult>? = dependency.selected.variants
            return if (variants == null || variants.isEmpty()) {
                null
            } else {
                dependency.resolvedVariant
            }
        }

        override fun getDependencies(
            selectedVariant: ResolvedVariantResult?,
            componentResult: ResolvedComponentResult
        ): Collection<DependencyResult> {
            return if (selectedVariant != null) {
                componentResult.getDependenciesForVariant(selectedVariant)
            } else {
                componentResult.dependencies
            }
        }
    }
}
