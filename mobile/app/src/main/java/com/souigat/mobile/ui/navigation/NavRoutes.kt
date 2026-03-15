package com.souigat.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.souigat.mobile.R

sealed class NavRoute(val route: String) {
    object Login : NavRoute("login")
    object Dashboard : NavRoute("dashboard")
    object Trips : NavRoute("trips")
    object SettlementSummary : NavRoute("settlement_summary")
    object History : NavRoute("history")
    object Expenses : NavRoute("expenses")
    object Profile : NavRoute("profile")
}

data class BottomNavItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val contentDescriptionRes: Int
)

val bottomNavItems = listOf(
    BottomNavItem(NavRoute.Dashboard.route, R.string.nav_home, Icons.Filled.Dashboard, R.string.nav_home),
    BottomNavItem(NavRoute.Trips.route, R.string.nav_trips, Icons.Filled.DirectionsBus, R.string.nav_trips),
    BottomNavItem(NavRoute.History.route, R.string.nav_history, Icons.Filled.History, R.string.nav_history),
    BottomNavItem(NavRoute.Profile.route, R.string.nav_profile, Icons.Filled.Person, R.string.nav_profile),
)
