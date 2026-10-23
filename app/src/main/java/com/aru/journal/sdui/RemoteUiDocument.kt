package com.aru.journal.sdui

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Declarative data only: no HTML, downloaded code, external URLs, or privileged actions. */
@Serializable
data class RemoteUiDocument(
    val schemaVersion: Int = 1,
    val revision: Int = 0,
    val minVersionCode: Int = 1,
    val accent: String = "purple",
    val features: RemoteUiFeatures = RemoteUiFeatures(),
    val cards: List<RemoteUiCard> = emptyList()
)

@Serializable
data class RemoteUiFeatures(val camera: Boolean = true, val microphone: Boolean = true, val savedMeals: Boolean = true)

@Serializable
data class RemoteUiCard(
    val id: String,
    val type: String,
    val title: String,
    val body: String = "",
    val tone: String = "neutral",
    val actions: List<RemoteUiAction> = emptyList()
)

@Serializable
data class RemoteUiAction(val label: String, val action: String)

object RemoteUiContract {
    const val MAX_BYTES = 64 * 1024
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val types = setOf("text", "actions", "daily_summary")
    private val actions = setOf("add_meal", "goals", "saved_meals", "settings")
    private val tones = setOf("neutral", "accent")
    private val accents = setOf("purple", "blue", "green")

    fun parse(raw: String, appVersionCode: Int): RemoteUiDocument {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "UI document too large" }
        val document = json.decodeFromString<RemoteUiDocument>(raw)
        require(document.schemaVersion == 1 && document.revision >= 0)
        require(document.minVersionCode in 1..appVersionCode)
        require(document.accent in accents)
        require(document.cards.size <= 8)
        require(document.cards.map { it.id }.distinct().size == document.cards.size)
        require(document.cards.all { it.id.matches(Regex("[a-zA-Z0-9_-]{1,48}")) })
        // Future components and actions are skipped independently; existing supported content survives.
        val cards = document.cards.filter { it.type in types }.map { card ->
            require(card.title.isNotBlank() && card.title.length <= 100)
            require(card.body.length <= 500 && card.tone in tones && card.actions.size <= 3)
            card.copy(actions = card.actions.filter { it.action in actions }.onEach {
                require(it.label.isNotBlank() && it.label.length <= 40)
            }.filter { document.features.savedMeals || it.action != "saved_meals" })
        }
        return document.copy(cards = cards)
    }
}
