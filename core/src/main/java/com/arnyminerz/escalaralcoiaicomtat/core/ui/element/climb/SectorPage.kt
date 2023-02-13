package com.arnyminerz.escalaralcoiaicomtat.core.ui.element.climb

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Chip
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.arnyminerz.escalaralcoiaicomtat.core.R
import com.arnyminerz.escalaralcoiaicomtat.core.annotations.textResource
import com.arnyminerz.escalaralcoiaicomtat.core.annotations.vector
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.path.Path
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.sector.Sector
import com.arnyminerz.escalaralcoiaicomtat.core.shared.IMAGE_MAX_ZOOM_LEVEL
import com.arnyminerz.escalaralcoiaicomtat.core.shared.REST_API_DOWNLOAD_ENDPOINT
import com.arnyminerz.escalaralcoiaicomtat.core.ui.element.Material3ChipColors
import com.arnyminerz.escalaralcoiaicomtat.core.ui.element.ZoomableImage
import com.arnyminerz.escalaralcoiaicomtat.core.ui.viewmodel.SectorPageViewModel
import com.arnyminerz.escalaralcoiaicomtat.core.utils.launch
import com.arnyminerz.escalaralcoiaicomtat.core.utils.mapsIntent
import com.arnyminerz.escalaralcoiaicomtat.core.utils.share
import me.bytebeats.views.charts.bar.BarChart
import me.bytebeats.views.charts.bar.render.bar.SimpleBarDrawer
import me.bytebeats.views.charts.bar.render.label.SimpleLabelDrawer
import me.bytebeats.views.charts.bar.render.xaxis.SimpleXAxisDrawer
import me.bytebeats.views.charts.bar.render.yaxis.SimpleYAxisDrawer
import me.bytebeats.views.charts.simpleChartAnimation
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// TODO: Move this somewhere else
val Float.Companion.DegreeConverter
    get() = TwoWayConverter<Float, AnimationVector2D>({
        val rad = (it * Math.PI / 180f).toFloat()
        AnimationVector2D(sin(rad), cos(rad))
    }, {
        ((atan2(it.v1, it.v2) * 180f / Math.PI).toFloat() + 360) % 360
    })

@Composable
@ExperimentalMaterialApi
@ExperimentalMaterial3Api
fun SectorPage(
    viewModel: SectorPageViewModel,
    sector: Sector,
    dataClassIntent: (sector: Sector) -> Intent,
    informationIntent: (path: Path) -> Intent,
    maximized: MutableState<Boolean>,
    scrollEnabled: (enabled: Boolean) -> Unit,
) {
    val context = LocalContext.current
    viewModel.loadPaths(sector)

    var showLaunchDialog by remember { mutableStateOf(false) }

    if (showLaunchDialog)
        LaunchDialog(sector, dataClassIntent) { showLaunchDialog = false }

    Column(modifier = Modifier.fillMaxSize()) {
        var imageMaximized by remember { maximized }
        val heightFraction by animateFloatAsState(targetValue = if (imageMaximized) 1f else .7f)

        val imageFile = sector.imageFile(context)

        // Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(heightFraction)
        ) {
            ZoomableImage(
                minScale = 1f,
                maxScale = IMAGE_MAX_ZOOM_LEVEL.toFloat(),
                imageModel = imageFile?.takeIf { it.exists() }
                    ?: "$REST_API_DOWNLOAD_ENDPOINT${sector.imagePath}",
                contentDescription = stringResource(R.string.image_desc_sector_image),
                modifier = Modifier
                    .fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { imageMaximized = !imageMaximized },
                ) {
                    Icon(
                        if (imageMaximized) Icons.Rounded.FlipToBack else Icons.Rounded.FlipToFront,
                        contentDescription = stringResource(R.string.action_maximize_image)
                    )
                }
                SmallFloatingActionButton(
                    onClick = { showLaunchDialog = true },
                ) {
                    Icon(
                        Icons.Rounded.Launch,
                        contentDescription = stringResource(R.string.fab_desc_launch)
                    )
                }
            }
        }

        val listState = rememberLazyListState()

        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }
                .collect { scrollEnabled(!it) }
        }

        if (!imageMaximized)
            LazyColumn(
                state = listState,
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Info Card
                        Card(
                            modifier = Modifier
                                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Chips
                                    Chip(
                                        onClick = { /*TODO*/ },
                                        leadingIcon = {
                                            Icon(
                                                sector.sunTime.vector,
                                                contentDescription = stringResource(R.string.sector_sun_time)
                                            )
                                        },
                                        colors = Material3ChipColors,
                                        modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                                    ) {
                                        Text(text = stringResource(sector.sunTime.textResource))
                                    }
                                    if (sector.kidsApt)
                                        Chip(
                                            onClick = { /*TODO*/ },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Rounded.ChildCare,
                                                    contentDescription = stringResource(R.string.sector_kids_apt)
                                                )
                                            },
                                            colors = Material3ChipColors,
                                            modifier = Modifier.padding(start = 4.dp),
                                        ) {
                                            Text(text = stringResource(R.string.sector_kids_apt))
                                        }
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .padding(4.dp)
                                        .clickable(enabled = sector.location != null) {
                                            sector.location
                                                ?.mapsIntent(true, sector.displayName)
                                                ?.let {
                                                    context.launch(it)
                                                }
                                        }
                                ) {
                                    Icon(
                                        Icons.Rounded.DirectionsWalk,
                                        contentDescription = stringResource(R.string.image_desc_walking_time),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        stringResource(
                                            R.string.sector_walking_time,
                                            sector.walkingTime.toString()
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Stats card
                        Card(
                            modifier = Modifier
                                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                var chartVisible by remember { mutableStateOf(false) }
                                val chartButtonIconRotation by animateValueAsState(
                                    targetValue = if (chartVisible) -180f else -90f,
                                    typeConverter = Float.DegreeConverter,
                                )

                                // Load the chart data
                                viewModel.loadBarChartData(sector)

                                // Heading
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .height(40.dp)
                                        .fillMaxWidth(),
                                ) {
                                    Text(
                                        stringResource(R.string.sector_info_chart_title),
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .weight(1f),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    IconButton(
                                        onClick = { chartVisible = !chartVisible }
                                    ) {
                                        Icon(
                                            Icons.Rounded.ChevronLeft,
                                            contentDescription = stringResource(R.string.sector_info_chart_button_desc),
                                            modifier = Modifier.rotate(chartButtonIconRotation),
                                        )
                                    }
                                }
                                // Chart
                                AnimatedVisibility(visible = chartVisible) {
                                    // Text(text = "Hello, this doesn't work, but hey, here's a pig 🐷")
                                    BarChart(
                                        barChartData = viewModel.barChartData,
                                        modifier = Modifier
                                            .height(120.dp)
                                            .fillMaxWidth()
                                            .padding(bottom = 24.dp, start = 4.dp, end = 4.dp),
                                        animation = simpleChartAnimation(),
                                        barDrawer = SimpleBarDrawer(),
                                        xAxisDrawer = SimpleXAxisDrawer(),
                                        yAxisDrawer = SimpleYAxisDrawer(
                                            axisLineThickness = 0.dp,
                                            axisLineColor = MaterialTheme.colorScheme.surfaceVariant,
                                            drawLabelEvery = 10,
                                            // labelTextSize = 0.sp,
                                            labelValueFormatter = {
                                                it.toInt().toString()
                                            } // Disables values for the y axis
                                        ),
                                        labelDrawer = SimpleLabelDrawer(
                                            drawLocation = SimpleLabelDrawer.DrawLocation.XAxis,
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                items(viewModel.paths) { item ->
                    PathItem(
                        item,
                        informationIntent,
                        viewModel.blockStatusList.find { it.pathId == item.objectId }
                    )
                }
            }
    }
}

@Composable
@ExperimentalMaterialApi
fun LaunchDialog(
    sector: Sector,
    dataClassIntent: (sector: Sector) -> Intent,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(R.string.dialog_share_title))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                val sectorLaunchLabel = stringResource(R.string.action_open_sector)

                if (sector.webUrl != null)
                    ListItem(
                        icon = {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = stringResource(R.string.action_share)
                            )
                        },
                        text = {
                            Text(text = stringResource(R.string.action_share))
                        },
                        modifier = Modifier
                            .clickable { context.share(sector.webUrl) },
                    )
                ListItem(
                    icon = {
                        Icon(
                            Icons.Rounded.AddToHomeScreen,
                            contentDescription = stringResource(R.string.action_add_to_homescreen)
                        )
                    },
                    text = {
                        Text(text = stringResource(R.string.action_add_to_homescreen))
                    },
                    modifier = Modifier
                        .clickable {
                            val shortcut =
                                ShortcutInfoCompat.Builder(context, UUID.randomUUID().toString())
                                    .setShortLabel(sector.displayName)
                                    .setLongLabel(sector.displayName)
                                    .setIcon(
                                        IconCompat.createWithBitmap(
                                            context.packageManager
                                                .getApplicationIcon(context.applicationInfo)
                                                .toBitmap()
                                        )
                                    )
                                    .setIntent(
                                        dataClassIntent(sector)
                                    )
                                    .build()
                            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
                        },
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.action_close))
            }
        },
    )
}
