package nl.gkandijk.geluid

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import nl.gkandijk.geluid.ui.MainScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Scherm blijft aan zolang de app open staat (vaste tablet).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MainScreen(vm)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Bij wegleggen automatisch weer vergrendelen.
        vm.unlocked = false
    }
}
