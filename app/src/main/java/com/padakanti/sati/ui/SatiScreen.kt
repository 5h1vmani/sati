package com.padakanti.sati.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.padakanti.sati.R

/** The one screen: a tab for the hourly bell, one for the sadhana timer and one for the practice log. */
@Composable
fun SatiScreen(ui: SatiUi, now: Long, tab: Tab, onTab: (Tab) -> Unit, actions: SatiActions) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(ui.message) {
        val message = ui.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        actions.messageShown()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = tab == it,
                        onClick = { onTab(it) },
                        icon = { Icon(painterResource(it.icon), null, Modifier.size(22.dp)) },
                        label = { Text(if (it.titleRes != 0) stringResource(it.titleRes) else it.title) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                when {
                    tab == Tab.Bell -> stringResource(R.string.app_name)
                    tab.titleRes != 0 -> stringResource(tab.titleRes)
                    else -> tab.title
                },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
            )
            when (tab) {
                Tab.Bell -> BellTab(ui, now, actions)
                Tab.Sadhana -> SadhanaTab(ui, actions)
                Tab.Log -> LogTab(ui, now, actions, snackbar, scope)
            }
        }
    }
}
