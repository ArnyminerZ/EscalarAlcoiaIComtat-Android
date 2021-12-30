package com.arnyminerz.escalaralcoiaicomtat.core.ui.element

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.arnyminerz.escalaralcoiaicomtat.core.R
import com.google.android.material.chip.Chip

@Composable
private fun rememberChipWithLifecycle(
    text: String,
    onClick: (() -> Unit)? = null
): Chip {
    val context = LocalContext.current
    val chip = remember {
        Chip(context).apply {
            id = R.id.chip
            this.text = text
            this.setOnClickListener { onClick?.let { it() } }
        }
    }
    return chip
}

/**
 * Creates a new Chip view.
 * @author Arnau Mora (ArnyminerZ)
 * @since 20211230
 * @param text The text of the chip.
 * @param modifier Modifiers to apply to the view.
 * @param onClick A callback for when the chip is clicked.
 */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val chipState = rememberChipWithLifecycle(text, onClick)
    AndroidView({ chipState }, modifier) { }
}