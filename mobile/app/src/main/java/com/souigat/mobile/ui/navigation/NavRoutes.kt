package com.souigat.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavRoute(val route: String) {
    object Login : NavRoute("login")
    object Dashboard : NavRoute("dashboard")
    object Trips : NavRoute("trips")
    object History : NavRoute("history")
    object Expenses : NavRoute("expenses")
    object Profile : NavRoute("profile")
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
)

val bottomNavItems = listOf(
    BottomNavItem(NavRoute.Dashboard.route, "Dashboard", Icons.Filled.Dashboard, "Dashboard tab"),
    BottomNavItem(NavRoute.History.route, "History", Icons.Filled.History, "Trip history tab"),
    BottomNavItem(NavRoute.Expenses.route, "Expenses", Icons.Filled.ListAlt, "Expenses tab"),
    BottomNavItem(NavRoute.Profile.route, "Profile", Icons.Filled.Person, "Profile tab"),
)
