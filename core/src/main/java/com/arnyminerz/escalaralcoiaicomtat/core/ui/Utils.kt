package com.arnyminerz.escalaralcoiaicomtat.core.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.arnyminerz.escalaralcoiaicomtat.core.R

/**
 * Used by [NavItems] for specifying the data of each item.
 * @author Arnau Mora
 * @since 20211227
 * @param text The text of the item.
 * @param icon The icon of the item.
 * @param contentDescription The content description of the icon of the item.
 */
sealed class Screen(
    val route: String,
    @StringRes val text: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector? = null,
    @StringRes val contentDescription: Int? = null
) {
    object Explore : Screen(
        "explore",
        R.string.item_explore,
        Icons.Outlined.Explore,
        Icons.Rounded.Explore,
        R.string.item_explore
    )

    object Map : Screen(
        "map",
        R.string.item_map,
        Icons.Outlined.Map,
        Icons.Rounded.Map,
        R.string.item_map
    )

    object Downloads : Screen(
        "downloads",
        R.string.item_downloads,
        Icons.Outlined.Download,
        Icons.Rounded.Download,
        R.string.item_downloads
    )

    object Settings : Screen(
        "settings",
        R.string.item_settings,
        Icons.Outlined.Settings,
        Icons.Rounded.Settings,
        R.string.item_settings
    )
}

@Composable
fun RowScope.NavItems(navController: NavController, items: List<Screen>) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    items.forEach { item ->
        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
        NavigationBarItem(
            selected,
            icon = {
                Icon(
                    if (selected) item.selectedIcon ?: item.icon else item.icon,
                    item.contentDescription?.let { stringResource(it) }
                )
            },
            label = { Text(text = stringResource(item.text)) },
            onClick = {
                navController.navigate(item.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )
    }
}
