package app.dishlog.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.dishlog.DishlogApp
import app.dishlog.ui.camera.CameraCapture
import app.dishlog.ui.screens.EntryScreen
import app.dishlog.ui.screens.SettingsScreen
import app.dishlog.ui.screens.TodayScreen
import app.dishlog.ui.theme.C
import app.dishlog.ui.theme.DishlogTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as DishlogApp).repo
        val openCameraFirst = savedInstanceState == null && repo.prefs.autoCamera

        setContent {
            DishlogTheme {
                val nav = rememberNavController()
                val slots by repo.slots.collectAsStateWithLifecycle(emptyList())
                LaunchedEffect(Unit) { if (openCameraFirst) nav.navigate("capture") }

                CompositionLocalProvider(LocalSlots provides slots) {
                    Box(Modifier.fillMaxSize().background(C.Ground)) {
                        NavHost(nav, startDestination = "today") {
                            composable("today") {
                                TodayScreen(
                                    repo = repo,
                                    onOpenEntry = { nav.navigate("entry/$it") },
                                    onCapture = { nav.navigate("capture") },
                                    onSettings = { nav.navigate("settings") },
                                )
                            }
                            composable("settings") {
                                Box(Modifier.statusBarsPadding()) { SettingsScreen(repo, onBack = { nav.popBackStack() }) }
                            }
                            composable("entry/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                                EntryScreen(repo, it.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
                            }
                            composable("capture") {
                                val scope = rememberCoroutineScope()
                                Box(Modifier.background(C.Camera).systemBarsPadding()) {
                                    CameraCapture(
                                        newFile = repo::newPhotoFile,
                                        singleShot = false,
                                        tagLabel = currentTagLabel(slots, repo.prefs.outsideNearest),
                                        onClose = { nav.popBackStack() },
                                        onDone = { files ->
                                            scope.launch {
                                                val e = repo.createEntry(files)
                                                nav.popBackStack("capture", inclusive = true)
                                                nav.navigate("entry/${e.id}")
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
