package org.gradle.dependencygraph.util

import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule

object JacksonJsonSerializer {
    private val mapper = jsonMapper {
        serializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        addModule(kotlinModule())
    }

    fun serializeToJson(dependencyGraph: Any): String {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(dependencyGraph)
    }
}
