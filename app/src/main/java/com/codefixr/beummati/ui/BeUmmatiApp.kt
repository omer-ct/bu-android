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
import androidx.compose.runtime.getValue
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
import com.codefixr.beummati.ui.hadith.HadithBooksScreen
import com.codefixr.beummati.ui.hadith.HadithChapterScreen
import com.codefixr.beummati.ui.hadith.HadithChaptersScreen
import com.codefixr.beummati.ui.home.HomeScreen
import com.codefixr.beummati.ui.library.ChapterReaderScreen
import com.codefixr.beummati.ui.library.DuaCategoryScreen
import com.codefixr.beummati.ui.library.DuasScreen
import com.codefixr.beummati.ui.library.LibraryScreen
import com.codefixr.beummati.ui.library.SahabaScreen
import com.codefixr.beummati.ui.library.SeriesScreen
import com.codefixr.beummati.ui.player.LecturePlayerScreen
import com.codefixr.beummati.ui.player.MiniPlayerBar
import com.codefixr.beummati.ui.qibla.QiblaScreen
import com.codefixr.beummati.ui.quran.QuranListScreen
import com.codefixr.beummati.ui.quran.SurahScreen
import com.codefixr.beummati.ui.salah.SalahTrackerScreen
import com.codefixr.beummati.ui.saved.SavedScreen
import com.codefixr.beummati.ui.scholars.ScholarsScreen
import com.codefixr.beummati.ui.search.SearchScreen
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
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    fun surah(n: Int) = "quran/$n"
    fun hadithBook(slug: String) = "hadith/$slug"
    fun hadithChapter(slug: String, index: Int) = "hadith/$slug/$index"
    fun series(id: String) = "library/series/$id"
    fun chapter(seriesId: String, chapterId: String) = "library/series/$seriesId/$chapterId"
    const val SAHABA = "library/sahaba"
    const val DUAS = "library/duas"
    fun duaCategory(id: Int) = "library/duas/$id"
}

/** Maps a content [Destination] produced in `data` onto a navigation route. */
fun routeFor(destination: Destination): String = when (destination) {
    is Destination.Surah -> Routes.surah(destination.number)
    is Destination.HadithBook -> Routes.hadithBook(destination.slug)
    is Destination.HadithChapter -> Routes.hadithChapter(destination.slug, destination.index)
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
fun BeUmmatiApp() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val onPlayer = route == Routes.PLAYER
    val currentTab = tabFor(route)
    val navigate: (String) -> Unit = { nav.navigate(it) }
    val back: () -> Unit = { nav.popBackStack() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!onPlayer) {
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
                QuranListScreen(onOpenSurah = { navigate(Routes.surah(it)) })
            }
            composable("quran/{n}", arguments = listOf(navArgument("n") { type = NavType.IntType })) {
                SurahScreen(number = it.arguments?.getInt("n") ?: 1, onBack = back)
            }

            composable(Tab.HADITH.route) {
                HadithBooksScreen(onOpenBook = { navigate(Routes.hadithBook(it)) })
            }
            composable("hadith/{slug}", arguments = listOf(navArgument("slug") { type = NavType.StringType })) {
                val slug = it.arguments?.getString("slug").orEmpty()
                HadithChaptersScreen(slug = slug, onBack = back, onOpenChapter = { idx -> navigate(Routes.hadithChapter(slug, idx)) })
            }
            composable(
                "hadith/{slug}/{index}",
                arguments = listOf(
                    navArgument("slug") { type = NavType.StringType },
                    navArgument("index") { type = NavType.IntType }
                )
            ) {
                HadithChapterScreen(
                    slug = it.arguments?.getString("slug").orEmpty(),
                    index = it.arguments?.getInt("index") ?: 1,
                    onBack = back
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
            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = back,
                    onOpen = { destination ->
                        val target = routeFor(destination)
                        if (target == Tab.SCHOLARS.route) nav.switchTab(Tab.SCHOLARS, currentTab) else navigate(target)
                    }
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = back) }

            composable(Routes.PLAYER) { LecturePlayerScreen(onBack = back) }
        }
    }
}
