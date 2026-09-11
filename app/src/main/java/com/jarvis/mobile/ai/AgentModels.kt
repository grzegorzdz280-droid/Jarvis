package com.jarvis.mobile.ai

data class AgentStep(
    val action: String,
    val value: String = "",
    val confirmation: Boolean = false
)

data class AgentPlan(
    val reply: String = "",
    val steps: List<AgentStep> = emptyList(),
    val remember: String = "",
    val done: Boolean = true
)
