package com.iporadar.app.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iporadar.app.di.ServiceLocator
import com.iporadar.app.ui.IpoViewModel
import com.iporadar.app.ui.allotment.AllotmentScreen
import com.iporadar.app.ui.detail.IpoDetailScreen
import com.iporadar.app.ui.home.HomeScreen
import com.iporadar.app.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val ALLOTMENT = "allotment"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{ipoId}"

    fun detail(ipoId: String) = "detail/$ipoId"
}

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomItems = listOf(
    BottomItem(Routes.HOME, "IPOs", Icons.AutoMirrored.Outlined.ViewList),
    BottomItem(Routes.ALLOTMENT, "Allotment", Icons.AutoMirrored.Outlined.FactCheck),
    BottomItem(Routes.SETTINGS, "Settings", Icons.Outlined.Settings)
)

@Composable
fun IpoRadarNavHost(startIpoId: String? = null) {
    val navController = rememberNavController()
    val vm: IpoViewModel = viewModel(
        factory = IpoViewModel.Factory(
            ServiceLocator.repository,
            ServiceLocator.prefs,
            ServiceLocator.allotmentRepository
        )
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbar by vm.snackbar.collectAsStateWithLifecycle()

    LaunchedEffect(snackbar) {
        snackbar?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeSnackbar()
        }
    }

    // Opened from a notification — jump straight to that IPO.
    LaunchedEffect(startIpoId) {
        if (!startIpoId.isNullOrBlank()) {
            navController.navigate(Routes.detail(startIpoId))
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomItems.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    bottomItems.forEach { item ->
                        val selected = backStackEntry?.destination?.hierarchy
                            ?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        vm = vm,
                        onOpenIpo = { navController.navigate(Routes.detail(it.id)) }
                    )
                }
                composable(Routes.ALLOTMENT) {
                    AllotmentScreen(vm = vm)
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(vm = vm)
                }
                composable(
                    route = Routes.DETAIL,
                    arguments = listOf(navArgument("ipoId") { type = NavType.StringType })
                ) { entry ->
                    IpoDetailScreen(
                        ipoId = entry.arguments?.getString("ipoId").orEmpty(),
                        vm = vm,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

