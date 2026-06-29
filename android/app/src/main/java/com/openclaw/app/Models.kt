package com.openclaw.app

data class ModelInfo(val id: String, val label: String)

data class AuditEntry(
    val ts: Double,
    val persona: String,
    val command: String,
    val exitCode: Int,
)

data class Persona(
    val id: String,
    val name: String,
    val emoji: String = "",
    val defaultModel: String? = null,
    val themeColor: String? = null,
    val allowedTools: List<String> = emptyList(),
    val systemPrompt: String = "",
    val allTools: Boolean = false,
)
