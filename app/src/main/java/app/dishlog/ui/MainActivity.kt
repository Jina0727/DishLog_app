package app.dishlog.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.dishlog.DishlogApp
import app.dishlog.ui.camera.CameraCapture
import app.dishlog.ui.screens.CalendarScreen
import app.dishlog.ui.screens.DayScreen
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
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route
                val tabs = listOf("today", "calendar", "settings")

                LaunchedEffect(Unit) { if (openCameraFirst) nav.navigate("capture") }

                Scaffold(
                    containerColor = C.Ground,
                    bottomBar = {
                        if (route in tabs) {
                            NavigationBar(containerColor = Color.White) {
                                listOf(
                                    Triple("today", "오늘", Icons.AutoMirrored.Outlined.List),
                                    Triple("calendar", "달력", Icons.Outlined.CalendarMonth),
                                    Triple("settings", "설정", Icons.Outlined.Settings),
                                ).forEach { (r, label, icon) ->
                                    NavigationBarItem(
                                        selected = route == r,
                                        onClick = { nav.goTab(r) },
                                        icon = { Icon(icon, contentDescription = null) },
                                        label = { Text(label) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = C.Ink,
                                            selectedTextColor = C.Ink,
                                            indicatorColor = Color(0xFFEEEEEA),
                                            unselectedIconColor = C.Muted,
                                            unselectedTextColor = C.Muted,
                                        ),
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        NavHost(nav, startDestination = "today") {
                            composable("today") {
                                TodayScreen(
                                    repo = repo,
                                    onOpenDay = { nav.navigate("day/$it") },
                                    onOpenEntry = { nav.navigate("entry/$it") },
                                    onCapture = { nav.navigate("capture") },
                                )
                            }
                            composable("calendar") {
                                CalendarScreen(
                                    repo = repo,
                                    onOpenDay = { nav.navigate("day/$it") },
                                    onOpenToday = { nav.goTab("today") },
                                )
                            }
                            composable("settings") { SettingsScreen(repo) }
                            composable("day/{day}", listOf(navArgument("day") { type = NavType.LongType })) {
                                DayScreen(
                                    repo = repo,
                                    epochDay = it.arguments!!.getLong("day"),
                                    onBack = { nav.popBackStack() },
                                    onOpenEntry = { id -> nav.navigate("entry/$id") },
                                )
                            }
                            composable("entry/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                                EntryScreen(repo, it.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
                            }
                            composable("capture") {
                                val scope = rememberCoroutineScope()
                                val slots by repo.slots.collectAsStateWithLifecycle(emptyList())
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

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo("today") { saveState = true }
    launchSingleTop = true
    restoreState = true
}
