package com.arnyminerz.escalaralcoiaicomtat.ui.screen.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arnyminerz.escalaralcoiaicomtat.R
import com.arnyminerz.escalaralcoiaicomtat.activity.MainActivity
import com.arnyminerz.escalaralcoiaicomtat.activity.climb.DataClassActivity
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.area.Area
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.path.Path
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.sector.Sector
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.updater.UpdaterSingleton
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.zone.Zone
import com.arnyminerz.escalaralcoiaicomtat.core.shared.app
import com.arnyminerz.escalaralcoiaicomtat.core.ui.element.Chip
import com.arnyminerz.escalaralcoiaicomtat.core.ui.element.climb.DownloadedDataItem
import com.arnyminerz.escalaralcoiaicomtat.core.utils.doAsync
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.placeholder
import com.google.accompanist.placeholder.shimmer
import timber.log.Timber

@Composable
@ExperimentalMaterial3Api
@ExperimentalMaterialApi
fun MainActivity.DownloadsScreen(updateAvailable: Boolean) {
    val context = LocalContext.current
    val downloads by downloadsViewModel.downloads.observeAsState()
    val sizeString by remember { downloadsViewModel.sizeString }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.updates_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                            .weight(1f)
                    )
                    if (updateAvailable)
                        Button(
                            onClick = { /*TODO*/ },
                            colors = ButtonDefaults.textButtonColors()
                        ) {
                            Text(text = stringResource(R.string.action_update_all))
                        }
                }
                if (updateAvailable) {
                    var updateAvailableObjects by remember {
                        mutableStateOf(
                            UpdaterSingleton
                                .getInstance()
                                .updateAvailableObjects
                        )
                    }
                    LazyColumn {
                        updateAvailableObjects.forEach { key, entries ->
                            items(entries) { item ->
                                val namespace = key.substring(0, key.length - 1)
                                val state = when (namespace) {
                                    Area.NAMESPACE -> downloadsViewModel
                                        .getDataClass<Area>(Area.NAMESPACE, item)
                                    Zone.NAMESPACE -> downloadsViewModel
                                        .getDataClass<Zone>(Zone.NAMESPACE, item)
                                    Sector.NAMESPACE -> downloadsViewModel
                                        .getDataClass<Sector>(Sector.NAMESPACE, item)
                                    Path.NAMESPACE -> downloadsViewModel
                                        .getDataClass<Path>(Path.NAMESPACE, item)
                                    else -> {
                                        Timber.w("Attention! Namespace \"%s\" not valid", namespace)
                                        return@items
                                    }
                                }
                                val dataClassPair by remember { state }

                                ListItem(
                                    modifier = Modifier.fillMaxWidth(),
                                    trailing = {
                                        var buttonEnabled by remember { mutableStateOf(true) }
                                        IconButton(
                                            onClick = {
                                                buttonEnabled = false
                                                dataClassPair?.let { (dataClass, score) ->
                                                    doAsync {
                                                        val updaterSingleton =
                                                            UpdaterSingleton.getInstance()
                                                        updaterSingleton
                                                            .update(
                                                                context,
                                                                namespace,
                                                                dataClass.objectId,
                                                                score
                                                            )
                                                        updateAvailableObjects =
                                                            updaterSingleton.updateAvailableObjects
                                                    }
                                                }
                                            },
                                            enabled = dataClassPair != null && buttonEnabled,
                                        ) {
                                            Icon(
                                                Icons.Rounded.Download,
                                                contentDescription = stringResource(R.string.action_download)
                                            )
                                        }
                                    }
                                ) {
                                    Text(
                                        text = dataClassPair?.first?.displayName
                                            ?: stringResource(R.string.status_loading),
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .placeholder(
                                                dataClassPair == null,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                highlight = PlaceholderHighlight.shimmer(
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                        .copy(alpha = .5f)
                                                ),
                                            )
                                    )
                                }
                            }
                        }
                    }
                } else
                    Text(
                        text = stringResource(R.string.updates_no_update_available),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(8.dp)
                    )
            }
        }

        LazyColumn {
            item {
                Card(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.downloads_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier
                                .padding(start = 12.dp, end = 12.dp)
                                .fillMaxWidth()
                        ) {
                            Timber.i("Size: $sizeString")
                            Chip(stringResource(R.string.downloads_size, sizeString))
                        }
                    }
                }
            }
            items(downloads ?: emptyList()) { data ->
                val isParentDownloaded = data.second
                if (!isParentDownloaded)
                    DownloadedDataItem(
                        data.first,
                        app.searchSession,
                        DataClassActivity::class.java
                    ) {
                        // This gets called when data gets deleted
                        downloadsViewModel.loadDownloads()
                    }
            }
        }
    }

    downloadsViewModel.loadDownloads()
}
