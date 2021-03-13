@file:Suppress("unused")

package com.arnyminerz.escalaralcoiaicomtat.notification

import android.app.PendingIntent
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.collection.arrayMapOf
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arnyminerz.escalaralcoiaicomtat.exception.notification.NullChannelIdException
import com.arnyminerz.escalaralcoiaicomtat.generic.ValueMax

private fun generateNotificationId(): Int {
    var greatest = 0
    for (id in builders.keys) {
        if (id > greatest)
            greatest = id
    }
    return greatest + 1
}

private val builders = arrayMapOf<Int, Notification.Builder>()

class Notification private constructor(private val builder: Builder) {
    fun edit(): Builder = builder

    fun show() {
        with(builder) {
            val notificationBuilder = NotificationCompat.Builder(context, channelId!!)
            icon?.let { notificationBuilder.setSmallIcon(it) }
            title?.let { notificationBuilder.setContentTitle(it) }
            text?.let { notificationBuilder.setContentText(it) }
            info?.let { notificationBuilder.setContentInfo(it) }
            longText?.let {
                notificationBuilder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(it)
                        .bigText(it)
                )
            } ?: text?.let {
                notificationBuilder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(it)
                        .bigText(it)
                )
            }
            intent?.let { notificationBuilder.setContentIntent(it) }
            progress?.let { value, max ->
                if (value < 0)
                    notificationBuilder.setProgress(0, 0, true)
                else
                    notificationBuilder.setProgress(value, max, false)
                notificationBuilder.setOngoing(true)
            } ?: run { notificationBuilder.setOngoing(false) }

            for (action in actions)
                notificationBuilder.addAction(
                    action.icon,
                    action.text.toString(),
                    action.clickListener
                )
        }
    }

    /**
     * Hides the notification
     * @author Arnau Mora
     * @since 20210313
     */
    fun hide() {
        NotificationManagerCompat.from(builder.context)
            .cancel(builder.id)
    }

    fun destroy() {

    }

    class Builder(val context: Context) {
        var id = generateNotificationId()
        var channelId: String? = null

        @DrawableRes
        var icon: Int? = null
        var title: String? = null
        var text: String? = null
        var info: String? = null
        var longText: String? = null
        var intent: PendingIntent? = null
        var progress: ValueMax<Int>? = null
        val actions: ArrayList<NotificationButton> = arrayListOf()

        /**
         * Sets an id to the notification
         * @author Arnau Mora
         * @since 20210313
         * @param id The id to set
         * @return The Builder instance
         * @throws IllegalStateException If trying to set an already existing id
         */
        @Throws(IllegalStateException::class)
        fun withId(id: Int): Builder {
            if (builders.containsKey(id))
                throw IllegalStateException("The specified id is already registered")
            this.id = id
            return this
        }

        /**
         * Sets a the channel id of the notification
         * @author Arnau Mora
         * @since 20210313
         * @param channelId The channel id of the notification
         * @return The Builder instance
         */
        fun withChannelId(channelId: String): Builder {
            this.channelId = channelId
            return this
        }

        /**
         * Sets a the icon of the notification
         * @author Arnau Mora
         * @since 20210313
         * @param icon The drawable icon id of the notification
         * @return The Builder instance
         */
        fun withIcon(@DrawableRes icon: Int): Builder {
            this.icon = icon
            return this
        }

        /**
         * Sets the title of the notification
         * @author Arnau Mora
         * @since 20210313
         * @param title The title of the notification
         * @return The Builder instance
         */
        fun withTitle(title: String): Builder {
            this.title = title
            return this
        }

        /**
         * Sets the the message of the notification
         * @author Arnau Mora
         * @since 20210313
         * @param text The message of the notification
         * @return The Builder instance
         */
        fun withText(text: String): Builder {
            this.text = text
            return this
        }

        /**
         * Sets the info message of the notification
         * @author Arnau Mora
         * @since 20210313
         * @param info The info message of the notification
         * @return The Builder instance
         */
        fun withInfoText(info: String): Builder {
            this.info = info
            return this
        }

        /**
         * Sets the text of the notification when it's expanded
         * @author Arnau Mora
         * @since 20210313
         * @param longText The text of the notification when expanded
         * @return The Builder instance
         */
        fun withLongText(longText: String): Builder {
            this.longText = longText
            return this
        }

        /**
         * Sets the action of the notification
         * @author Arnau Mora
         * @since 20210313
         * @param pendingIntent What should be called when the notification is tapped
         * @return The Builder instance
         */
        fun withIntent(pendingIntent: PendingIntent): Builder {
            this.intent = pendingIntent
            return this
        }

        /**
         * Sets the progress of the notification.
         * If progress is set to less than 0, it will be indeterminate
         * @author Arnau Mora
         * @since 20210313
         * @param progress The progress of the notification
         * @return The Builder instance
         */
        fun withProgress(progress: ValueMax<Int>): Builder {
            this.progress = progress
            return this
        }

        /**
         * Sets the progress of the notification.
         * If progress is set to less than 0, it will be indeterminate
         * @author Arnau Mora
         * @since 20210313
         * @param progress The current progress
         * @param max The maximum progress
         * @return The Builder instance
         * @throws IllegalStateException When progress is greater than max
         */
        @Throws(IllegalStateException::class)
        fun withProgress(progress: Int, max: Int): Builder =
            withProgress(ValueMax(progress, max))

        /**
         * Adds a button to the notification
         * @author Arnau Mora
         * @since 20210313
         * @param button The button to add
         * @return The Builder instance
         */
        fun addAction(button: NotificationButton): Builder {
            actions.add(button)
            return this
        }

        /**
         * Adds multiple buttons to the notification
         * @author Arnau Mora
         * @since 20210313
         * @param buttons The buttons to add
         * @return The Builder instance
         */
        fun addActions(vararg buttons: NotificationButton): Builder {
            actions.addAll(buttons)
            return this
        }

        /**
         * Adds multiple buttons to the notification
         * @author Arnau Mora
         * @since 20210313
         * @param buttons The buttons to add
         * @return The Builder instance
         */
        fun addActions(buttons: Collection<NotificationButton>): Builder {
            actions.addAll(buttons)
            return this
        }

        /**
         * Builds the notification
         * @author Arnau Mora
         * @since 20210313
         * @return The built notification
         * @throws IllegalStateException If the notification id already exists
         * @throws NullChannelIdException If the channel id is null
         */
        @Throws(IllegalStateException::class, NullChannelIdException::class)
        fun build(): Notification {
            if (channelId == null)
                throw NullChannelIdException()
            if (builders.containsKey(id))
                throw IllegalStateException("The specified notification id is already registered")
            return Notification(this)
        }
    }
}
