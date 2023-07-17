package org.gradle.github.dependencygraph.internal.util

import org.gradle.api.internal.GradleInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.operations.BuildOperationListenerManager

internal abstract class GradleExtensions {

    inline val Gradle.objectFactory: ObjectFactory
        get() = service()

    inline val Gradle.providerFactory: ProviderFactory
        get() = service()

    inline val Gradle.buildOperationListenerManager: BuildOperationListenerManager
        get() = service()
}

internal inline fun <reified T> Gradle.service(): T =
    (this as GradleInternal).services.get(T::class.java)
