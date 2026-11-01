package com.peaspilaf.copyparty.core

import kotlinx.serialization.json.Json
import java.io.File

/**
 * CI helper: `render-config <state.json> <out.conf>`.
 *
 * CI feeds a state file through the exact renderer the app uses and then boots real copyparty
 * with the result, so the config format is verified end to end instead of by reading code.
 */
fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("usage: render-config <state.json> <out.conf>")
        kotlin.system.exitProcess(2)
    }
    val state = Json { ignoreUnknownKeys = true }.decodeFromString<AppState>(File(args[0]).readText())
    val issues = Validator.validate(state)
    for (issue in issues) {
        System.err.println("${issue.severity} ${issue.path}: ${issue.message}")
    }
    if (issues.any { it.severity == Severity.ERROR }) {
        System.err.println("refusing to render an invalid state")
        kotlin.system.exitProcess(1)
    }
    File(args[1]).writeText(ConfigRenderer.render(state))
}
