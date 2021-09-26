package com.arnyminerz.escalaralcoiaicomtat.worker

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appsearch.app.AppSearchSession
import androidx.lifecycle.LiveData
import androidx.work.*
import com.arnyminerz.escalaralcoiaicomtat.activity.climb.SectorActivity
import com.arnyminerz.escalaralcoiaicomtat.activity.climb.ZoneActivity
import com.arnyminerz.escalaralcoiaicomtat.core.R
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.dataclass.DataClass
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.sector.Sector
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.zone.Zone
import com.arnyminerz.escalaralcoiaicomtat.core.notification.DOWNLOAD_COMPLETE_CHANNEL_ID
import com.arnyminerz.escalaralcoiaicomtat.core.notification.DOWNLOAD_PROGRESS_CHANNEL_ID
import com.arnyminerz.escalaralcoiaicomtat.core.notification.Notification
import com.arnyminerz.escalaralcoiaicomtat.core.shared.*
import com.arnyminerz.escalaralcoiaicomtat.core.utils.*
import com.arnyminerz.escalaralcoiaicomtat.core.utils.storage.dataDir
import com.arnyminerz.escalaralcoiaicomtat.core.worker.download.*
import com.arnyminerz.escalaralcoiaicomtat.core.worker.failure
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.File
import java.io.IOException

@ExperimentalBadgeUtils
class DownloadWorker
private constructor(appContext: Context, workerParams: WorkerParameters) :
    DownloadWorkerModel, CoroutineWorker(appContext, workerParams) {
    override val factory: DownloadWorkerFactory = Companion

    private lateinit var namespace: String
    private lateinit var displayName: String
    private var downloadPath: String? = null
    private var overwrite: Boolean = false
    private var quality: Int = -1

    private lateinit var storage: FirebaseStorage

    /**
     * Specifies the downloading notification. For modifying it later.
     * @since 20210323
     */
    private lateinit var notification: Notification

    /**
     * The session for performing data loads.
     * @author Arnau Mora
     * @since 20210818
     */
    private lateinit var appSearchSession: AppSearchSession

    /**
     * Downloads the image file for the specified object.
     * @author Arnau Mora
     * @since 20210822
     * @param imageReferenceUrl The [FirebaseStorage] reference url of the image to download.
     * @param imageFile The [File] instance referencing where the object's image should be stored at.
     * @param objectId The id of the object to download.
     * @param scale The scale with which to download the object.
     * @param progressListener A listener for observing the download progress.
     * @see ERROR_ALREADY_DOWNLOADED
     * @see ERROR_DELETE_OLD
     * @see ERROR_CREATE_PARENT
     * @see ERROR_COMPRESS_IMAGE
     * @see ERROR_FETCH_IMAGE
     * @see ERROR_STORE_IMAGE
     */
    private suspend fun downloadImageFile(
        imageReferenceUrl: String,
        imageFile: File,
        objectId: String,
        scale: Float,
        progressListener: suspend (progress: ValueMax<Long>) -> Unit
    ): Result = coroutineScope {
        val dataDir = imageFile.parentFile!!

        var error: Result? = null
        if (imageFile.exists() && !overwrite)
            error = failure(ERROR_ALREADY_DOWNLOADED)
        if (!imageFile.deleteIfExists())
            error = failure(ERROR_DELETE_OLD)
        if (!dataDir.exists() && !dataDir.mkdirs())
            error = failure(ERROR_CREATE_PARENT)
        if (error != null)
            return@coroutineScope error

        val n = namespace[0] // The key for logs
        val pin = "$n/$objectId" // This is for identifying progress logs

        notification
            .edit()
            .withInfoText(R.string.notification_download_progress_info_downloading_image)
            .buildAndShow()

        try {
            var existingImage: File? = null
            val cacheDir = applicationContext.cacheDir

            // If there's a full version cached version of the image, select it
            val cacheFile = File(cacheDir, DataClass.imageName(namespace, objectId, null))
            if (existingImage == null && cacheFile.exists())
                existingImage = cacheFile

            // If there's an scaled (default scale) version of the image, select it
            val scaledCacheFile = File(
                cacheDir,
                DataClass.imageName(namespace, objectId, "scale$DATACLASS_PREVIEW_SCALE")
            )
            if (existingImage == null && scaledCacheFile.exists())
                existingImage = scaledCacheFile

            // If there's an scaled (given scale) version of the image, select it
            val scaledFile = File(
                cacheDir,
                DataClass.imageName(namespace, objectId, "scale$scale")
            )
            if (existingImage == null && scaledFile.exists())
                existingImage = scaledFile

            // This is the old image format. If there's one cached, select it
            val tempFile = File(applicationContext.cacheDir, "dataClass_$objectId")
            if (existingImage == null && tempFile.exists())
                existingImage = tempFile

            // If an image has been selected, copy it to imageFile.
            if (existingImage?.exists() == true) {
                Timber.d("$pin > Copying cached image file from \"$existingImage\" to \"$imageFile\"")
                existingImage.copyTo(imageFile, overwrite = true)
            } else {
                // Otherwise, download it from the server.
                Timber.d("$pin > Downloading image from Firebase Storage: $imageReferenceUrl...")
                Timber.v("$pin > Getting reference...")
                val reference = storage.getReferenceFromUrl(imageReferenceUrl)
                Timber.v("$pin > Fetching stream...")
                val snapshot = reference.stream
                    .addOnProgressListener { snapshot ->
                        val progress = ValueMax(snapshot.bytesTransferred, snapshot.totalByteCount)
                        CoroutineScope(coroutineContext).launch { progressListener(progress) }
                        Timber.v("$pin > Image progress: ${progress.percentage}")
                    }
                    .await()
                Timber.v("$pin > Got image stream, decoding...")
                val stream = snapshot.stream
                val bitmap: Bitmap? = BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply {
                        inSampleSize = (1 / scale).toInt()
                    }
                )
                if (bitmap != null) {
                    Timber.v("$pin > Got bitmap.")
                    val quality = (scale * 100).toInt()
                    Timber.v("$pin > Compression quality: $quality")
                    Timber.v("$pin > Compressing bitmap to \"$imageFile\"...")
                    val compressed =
                        bitmap.compress(WEBP_LOSSY_LEGACY, quality, imageFile.outputStream())
                    if (compressed)
                        Timber.v("$pin > Bitmap compressed successfully.")
                    else
                        return@coroutineScope failure(ERROR_COMPRESS_IMAGE)
                } else
                    return@coroutineScope failure(ERROR_FETCH_IMAGE)
            }
        } catch (e: StorageException) {
            Timber.w(e, "Could not get image")
            return@coroutineScope failure(ERROR_FETCH_IMAGE)
        } catch (e: IOException) {
            Timber.e(e, "Could not copy image file")
            return@coroutineScope failure(ERROR_STORE_IMAGE)
        }

        if (!imageFile.exists())
            return@coroutineScope failure(ERROR_STORE_IMAGE)

        return@coroutineScope Result.success()
    }

    /**
     * Downloads the KMZ file from the server.
     * @author Arnau Mora
     * @since 20210926
     * @param storage The [FirebaseStorage] reference for fetching files from the server.
     * @param referenceUrl The url in [storage] where the KMZ is stored at.
     * @param targetFile The [File] to store the KMZ data at.
     * @param progressListener A callback function for observing the download progress.
     */
    private suspend fun downloadKmz(
        storage: FirebaseStorage,
        referenceUrl: String,
        targetFile: File,
        progressListener: suspend (progress: ValueMax<Long>) -> Unit
    ) = coroutineScope {
        progressListener(ValueMax(0, -1))

        Timber.v("Getting kmz file reference ($referenceUrl)...")
        val reference = storage.getReferenceFromUrl(referenceUrl)

        val snapshot = reference
            .stream
            .addOnProgressListener { snapshot ->
                CoroutineScope(coroutineContext).launch { progressListener(snapshot.progress()) }
            }
            .await()
        progressListener(snapshot.progress())

        Timber.v("Got the KMZ stream. Writing to the output file...")
        val stream = snapshot.stream
        val outputStream = targetFile.outputStream()
        var read = stream.read()
        while (read > 0) {
            outputStream.write(read)
            read = stream.read()
        }
        outputStream.close()
        stream.close()
    }

    /**
     * Fetches the data from [FirebaseFirestore] at the specified [path]. And downloads the image
     * file.
     * @author Arnau Mora
     * @since 20210926
     * @param firestore The [FirebaseFirestore] instance to fetch the data from.
     * @param storage The [FirebaseStorage] instance to fetch files from the server.
     * @param path The path where the data is stored at.
     * @param progressListener A callback function for observing the download progress.
     * @throws FirebaseFirestoreException If there happens an exception while fetching the data
     * from the server.
     * @throws RuntimeException When there is an unexpected type exception in the data from the server.
     * @throws IllegalStateException If the document at [path] doesn't contain a required field.
     */
    @Throws(FirebaseFirestoreException::class)
    private suspend fun downloadData(
        firestore: FirebaseFirestore,
        storage: FirebaseStorage,
        path: String,
        progressListener: suspend (progress: ValueMax<Long>) -> Unit,
        iterateChildren: Boolean = true
    ) = coroutineScope {
        progressListener(ValueMax(0, -1)) // Set to -1 for indeterminate

        Timber.v("Getting document ($path)...")
        val document = firestore.document(path).get().await()

        Timber.v("Processing namespace...")
        val namespace = document.reference.parent.id.let { collectionName ->
            collectionName.substring(0, collectionName.length - 1)
        }

        Timber.v("Getting fields...")
        val objectId = document.id
        val imageReferenceUrl = document.getString("image") ?: run {
            Timber.w("Object at \"$path\" doesn't have an image field.")
            throw IllegalStateException("Object at \"$path\" doesn't have an image field.")
        }
        val kmzReferenceUrl = document.getString("kmz")

        val imageFileName = DataClass.imageName(namespace, objectId, null)
        val imageFile = File(dataDir(applicationContext), imageFileName)
        // If the download is Zone, store the image in lower res, if it's Sector, download HD
        val isPreview = namespace == Zone.NAMESPACE
        val scale = if (isPreview) DATACLASS_PREVIEW_SCALE else 1f

        Timber.v("Downloading image file...")
        downloadImageFile(imageReferenceUrl, imageFile, objectId, scale, progressListener)

        if (kmzReferenceUrl != null) {
            val kmzFileName = "${namespace}_$objectId"
            val kmzFile = File(dataDir(applicationContext), kmzFileName)
            downloadKmz(storage, kmzReferenceUrl, kmzFile, progressListener)
        }

        // Zones have children
        if (iterateChildren && namespace == Zone.NAMESPACE) {
            val sectorsCollection = document.reference.collection("Sectors")
            val collectionsReference = sectorsCollection.get().await()
            val documents = collectionsReference.documents
            for (sectorDocument in documents) {
                val documentPath: String = sectorDocument.reference.path
                downloadData(firestore, storage, documentPath, progressListener, false)
            }
        }
    }

    override suspend fun doWork(): Result {
        // Get all data
        val namespace = inputData.getString(DOWNLOAD_NAMESPACE)
        downloadPath = inputData.getString(DOWNLOAD_PATH)
        val displayName = inputData.getString(DOWNLOAD_DISPLAY_NAME)
        overwrite = inputData.getBoolean(DOWNLOAD_OVERWRITE, DOWNLOAD_OVERWRITE_DEFAULT)
        quality = inputData.getInt(DOWNLOAD_OVERWRITE, DOWNLOAD_QUALITY_DEFAULT)

        Timber.v("Starting download for %s".format(displayName))

        // Check if any required data is missing
        return if (namespace == null || downloadPath == null || displayName == null)
            failure(ERROR_MISSING_DATA)
        else {
            Timber.v("Initializing Firebase Storage instance...")
            storage = Firebase.storage

            Timber.v("Initializing search session...")
            appSearchSession = createSearchSession(applicationContext)

            Timber.v("Downloading $namespace at $downloadPath...")
            this.namespace = namespace
            this.displayName = displayName

            val message = applicationContext.getString(
                R.string.notification_download_progress_message,
                displayName
            )

            // Build the notification
            val notificationBuilder = Notification.Builder(applicationContext)
                .withChannelId(DOWNLOAD_PROGRESS_CHANNEL_ID)
                .withIcon(R.drawable.ic_notifications)
                .withTitle(R.string.notification_download_progress_title)
                .withInfoText(R.string.notification_download_progress_info_fetching)
                .withText(message)
                .withProgress(-1, 0)
                .setPersistent(true)
            notification = notificationBuilder.buildAndShow()

            Timber.v("Getting Firestore instance...")
            val firestore = Firebase.firestore

            downloadData(firestore, storage, downloadPath!!) {

            }

            Timber.v("Finished downloading $displayName. Result: $downloadResult")
            notification.destroy()

            val intent: PendingIntent? =
                if (downloadResult == Result.success()) {
                    Timber.v("Getting intent...")
                    val downloadPathSplit = downloadPath!!.split('/')
                    when (namespace) {
                        // Area Skipped since not-downloadable
                        Zone.NAMESPACE -> {
                            // Example: Areas/<Area ID>/Zones/<Zone ID>
                            val zoneId = downloadPathSplit[3]
                            Timber.v("Intent will launch zone with id $zoneId")
                            ZoneActivity.intent(applicationContext, zoneId)
                        }
                        Sector.NAMESPACE -> {
                            // Example: Areas/<Area ID>/Zones/<Zone ID>/Sectors/<Sector ID>
                            val zoneId = downloadPathSplit[3]
                            val sectorId = downloadPathSplit[5]
                            Timber.v("Intent will launch sector with id $sectorId in $zoneId.")
                            SectorActivity.intent(applicationContext, zoneId, sectorId)
                        }
                        else -> {
                            downloadResult = failure(ERROR_UNKNOWN_NAMESPACE)
                            null
                        }
                    }?.let { intent ->
                        val pendingIntent = PendingIntent.getActivity(
                            applicationContext,
                            (System.currentTimeMillis() and 0xffffff).toInt(),
                            intent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
                        )
                        pendingIntent
                    }
                } else null

            if (downloadResult == Result.success()) {
                Timber.v("Showing download finished notification")
                val text = applicationContext.getString(
                    R.string.notification_download_complete_message,
                    this@DownloadWorker.displayName
                )
                Notification.Builder(applicationContext)
                    .withChannelId(DOWNLOAD_COMPLETE_CHANNEL_ID)
                    .withIcon(R.drawable.ic_notifications)
                    .withTitle(R.string.notification_download_complete_title)
                    .withText(text)
                    .withIntent(intent)
                    .buildAndShow()
            } else {
                Timber.v("Download failed! Result: $downloadResult. Showing notification.")
                val text = applicationContext.getString(
                    R.string.notification_download_failed_message,
                    this.displayName
                )
                Notification.Builder(applicationContext)
                    .withChannelId(DOWNLOAD_COMPLETE_CHANNEL_ID)
                    .withIcon(R.drawable.ic_notifications)
                    .withTitle(R.string.notification_download_failed_title)
                    .withText(text)
                    .withLongText(
                        R.string.notification_download_failed_message_long,
                        displayName,
                        downloadResult.outputData.getString("error") ?: "unknown"
                    )
                    .buildAndShow()
            }

            Timber.v("Closing search session...")
            appSearchSession.close()

            downloadResult
        }
    }

    companion object : DownloadWorkerFactory {
        /**
         * Schedules a new download as a new Worker.
         * @author Arnau Mora
         * @since 20210313
         * @param context The context to run from
         * @param tag The tag of the worker
         * @param data The data of the download
         * @return The scheduled operation
         *
         * @see DownloadData
         * @see DownloadWorker
         * @see ERROR_MISSING_DATA
         * @see ERROR_ALREADY_DOWNLOADED
         * @see ERROR_DELETE_OLD
         * @see ERROR_DATA_FETCH
         * @see ERROR_STORE_IMAGE
         * @see ERROR_UPDATE_IMAGE_REF
         * @see ERROR_COMPRESS_IMAGE
         * @see ERROR_FETCH_IMAGE
         */
        @JvmStatic
        override fun schedule(
            context: Context,
            tag: String,
            data: DownloadData
        ): LiveData<WorkInfo> {
            Timber.v("Scheduling new download...")
            Timber.v("Building download constraints...")
            val constraints = Constraints.Builder()
                .apply {
                    if (SETTINGS_MOBILE_DOWNLOAD_PREF.get())
                        setRequiredNetworkType(NetworkType.UNMETERED)
                    else if (!SETTINGS_ROAMING_DOWNLOAD_PREF.get())
                        setRequiredNetworkType(NetworkType.NOT_ROAMING)
                    else
                        setRequiredNetworkType(NetworkType.CONNECTED)
                }

            Timber.v("Building DownloadWorker request...")
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(constraints.build())
                .addTag(WORKER_TAG_DOWNLOAD)
                .addTag(tag)
                .setInputData(
                    with(data) {
                        workDataOf(
                            DOWNLOAD_NAMESPACE to dataClass.namespace,
                            DOWNLOAD_PATH to dataClass.metadata.documentPath,
                            DOWNLOAD_DISPLAY_NAME to dataClass.displayName,
                            DOWNLOAD_OVERWRITE to overwrite,
                            DOWNLOAD_QUALITY to quality
                        )
                    }
                )
                .build()

            Timber.v("Getting WorkManager instance, and enqueueing job.")
            val workManager = WorkManager
                .getInstance(context)
            workManager.enqueue(request)

            return workManager.getWorkInfoByIdLiveData(request.id)
        }
    }
}
