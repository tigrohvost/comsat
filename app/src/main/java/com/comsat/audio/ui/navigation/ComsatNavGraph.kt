package com.comsat.audio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.comsat.audio.ui.screen.AirportListScreen
import com.comsat.audio.ui.screen.MainScreen
import com.comsat.audio.ui.screen.StationListScreen
import com.comsat.audio.viewmodel.MainViewModel

sealed class Screen(val route: String) {
    data object Main : Screen("main")
    data object Airports : Screen("airports")
    data object Stations : Screen("stations")
}

@Composable
fun ComsatNavGraph(navController: NavHostController) {
    // Single Activity-scoped ViewModel shared by all destinations.
    val viewModel: MainViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = Screen.Main.route) {
        composable(Screen.Main.route) {
            MainScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.Airports.route) {
            AirportListScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.Stations.route) {
            StationListScreen(navController = navController, viewModel = viewModel)
        }
    }
}
