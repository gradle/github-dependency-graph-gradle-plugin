package org.gradle.github.dependency.extractor.internal

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.github.packageurl.PackageURL
import org.gradle.github.dependency.extractor.internal.json.GitHubDependencyGraph

object JacksonJsonSerializer {
    class PackageUrlSerializer: StdSerializer<PackageURL>(PackageURL::class.java) {
        override fun serialize(value: PackageURL, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeString(value.toString())
        }
    }

    private val mapper = jsonMapper {
        addModule(kotlinModule())
        val simpleModule = SimpleModule()
        simpleModule.addSerializer(PackageUrlSerializer())
        addModule(simpleModule)
    }

    fun serializeToJson(ghDependencyGraph: GitHubDependencyGraph): String {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ghDependencyGraph)
    }
}
