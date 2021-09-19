package com.arnyminerz.escalaralcoiaicomtat.fragment.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.annotation.UiThread
import com.arnyminerz.escalaralcoiaicomtat.R
import com.arnyminerz.escalaralcoiaicomtat.activity.isolated.FeedbackActivity
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.path.Path
import com.arnyminerz.escalaralcoiaicomtat.core.shared.EXTRA_FEEDBACK
import com.arnyminerz.escalaralcoiaicomtat.core.utils.launch
import com.arnyminerz.escalaralcoiaicomtat.core.utils.putExtra
import com.arnyminerz.escalaralcoiaicomtat.core.view.viewListOf
import io.noties.markwon.Markwon

/**
 * The dialog for displaying extra path info to the user.
 * @author Arnau Mora
 * @since 20210919
 */
class DescriptionDialog private constructor(context: Context, path: Path) {
    companion object {
        /**
         * Creates a new [DescriptionDialog] instance based on a [Path].
         * @author Arnau Mora
         * @since 20210919
         * @param context The context that is requesting the dialog creation.
         * @param path The path that contains the data to display.
         * @return A new [DescriptionDialog] instance if [path] has information ([Path.hasInfo]) is
         * true, or null otherwise.
         */
        fun create(context: Context, path: Path): DescriptionDialog? =
            if (path.hasInfo())
                DescriptionDialog(context, path)
            else null
    }

    /**
     * This will get initialized together with [show], and can be used for dismissing the dialog if
     * necessary.
     * @author Arnau Mora
     * @since 20210919
     * @see show
     * @see hide
     */
    private lateinit var dialog: AlertDialog

    /**
     * The builder instance for creating the dialog.
     * @author Arnau Mora
     * @since 20210919
     */
    private val dialogBuilder: AlertDialog.Builder =
        AlertDialog.Builder(context, R.style.ThemeOverlay_App_AlertDialog)

    init {
        val factory = LayoutInflater.from(context)
        val view = factory.inflate(R.layout.dialog_description, null)

        val builderTextView = view.findViewById<TextView>(R.id.builtBy_textView)
        val rebuilderTextView = view.findViewById<TextView>(R.id.rebuiltBy_textView)
        val descriptionTextView = view.findViewById<TextView>(R.id.description_textView)

        val builderTitleTextView = view.findViewById<TextView>(R.id.builtBy_titleTextView)
        val rebuilderTitleTextView = view.findViewById<TextView>(R.id.rebuiltBy_titleTextView)
        val descriptionTitleTextView = view.findViewById<TextView>(R.id.description_titleTextView)

        val sendFeedbackButton = view.findViewById<Button>(R.id.sendFeedback_button)

        val markwon = Markwon.create(context)
        if (path.builtBy?.ifBlank { null } != null)
            markwon.setMarkdown(builderTextView, path.builtBy!!)
        else
            viewListOf(builderTextView, builderTitleTextView).visibility(false)
        if (path.rebuiltBy?.ifBlank { null } != null)
            markwon.setMarkdown(rebuilderTextView, path.rebuiltBy!!)
        else viewListOf(rebuilderTextView, rebuilderTitleTextView).visibility(false)
        if (path.description?.ifBlank { null } != null)
            markwon.setMarkdown(descriptionTextView, path.description!!)
        else viewListOf(descriptionTextView, descriptionTitleTextView).visibility(false)

        sendFeedbackButton.setOnClickListener {
            val reference =
                context.getString(R.string.dialog_description_reference, path.documentPath)
                    .replace("Areas/", "")
                    .replace("Zones/", "")
                    .replace("Sectors/", "")
                    .replace("Paths/", "")
            context.launch(FeedbackActivity::class.java) {
                putExtra(EXTRA_FEEDBACK, "\n---\n$reference")
            }
        }

        dialogBuilder.setView(view)
    }

    /**
     * Shows the dialog to the user.
     * @author Arnau Mora
     * @since 20210919
     */
    @UiThread
    fun show() {
        if (!this::dialog.isInitialized)
            dialog = dialogBuilder.create()
        dialog.show()
    }

    /**
     * Hides the dialog to the user.
     * @author Arnau Mora
     * @since 20210919
     */
    @UiThread
    fun hide() {
        dialog.hide()
    }
}
