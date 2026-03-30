package com.example.anonymousmeetup.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.anonymousmeetup.ui.screens.*
import com.example.anonymousmeetup.ui.theme.AnonymousMeetupTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnonymousMeetupTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val bottomItems = listOf(
        BottomNavItem("groups", "Группы", Icons.Default.People),
        BottomNavItem("map", "Карта", Icons.Default.Map),
        BottomNavItem("profile", "Профиль", Icons.Default.Person)
    )

    val bottomRoutes = bottomItems.map { it.route }.toSet()
    val showBottomBar = currentDestination?.route in bottomRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
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
                            icon = {
                                Icon(item.icon, contentDescription = item.label)
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "groups",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("login") {
                LoginScreen(
                    onNavigateToRegister = {
                        navController.navigate("register")
                    },
                    onLoginSuccess = {
                        navController.navigate("groups") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }
            composable("register") {
                RegisterScreen(
                    onNavigateToLogin = {
                        navController.navigate("login") {
                            popUpTo("register") { inclusive = true }
                        }
                    },
                    onRegisterSuccess = {
                        navController.navigate("groups") {
                            popUpTo("register") { inclusive = true }
                        }
                    }
                )
            }
            composable("groups") {
                GroupsScreen(
                    onGroupClick = { groupId ->
                        navController.navigate("group/$groupId")
                    },
                    onAddGroupClick = {
                        navController.navigate("create_group")
                    },
                    onSearchGroupsClick = {
                        navController.navigate("groups_search")
                    }
                )
            }
            composable("group/{groupId}") { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
                GroupDetailScreen(
                    groupId = groupId,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenChat = { id -> navController.navigate("chat/$id") }
                )
            }
            composable("groups_search") {
                SearchGroupsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onGroupJoined = {
                        navController.navigate("groups") {
                            popUpTo("groups_search") { inclusive = true }
                        }
                    },
                    onOpenGroup = { groupId -> navController.navigate("group/$groupId") }
                )
            }
            composable("chat/{groupId}") { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
                ChatScreen(
                    groupId = groupId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMap = { navController.navigate("map") },
                    onOpenPrivateChat = { sessionId ->
                        navController.navigate("private_chat/$sessionId")
                    }
                )
            }
            composable("private_chat/{sessionId}") { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                PrivateChatScreen(
                    sessionId = sessionId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("friends") {
                FriendsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("encounters") {
                EncountersScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("map") {
                MapScreen(
                    groupId = null,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenPrivateChat = { sessionId -> navController.navigate("private_chat/$sessionId") }
                )
            }
            composable("map/{groupId}") { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId")
                MapScreen(
                    groupId = groupId,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenPrivateChat = { sessionId -> navController.navigate("private_chat/$sessionId") }
                )
            }
            composable("profile") {
                ProfileScreen(
                    onFriendsClick = { navController.navigate("friends") },
                    onEncountersClick = { navController.navigate("encounters") },
                    onGroupsClick = { navController.navigate("groups") },
                    onLogout = {
                        navController.navigate("groups") {
                            popUpTo("groups") { inclusive = true }
                        }
                    }
                )
            }
            composable("create_group") {
                CreateGroupScreen(
                    onGroupCreated = {
                        navController.navigate("groups") {
                            popUpTo("groups") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)



