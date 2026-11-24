package nl.gkandijk.geluid

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.gkandijk.geluid.ttp.Sub

/** Schermindeling, ingelezen uit assets/layout.json. */
@Serializable
data class AppLayout(
    val title: String = "Geluid",
    val pages: List<Page> = emptyList(),
)

@Serializable
data class Page(
    val title: String,
    /** Alleen zichtbaar na ontgrendelen met de beheer-pincode. */
    val advanced: Boolean = false,
    val presets: List<PresetDef> = emptyList(),
    val faders: List<FaderDef> = emptyList(),
)

@Serializable
data class PresetDef(
    val label: String,
    val preset: Int,
    /** Eerst om bevestiging vragen (bijv. "System mute"). */
    val confirm: Boolean = false,
)

@Serializable
data class FaderDef(
    val label: String,
    val tag: String,
    val ch: Int = 1,
    /** Veilig bereik in dB; wordt verder beperkt tot wat de Tesira toestaat. */
    val min: Float = -60f,
    val max: Float = 6f,
    /** false = alleen een mute-knop, geen schuif. */
    val fader: Boolean = true,
    val mute: Boolean = true,
    /** Ander blok voor de mute (bijv. een apart Mute-blok); standaard hetzelfde als tag. */
    val muteTag: String? = null,
    val muteCh: Int? = null,
) {
    val muteTagOrDefault: String get() = muteTag ?: tag
    val muteChOrDefault: Int get() = muteCh ?: ch
}

object LayoutLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): AppLayout = json.decodeFromString(AppLayout.serializer(), text)

    fun subscriptions(layout: AppLayout): List<Sub> = layout.pages.flatMap { page ->
        page.faders.flatMap { f ->
            buildList {
                if (f.fader) add(Sub(f.tag, "level", f.ch))
                if (f.mute) add(Sub(f.muteTagOrDefault, "mute", f.muteChOrDefault))
            }
        }
    }.distinct()
}
