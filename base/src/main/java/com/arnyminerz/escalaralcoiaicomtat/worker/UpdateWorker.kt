package com.arnyminerz.escalaralcoiaicomtat.worker

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.*
import com.arnyminerz.escalaralcoiaicomtat.R
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.area.loadAreas
import com.arnyminerz.escalaralcoiaicomtat.core.notification.DOWNLOAD_PROGRESS_CHANNEL_ID
import com.arnyminerz.escalaralcoiaicomtat.core.notification.Notification
import com.arnyminerz.escalaralcoiaicomtat.core.shared.App
import com.arnyminerz.escalaralcoiaicomtat.core.shared.PREF_INDEXED_SEARCH
import com.arnyminerz.escalaralcoiaicomtat.core.shared.SETTINGS_MOBILE_DOWNLOAD_PREF
import com.arnyminerz.escalaralcoiaicomtat.core.shared.SETTINGS_ROAMING_DOWNLOAD_PREF
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import timber.log.Timber
import kotlin.random.Random


/**
 * The tag used for identifying the worker and fetching its livedata.
 * @author Arnau Mora
 * @since 20210926
 */
const val UPDATE_WORKER_TAG = "data_updater"

/**
 * The worker that aims to keep the app's data updated. When called, when all the requirements are
 * met the app's data will automatically be updated.
 * @author Arnau Mora
 * @since 20210919
 */
class UpdateWorker
private constructor(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        // Get the worker data
        val notificationId = inputData.getInt(WORKER_PARAMETER_NOTIFICATION_ID, -1)

        // Remove already indexed value so data will be fetched again.
        PREF_INDEXED_SEARCH.put(false)

        // Get the Firestore instance
        val firestore = Firebase.firestore

        // Get the app instance
        val app = applicationContext as App

        // Get the notification
        var notificationBuilder = Notification.get(notificationId) ?: run {
            return Result.failure(workDataOf("error" to "notification_not_found"))
        }
        // Dismiss the old content
        Notification.dismiss(applicationContext, notificationId)

        notificationBuilder = notificationBuilder
            .withId(Random.nextInt())
            .withTitle(R.string.notification_new_version_downloading_title)
            .withText(R.string.notification_new_version_downloading_short)
            .withLongText(
                applicationContext.getString(
                    R.string.notification_new_version_downloading_message,
                    0, 0
                )
            )
        var noti = notificationBuilder.buildAndShow()

        // Load areas
        Timber.d("Downloading areas...")
        firestore.loadAreas(app) { progress ->
            Timber.v("Areas load progress: ${progress.percentage}")

            // Hide and destroy the notification
            noti.destroy()

            notificationBuilder = notificationBuilder
                .withLongText(
                    applicationContext.getString(
                        R.string.notification_new_version_downloading_message,
                        progress.value,
                        progress.max
                    )
                )
                .withProgress(progress)
            noti = notificationBuilder.buildAndShow()
        }
        noti.destroy()

        // TODO: Update downloads

        return Result.success()
    }

    companion object {
        /**
         * This is used for passing to the worker the id of the notification that is used for
         * updating the progress.
         * @author Arnau Mora
         * @since 20210919
         */
        private const val WORKER_PARAMETER_NOTIFICATION_ID = "NotificationId"

        /**
         * Schedules the worker to be ran as soon as possible.
         * @author Arnau Mora
         * @since 20210919
         */
        fun schedule(context: Context): LiveData<WorkInfo> {
            // Create the notification for displaying the progress to the user
            val notificationBuilder = Notification.Builder(context)
                .withChannelId(DOWNLOAD_PROGRESS_CHANNEL_ID)
                .withIcon(R.drawable.ic_notifications)
                .withTitle(R.string.notification_new_version_waiting_title)
                .withText(R.string.notification_new_version_waiting_short)
                .withLongText(R.string.notification_new_version_waiting_message)
                .withProgress(-1, 0, true)
                .withColorResource(R.color.colorAccent)
            notificationBuilder.buildAndShow()

            val constraints = Constraints.Builder()
                .apply {
                    if (!SETTINGS_ROAMING_DOWNLOAD_PREF.get())
                        setRequiredNetworkType(NetworkType.NOT_ROAMING)
                    else if (SETTINGS_MOBILE_DOWNLOAD_PREF.get())
                        setRequiredNetworkType(NetworkType.UNMETERED)
                    else
                        setRequiredNetworkType(NetworkType.CONNECTED)
                }
            Timber.v("Building DownloadWorker request...")
            val request = OneTimeWorkRequestBuilder<UpdateWorker>()
                .setConstraints(constraints.build())
                .addTag(UPDATE_WORKER_TAG)
                .setInputData(workDataOf(WORKER_PARAMETER_NOTIFICATION_ID to notificationBuilder.id))
                .build()

            Timber.v("Getting WorkManager instance, and enqueueing job.")
            val workManager = WorkManager
                .getInstance(context)
            workManager.enqueue(request)

            return workManager.getWorkInfoByIdLiveData(request.id)
        }
    }
}
