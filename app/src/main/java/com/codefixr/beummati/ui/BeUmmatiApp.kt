package com.codefixr.beummati.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.codefixr.beummati.data.Destination
import com.codefixr.beummati.data.OfflinePacks
import com.codefixr.beummati.data.QuranReadMode
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.ui.calendar.IslamicCalendarScreen
import com.codefixr.beummati.ui.dhikr.DhikrScreen
import com.codefixr.beummati.ui.hifz.HifzScreen
import com.codefixr.beummati.ui.hadith.HadithBooksScreen
import com.codefixr.beummati.ui.hadith.HadithChapterScreen
import com.codefixr.beummati.ui.hadith.HadithChaptersScreen
import com.codefixr.beummati.ui.home.HomeScreen
import com.codefixr.beummati.ui.plans.ReadingPlanScreen
import com.codefixr.beummati.ui.library.ChapterReaderScreen
import com.codefixr.beummati.ui.library.DuaCategoryScreen
import com.codefixr.beummati.ui.library.DuasScreen
import com.codefixr.beummati.ui.library.LibraryScreen
import com.codefixr.beummati.ui.library.SahabaScreen
import com.codefixr.beummati.ui.library.SeriesScreen
import com.codefixr.beummati.ui.offline.OfflineDataScreen
import com.codefixr.beummati.ui.offline.OfflineSetupScreen
import com.codefixr.beummati.ui.player.LecturePlayerScreen
import com.codefixr.beummati.ui.player.MiniPlayerBar
import com.codefixr.beummati.ui.qibla.QiblaScreen
import com.codefixr.beummati.ui.quran.ParahScreen
import com.codefixr.beummati.ui.quran.QuranListScreen
import com.codefixr.beummati.ui.quran.SurahScreen
import com.codefixr.beummati.ui.salah.SalahTrackerScreen
import com.codefixr.beummati.ui.saved.SavedScreen
import com.codefixr.beummati.ui.scholars.ScholarsScreen
import com.codefixr.beummati.ui.search.SearchScreen
import com.codefixr.beummati.ui.settings.PrivacyScreen
import com.codefixr.beummati.ui.settings.ReadingSettingsScreen
import com.codefixr.beummati.ui.settings.SettingsScreen

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Today", Icons.Outlined.WbSunny),
    QURAN("quran", "Qur’an", Icons.AutoMirrored.Outlined.MenuBook),
    HADITH("hadith", "Hadith", Icons.Outlined.AutoStories),
    LIBRARY("library", "Library", Icons.Outlined.LocalLibrary),
    SCHOLARS("scholars", "Scholars", Icons.Outlined.Groups),
    SAVED("saved", "Saved", Icons.Outlined.BookmarkBorder)
}

object Routes {
    const val PLAYER = "player"
    const val QIBLA = "qibla"
    const val SALAH = "salah"
    const val CALENDAR = "calendar"
    const val DHIKR = "dhikr"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val READING_SETTINGS = "settings/reading"
    const val OFFLINE_DATA = "settings/offline"
    const val OFFLINE_SETUP = "offline-setup"
    const val HIFZ = "hifz"
    const val PLANS = "plans"
    const val PRIVACY = "settings/privacy"
    fun surah(n: Int, ayah: Int? = null) =
        if (ayah != null && ayah > 0) "quran/$n?ayah=$ayah" else "quran/$n"
    fun parah(n: Int) = "quran/parah/$n"
    fun hadithBook(slug: String) = "hadith/$slug"
    fun hadithChapter(slug: String, index: Int, number: Int? = null) =
        if (number != null && number > 0) "hadith/$slug/$index?n=$number" else "hadith/$slug/$index"
    fun series(id: String) = "library/series/$id"
    fun chapter(seriesId: String, chapterId: String) = "library/series/$seriesId/$chapterId"
    const val SAHABA = "library/sahaba"
    const val DUAS = "library/duas"
    fun duaCategory(id: Int) = "library/duas/$id"
}

/** Maps a content [Destination] produced in `data` onto a navigation route. */
fun routeFor(destination: Destination): String = when (destination) {
    is Destination.Surah -> Routes.surah(destination.number, destination.ayah)
    is Destination.HadithBook -> Routes.hadithBook(destination.slug)
    is Destination.HadithChapter -> Routes.hadithChapter(destination.slug, destination.index, destination.number)
    is Destination.Series -> Routes.series(destination.id)
    is Destination.LectureChapter -> Routes.chapter(destination.seriesId, destination.chapterId)
    is Destination.DuaCategory -> Routes.duaCategory(destination.id)
    Destination.Duas -> Routes.DUAS
    Destination.Sahaba -> Routes.SAHABA
    Destination.Scholars -> Tab.SCHOLARS.route
}

private fun tabFor(route: String?): Tab? =
    route?.let { r -> Tab.entries.firstOrNull { r == it.route || r.startsWith(it.route + "/") } }

private fun NavHostController.switchTab(tab: Tab, currentTab: Tab?) {
    if (tab == currentTab && popBackStack(tab.route, inclusive = false)) return
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun BeUmmatiApp(
    pendingRoute: String? = null,
    onPendingRouteConsumed: () -> Unit = {}
) {
    val setupDone by OfflinePacks.setupDone.collectAsState()
    var showSetup by remember { mutableStateOf(!OfflinePacks.setupDone.value) }
    if (!setupDone && showSetup) {
        OfflineSetupScreen(onFinished = { showSetup = false })
        return
    }

    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val onPlayer = route == Routes.PLAYER
    val immersiveReading by ImmersiveReading.active.collectAsState()
    val hideBottomChrome = onPlayer || immersiveReading
    val currentTab = tabFor(route)
    val navigate: (String) -> Unit = { nav.navigate(it) }
    val back: () -> Unit = { nav.popBackStack() }

    LaunchedEffect(pendingRoute) {
        val target = pendingRoute ?: return@LaunchedEffect
        runCatching {
            nav.navigate(target) {
                launchSingleTop = true
            }
        }
        onPendingRouteConsumed()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!hideBottomChrome) {
                Column {
                    MiniPlayerBar(onOpen = { nav.navigate(Routes.PLAYER) { launchSingleTop = true } })
                    NavigationBar {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = tab == currentTab,
                                onClick = { nav.switchTab(tab, currentTab) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Clip) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.HOME.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Tab.HOME.route) {
                HomeScreen(navigate = navigate, onSwitchTab = { nav.switchTab(it, currentTab) })
            }

            composable(Tab.QURAN.route) {
                QuranListScreen(
                    onOpenSurah = {
                        // Surah Qirat is Arabic-first — open mushaf for that surah.
                        SettingsStore.setQuranReadMode(QuranReadMode.MUSHAF)
                        navigate(Routes.surah(it))
                    },
                    onOpenParah = { navigate(Routes.parah(it)) },
                    onOpenSettings = { navigate(Routes.SETTINGS) }
                )
            }
            composable("quran/parah/{n}", arguments = listOf(navArgument("n") { type = NavType.IntType })) {
                ParahScreen(
                    number = it.arguments?.getInt("n") ?: 1,
                    onBack = back,
                    onOpenSettings = { navigate(Routes.READING_SETTINGS) }
                )
            }
            composable(
                "quran/{n}?ayah={ayah}",
                arguments = listOf(
                    navArgument("n") { type = NavType.IntType },
                    navArgument("ayah") { type = NavType.IntType; defaultValue = -1 }
                )
            ) {
                SurahScreen(
                    number = it.arguments?.getInt("n") ?: 1,
                    initialAyah = it.arguments?.getInt("ayah")?.takeIf { a -> a > 0 },
                    onBack = back,
                    onOpenSettings = { navigate(Routes.READING_SETTINGS) },
                    navigate = navigate
                )
            }

            composable(Tab.HADITH.route) {
                HadithBooksScreen(onOpenBook = { navigate(Routes.hadithBook(it)) })
            }
            composable("hadith/{slug}", arguments = listOf(navArgument("slug") { type = NavType.StringType })) {
                val slug = it.arguments?.getString("slug").orEmpty()
                HadithChaptersScreen(
                    slug = slug,
                    onBack = back,
                    onOpenChapter = { idx, number -> navigate(Routes.hadithChapter(slug, idx, number)) }
                )
            }
            composable(
                "hadith/{slug}/{index}?n={n}",
                arguments = listOf(
                    navArgument("slug") { type = NavType.StringType },
                    navArgument("index") { type = NavType.IntType },
                    navArgument("n") { type = NavType.IntType; defaultValue = -1 }
                )
            ) {
                HadithChapterScreen(
                    slug = it.arguments?.getString("slug").orEmpty(),
                    index = it.arguments?.getInt("index") ?: 1,
                    initialHadith = it.arguments?.getInt("n")?.takeIf { n -> n > 0 },
                    onBack = back,
                    onOpenHadith = { idx, number ->
                        navigate(Routes.hadithChapter(it.arguments?.getString("slug").orEmpty(), idx, number))
                    }
                )
            }

            composable(Tab.LIBRARY.route) {
                LibraryScreen(navigate = navigate, onOpenQuran = { nav.switchTab(Tab.QURAN, currentTab) })
            }
            composable(Routes.SAHABA) { SahabaScreen(onBack = back) }
            composable(Routes.DUAS) { DuasScreen(onBack = back, navigate = navigate) }
            composable("library/duas/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) {
                DuaCategoryScreen(id = it.arguments?.getInt("id") ?: 1, onBack = back)
            }
            composable("library/series/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                SeriesScreen(seriesId = it.arguments?.getString("id").orEmpty(), onBack = back, navigate = navigate)
            }
            composable(
                "library/series/{id}/{chapter}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("chapter") { type = NavType.StringType }
                )
            ) {
                ChapterReaderScreen(
                    seriesId = it.arguments?.getString("id").orEmpty(),
                    chapterId = it.arguments?.getString("chapter").orEmpty(),
                    onBack = back,
                    navigate = navigate
                )
            }

            composable(Tab.SCHOLARS.route) { ScholarsScreen() }

            composable(Tab.SAVED.route) { SavedScreen(navigate = navigate) }

            composable(Routes.QIBLA) { QiblaScreen(onBack = back) }
            composable(Routes.SALAH) { SalahTrackerScreen(onBack = back) }
            composable(Routes.CALENDAR) { IslamicCalendarScreen(onBack = back) }
            composable(Routes.DHIKR) { DhikrScreen(onBack = back) }
            composable(Routes.HIFZ) { HifzScreen(onBack = back, navigate = navigate) }
            composable(Routes.PLANS) { ReadingPlanScreen(onBack = back, navigate = navigate) }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = back,
                    onOpen = { destination ->
                        val target = routeFor(destination)
                        if (target == Tab.SCHOLARS.route) nav.switchTab(Tab.SCHOLARS, currentTab) else navigate(target)
                    }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = back,
                    onOpenReading = { navigate(Routes.READING_SETTINGS) },
                    onOpenOffline = { navigate(Routes.OFFLINE_DATA) },
                    onOpenPrivacy = { navigate(Routes.PRIVACY) },
                    onOpenCalendar = { navigate(Routes.CALENDAR) },
                    onOpenDhikr = { navigate(Routes.DHIKR) }
                )
            }
            composable(Routes.READING_SETTINGS) { ReadingSettingsScreen(onBack = back) }
            composable(Routes.OFFLINE_DATA) { OfflineDataScreen(onBack = back) }
            composable(Routes.PRIVACY) { PrivacyScreen(onBack = back) }

            composable(Routes.PLAYER) { LecturePlayerScreen(onBack = back) }
        }
    }
}
