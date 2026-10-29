package com.promoapp.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.promoapp.R
import com.promoapp.ui.alerts.AlertsScreen
import com.promoapp.ui.coupons.CouponsScreen
import com.promoapp.ui.detail.DealDetailScreen
import com.promoapp.ui.home.HomeScreen
import com.promoapp.ui.post.PostDealScreen
import com.promoapp.ui.profile.ProfileScreen
import com.promoapp.ui.search.SearchScreen

enum class TopLevelDestination(
    val route: Any,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(HomeRoute, R.string.nav_deals, Icons.Filled.Home),
    SEARCH(SearchRoute, R.string.nav_search, Icons.Filled.Search),
    COUPONS(CouponsRoute, R.string.nav_coupons, Icons.Filled.ShoppingCart),
    ALERTS(AlertsRoute, R.string.nav_alerts, Icons.Filled.Notifications),
    PROFILE(ProfileRoute, R.string.nav_profile, Icons.Filled.Person),
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { currentDestination.isIn(it) }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination.isIn(destination),
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<HomeRoute> {
                HomeScreen(onDealClick = { navController.navigate(DealDetailRoute(it)) })
            }
            composable<SearchRoute> {
                SearchScreen(onDealClick = { navController.navigate(DealDetailRoute(it)) })
            }
            composable<CouponsRoute> { CouponsScreen() }
            composable<AlertsRoute> {
                AlertsScreen(onDealClick = { navController.navigate(DealDetailRoute(it)) })
            }
            composable<ProfileRoute> {
                ProfileScreen(
                    onPostClick = { navController.navigate(PostDealRoute) },
                    onDealClick = { navController.navigate(DealDetailRoute(it)) },
                )
            }
            composable<PostDealRoute> {
                PostDealScreen(
                    onBack = { navController.navigateUp() },
                    onPosted = { dealId ->
                        navController.navigate(DealDetailRoute(dealId)) {
                            popUpTo<PostDealRoute> { inclusive = true }
                        }
                    },
                )
            }
            composable<DealDetailRoute>(
                deepLinks = listOf(navDeepLink<DealDetailRoute>(basePath = "promoapp://deal")),
            ) {
                DealDetailScreen(onBack = { navController.navigateUp() })
            }
        }
    }
}

private fun NavDestination?.isIn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
