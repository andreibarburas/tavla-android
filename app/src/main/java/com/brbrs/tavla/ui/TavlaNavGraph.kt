package com.brbrs.tavla.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.brbrs.tavla.ui.screens.AppLockScreen
import com.brbrs.tavla.ui.screens.BoardScreen
import com.brbrs.tavla.ui.screens.BoardsScreen
import com.brbrs.tavla.ui.screens.CalendarScreen
import com.brbrs.tavla.ui.screens.CardScreen
import com.brbrs.tavla.ui.screens.InsightsScreen
import com.brbrs.tavla.ui.screens.LoginScreen
import com.brbrs.tavla.ui.screens.ModePickerScreen
import com.brbrs.tavla.ui.screens.OnboardingScreen
import com.brbrs.tavla.ui.screens.SettingsScreen
import com.brbrs.tavla.ui.screens.SolidtimeBoardScreen
import com.brbrs.tavla.ui.screens.SolidtimeSettingsScreen

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object ModePicker : Screen("mode_picker")
    object AppLock    : Screen("app_lock")
    object Login      : Screen("login")
    object Boards     : Screen("boards")
    object Calendar   : Screen("calendar")
    object Settings   : Screen("settings")
    object Board      : Screen("board/{boardId}") {
        fun route(boardId: Long) = "board/$boardId"
    }
    object Card       : Screen("card/{boardId}/{cardId}") {
        fun route(boardId: Long, cardId: Long) = "card/$boardId/$cardId"
    }
    object Insights   : Screen("insights/{boardId}") {
        fun route(boardId: Long) = "insights/$boardId"
    }
    object SolidtimeSettings : Screen("solidtime_settings")
    object SolidtimeBoard    : Screen("solidtime_board/{boardId}") {
        fun route(boardId: Long) = "solidtime_board/$boardId"
    }
}

@Composable
fun TavlaNavGraph(
    openBoardId: Long? = null,
    openCardId: Long? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()

    // Timer-notification tap: jump straight to the card it is tracking.
    LaunchedEffect(openBoardId, openCardId) {
        if (openBoardId != null && openCardId != null) {
            navController.navigate(Screen.Card.route(openBoardId, openCardId))
            onDeepLinkConsumed()
        }
    }

    NavHost(navController = navController, startDestination = Screen.Onboarding.route) {

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onContinue = {
                    navController.navigate(Screen.AppLock.route) { popUpTo(Screen.Onboarding.route) { inclusive = true } }
                },
                onGetStarted = {
                    navController.navigate(Screen.ModePicker.route) { popUpTo(Screen.Onboarding.route) { inclusive = true } }
                },
            )
        }

        composable(Screen.ModePicker.route) {
            ModePickerScreen(
                // Keep ModePicker on the back stack here -- tapping Nextcloud just to see what's
                // next (and then backing out to pick Local instead) should actually work. The
                // picker is only cleared once a path is actually committed to (see popUpTo(0)
                // calls below).
                onNextcloud = { navController.navigate(Screen.AppLock.route) },
                onLocal = {
                    navController.navigate(Screen.Boards.route) { popUpTo(0) { inclusive = true } }
                },
            )
        }

        composable(Screen.AppLock.route) {
            AppLockScreen(
                onUnlocked       = { navController.navigate(Screen.Boards.route) { popUpTo(0) { inclusive = true } } },
                onNoLock         = { navController.navigate(Screen.Login.route) { popUpTo(Screen.AppLock.route) { inclusive = true } } },
                onLoggedInNoLock = { navController.navigate(Screen.Boards.route) { popUpTo(0) { inclusive = true } } },
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = { navController.navigate(Screen.Boards.route) { popUpTo(0) { inclusive = true } } }
            )
        }

        composable(Screen.Boards.route) {
            BoardsScreen(
                onBoardClick = { navController.navigate(Screen.Board.route(it)) },
                onCardClick  = { boardId, cardId -> navController.navigate(Screen.Card.route(boardId, cardId)) },
                onSettings   = { navController.navigate(Screen.Settings.route) },
                onCalendar   = { navController.navigate(Screen.Calendar.route) },
            )
        }

        composable(Screen.Calendar.route) {
            CalendarScreen(
                onBack      = { navController.popBackStack() },
                onCardClick = { boardId, cardId -> navController.navigate(Screen.Card.route(boardId, cardId)) },
            )
        }

        composable(
            route = Screen.Board.route,
            arguments = listOf(navArgument("boardId") { type = NavType.LongType }),
        ) { backStack ->
            val boardId = backStack.arguments?.getLong("boardId") ?: return@composable
            BoardScreen(
                onBack      = { navController.popBackStack() },
                onCardClick = { cardId -> navController.navigate(Screen.Card.route(boardId, cardId)) },
                onInsights  = { navController.navigate(Screen.Insights.route(boardId)) },
                onSolidtime = { navController.navigate(Screen.SolidtimeBoard.route(boardId)) },
            )
        }

        composable(
            route = Screen.Insights.route,
            arguments = listOf(navArgument("boardId") { type = NavType.LongType }),
        ) {
            InsightsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Card.route,
            arguments = listOf(
                navArgument("boardId") { type = NavType.LongType },
                navArgument("cardId")  { type = NavType.LongType },
            ),
        ) {
            CardScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack      = { navController.popBackStack() },
                onLoggedOut = { navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } } },
                onSolidtime = { navController.navigate(Screen.SolidtimeSettings.route) },
                onConnectNextcloud = { navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } } },
            )
        }

        composable(Screen.SolidtimeSettings.route) {
            SolidtimeSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.SolidtimeBoard.route,
            arguments = listOf(navArgument("boardId") { type = NavType.LongType }),
        ) {
            SolidtimeBoardScreen(onBack = { navController.popBackStack() })
        }
    }
}
