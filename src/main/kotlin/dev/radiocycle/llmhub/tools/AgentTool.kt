package dev.radiocycle.llmhub.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class ToolSpec(
    val name: String,
    val description: String,
    /** JSON-Schema object describing the arguments. */
    val parameters: JsonObject,
)

data class ToolOutcome(val content: String, val isError: Boolean = false)

interface AgentTool {
    val spec: ToolSpec
    suspend fun execute(args: JsonObject): ToolOutcome
}

/** Small DSL so tool schemas stay readable. */
fun objectSchema(required: List<String> = emptyList(), properties: JsonObjectBuilder.() -> Unit): JsonObject =
    buildJsonObject {
        put("type", "object")
        putJsonObject("properties", properties)
        putJsonArray("required") { required.forEach { add(it) } }
    }

fun JsonObjectBuilder.stringProp(name: String, description: String) = putJsonObject(name) {
    put("type", "string")
    put("description", description)
}

fun JsonObjectBuilder.intProp(name: String, description: String) = putJsonObject(name) {
    put("type", "integer")
    put("description", description)
}

fun JsonObjectBuilder.putBool(name: String, description: String) = putJsonObject(name) {
    put("type", "boolean")
    put("description", description)
}
