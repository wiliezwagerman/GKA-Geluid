package nl.gkandijk.geluid

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import nl.gkandijk.geluid.ttp.ConnConfig
import nl.gkandijk.geluid.ttp.Protocol
import nl.gkandijk.geluid.ttp.TesiraClient

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("instellingen", Context.MODE_PRIVATE)

    val layout: AppLayout = app.assets.open("layout.json").bufferedReader().use {
        LayoutLoader.parse(it.readText())
    }

    val client = TesiraClient(viewModelScope)

    var config by mutableStateOf(loadConfig())
        private set

    var pin by mutableStateOf(prefs.getString("pin", DEFAULT_PIN) ?: DEFAULT_PIN)
        private set

    /** Beheerpagina's en instellingen zijn zichtbaar. */
    var unlocked by mutableStateOf(false)

    /** Korte melding onderin het scherm. */
    var message by mutableStateOf<String?>(null)

    init {
        connect()
    }

    fun connect() {
        client.start(config, LayoutLoader.subscriptions(layout))
    }

    fun save(newConfig: ConnConfig, newPin: String) {
        config = newConfig
        pin = newPin.ifBlank { DEFAULT_PIN }
        prefs.edit()
            .putString("host", newConfig.host)
            .putString("protocol", newConfig.protocol.name)
            .putInt("port", newConfig.port)
            .putString("user", newConfig.user)
            .putString("password", newConfig.password)
            .putString("pin", pin)
            .apply()
        connect()
    }

    fun tryUnlock(entered: String): Boolean {
        unlocked = entered == pin
        return unlocked
    }

    fun recallPreset(p: PresetDef) {
        viewModelScope.launch {
            val ok = client.recallPreset(p.preset)
            message = if (ok) "\"${p.label}\" ingesteld" else "\"${p.label}\" mislukt: geen verbinding?"
        }
    }

    private fun loadConfig(): ConnConfig {
        val protocol = runCatching {
            Protocol.valueOf(prefs.getString("protocol", Protocol.TELNET.name)!!)
        }.getOrDefault(Protocol.TELNET)
        return ConnConfig(
            host = prefs.getString("host", "") ?: "",
            protocol = protocol,
            port = prefs.getInt("port", if (protocol == Protocol.SSH) 22 else 23),
            user = prefs.getString("user", "default") ?: "default",
            password = prefs.getString("password", "") ?: "",
        )
    }

    override fun onCleared() {
        client.stop()
    }

    companion object {
        const val DEFAULT_PIN = "1234"
    }
}
