package com.arnyminerz.escalaralcoiaicomtat.core.ui.element.climb

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arnyminerz.escalaralcoiaicomtat.core.R
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.area.Area
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.dataclass.DataClass
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.dataclass.DataClassImpl
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.sector.Sector
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.zone.Zone
import com.arnyminerz.escalaralcoiaicomtat.core.ui.PoppinsFamily
import com.arnyminerz.escalaralcoiaicomtat.core.ui.viewmodel.DataClassItemViewModel
import com.arnyminerz.escalaralcoiaicomtat.core.utils.launch
import com.arnyminerz.escalaralcoiaicomtat.core.utils.mapsIntent
import com.arnyminerz.escalaralcoiaicomtat.core.view.ImageLoadParameters
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.fade
import com.google.accompanist.placeholder.placeholder

@Composable
@ExperimentalMaterial3Api
fun DataClassItem(
    item: DataClassImpl,
    onClick: () -> Unit
) {
    if (item is DataClass<*, *, *>) {
        val context = LocalContext.current
        val viewModel: DataClassItemViewModel = viewModel(
            factory = DataClassItemViewModel.Factory(
                context.applicationContext as Application
            )
        )

        if (item.displayOptions.vertical)
            VerticalDataClassItem(
                item,
                viewModel,
                onClick,
            )
        else
            HorizontalDataClassItem(
                item,
                onClick = onClick,
            )
    } else
        PathDataClassItem(item)
}

/**
 * Displays a data class object as a path.
 * @author Arnau Mora
 * @since 20220102
 * @param dataClassImpl The data of the path
 */
@Composable
fun PathDataClassItem(dataClassImpl: DataClassImpl) {
    // TODO
    Text(
        text = "Hey! This is the contents of the path called \"${dataClassImpl.displayName}\"",
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Displays a data class object that can be downloaded. The UI is a little more complex.
 * @author Arnau Mora
 * @since 20211229
 * @param item The DataClass to display.
 * @param viewModel The View Model for doing async tasks.
 */
@Composable
@ExperimentalMaterial3Api
private fun VerticalDataClassItem(
    item: DataClass<*, *, *>,
    viewModel: DataClassItemViewModel,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    var loadingImage by remember { mutableStateOf(true) }

    val onClickListener: () -> Unit = {
        if (item !is Sector)
            viewModel.loadChildren(item) { if (it is Sector) it.weight else it.displayName }
        onClick()
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .padding(start = 8.dp, bottom = 4.dp, end = 8.dp, top = 4.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            item.Image(
                Modifier
                    .width(120.dp)
                    .height(160.dp)
                    .clickable(
                        enabled = true,
                        role = Role.Image,
                        onClick = onClickListener
                    )
                    .placeholder(
                        loadingImage,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        highlight = PlaceholderHighlight.fade(
                            MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = .8f),
                        ),
                    ),
                imageLoadParameters = ImageLoadParameters()
                    .withSize(120.dp, 160.dp)
            ) { loadingImage = false }

            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = item.displayName,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = PoppinsFamily,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .padding(start = 4.dp, top = 4.dp)
                                .fillMaxWidth()
                                .clickable(onClick = onClickListener),
                        )
                        Text(
                            text = item.metadata.childrenCount.let {
                                if (item.namespace == Zone.NAMESPACE)
                                    stringResource(R.string.downloads_sectors_title, it)
                                else
                                    stringResource(R.string.downloads_paths_title, it)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .fillMaxWidth()
                                .clickable(onClick = onClickListener),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .padding(end = 4.dp)
                    ) {
                        Button(
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            onClick = onClickListener,
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                stringResource(R.string.action_view),
                                tint = MaterialTheme.colorScheme.onTertiary,
                            )
                        }

                        val location = item.location
                        if (location != null)
                            OutlinedButton(
                                onClick = {
                                    context.launch(location.mapsIntent(markerTitle = item.displayName))
                                },
                            ) {
                                Icon(
                                    Icons.Default.Map,
                                    stringResource(R.string.action_view),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                    }
                }
            }
        }
    }
}

/**
 * Displays a data class object that can't be downloaded. The UI is simpler, just image and name.
 * @author Arnau Mora
 * @since 20211229
 * @param item The DataClass to display.
 * @param onClick What to do when clicked.
 */
@Composable
@ExperimentalMaterial3Api
private fun HorizontalDataClassItem(
    item: DataClass<*, *, *>,
    isPlaceholder: Boolean = false,
    onClick: () -> Unit,
) {
    var loadingImage by remember { mutableStateOf(true) }

    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column {
            item.Image(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clickable(onClick = onClick)
                    .placeholder(
                        visible = loadingImage,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        highlight = PlaceholderHighlight.fade(
                            MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = .8f)
                        ),
                    ),
                isPlaceholder = isPlaceholder,
                imageLoadParameters = ImageLoadParameters()
                    .withResultImageScale(.3f)
            ) { loadingImage = isPlaceholder }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = item.displayName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp)
                        .fillMaxWidth()
                        .clickable(onClick = onClick),
                )
                Text(
                    text = stringResource(
                        R.string.downloads_zones_title,
                        item.metadata.childrenCount
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Non-downloadable DataClass Item")
fun NonDownloadableDataClassItemPreview() {
    HorizontalDataClassItem(
        Area.SAMPLE,
        true,
    ) {}
}
