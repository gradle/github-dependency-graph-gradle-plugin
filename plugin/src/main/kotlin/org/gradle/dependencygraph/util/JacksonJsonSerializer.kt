package org.gradle.dependencygraph.util

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.json.JsonMapper

object JacksonJsonSerializer {
    private val mapper = JsonMapper.builder()
        .serializationInclusion(JsonInclude.Include.NON_NULL)
        .build()

    fun serializeToJson(dependencyGraph: Any): String {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(dependencyGraph)
    }
}
