package com.arnyminerz.escalaralcoiaicomtat.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.LiveData
import androidx.work.*
import com.arnyminerz.escalaralcoiaicomtat.R
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.area.Area
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.area.loadAreas
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.sector.Sector
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.zone.Zone
import com.arnyminerz.escalaralcoiaicomtat.core.notification.DOWNLOAD_PROGRESS_CHANNEL_ID
import com.arnyminerz.escalaralcoiaicomtat.core.notification.Notification
import com.arnyminerz.escalaralcoiaicomtat.core.shared.App
import com.arnyminerz.escalaralcoiaicomtat.core.shared.PREF_INDEXED_SEARCH
import com.arnyminerz.escalaralcoiaicomtat.core.shared.SETTINGS_MOBILE_DOWNLOAD_PREF
import com.arnyminerz.escalaralcoiaicomtat.core.shared.SETTINGS_ROAMING_DOWNLOAD_PREF
import com.arnyminerz.escalaralcoiaicomtat.core.utils.*
import com.arnyminerz.escalaralcoiaicomtat.core.utils.storage.dataDir
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
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

        suspend fun updateProgress(step: String, value: Int) {
            setProgress(
                workDataOf(
                    PROGRESS_KEY_STEP to step,
                    PROGRESS_KEY_VALUE to value,
                    PROGRESS_KEY_INFO_NOTIFICATION to notificationId
                )
            )
        }

        updateProgress(PROGRESS_STEP_PRE, 0)

        // Remove already indexed value so data will be fetched again.
        PREF_INDEXED_SEARCH.put(false)

        // Get the required Firebase instances
        val firestore = Firebase.firestore
        val storage = Firebase.storage

        // Get the app instance
        val app = applicationContext as App

        // Get the notification
        var notificationBuilder = Notification.get(notificationId) ?: run {
            return Result.failure(workDataOf("error" to "notification_not_found"))
        }
        // Dismiss the old content
        Notification.dismiss(applicationContext, notificationId)

        updateProgress(PROGRESS_STEP_PRE, 100)

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

        updateProgress(PROGRESS_STEP_DATA_DOWNLOAD, 0)

        // Load areas
        Timber.d("Downloading areas...")
        firestore.loadAreas(app) { progress ->
            Timber.v("Areas load progress: ${progress.percentage}")

            doAsync {
                updateProgress(PROGRESS_STEP_DATA_DOWNLOAD, progress.percentage)
            }

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

        updateProgress(PROGRESS_STEP_DATA_DOWNLOAD, 100)
        updateProgress(PROGRESS_STEP_IMAGE_PROCESS, 0)

        // Get all the image files, both from cache and downloads
        Timber.v("Getting image files...")
        val imageFiles = fetchImageFiles { progress ->
            updateProgress(PROGRESS_STEP_IMAGE_PROCESS, progress.percentage)

            notificationBuilder = notificationBuilder
                .withLongText(
                    applicationContext.getString(
                        R.string.notification_new_version_image_files_message,
                        progress.value,
                        progress.max
                    )
                )
                .withProgress(progress)
            noti = notificationBuilder.buildAndShow()
        }
        updateProgress(PROGRESS_STEP_IMAGE_PROCESS, 100)

        val cacheImageFiles = imageFiles.first
        val dataImageFiles = imageFiles.second
        val cacheImageFilesCount = cacheImageFiles.size
        val dataImageFilesCount = dataImageFiles.size
        val processedImageFilesCount = cacheImageFilesCount + dataImageFilesCount

        Timber.v("Got $cacheImageFilesCount cached image files.")
        Timber.v("Got $dataImageFilesCount data image files.")

        // Remove all cached files
        Timber.v("Removing all cached files...")
        for ((f, cachedFile) in imageFiles.first.withIndex()) {
            val vm = ValueMax(f, processedImageFilesCount)
            updateProgress(PROGRESS_STEP_IMAGE_REFERENCING, vm.percentage)

            notificationBuilder = notificationBuilder
                .withLongText(
                    applicationContext.getString(
                        R.string.notification_new_version_cache_clear_message,
                        vm.value,
                        vm.max
                    )
                )
                .withProgress(vm)
            noti = notificationBuilder.buildAndShow()

            if (!cachedFile.deleteIfExists())
                Timber.w("Could not delete $cachedFile")
        }

        // Create the relations between the downloaded files, and its references in Firebase Storage.
        Timber.v("Referencing image files...")
        val referencedImageFiles = processImageFiles(firestore, imageFiles.second) {
            val vm = ValueMax(it + cacheImageFilesCount, processedImageFilesCount)
            updateProgress(PROGRESS_STEP_IMAGE_REFERENCING, vm.percentage)

            notificationBuilder = notificationBuilder
                .withLongText(
                    applicationContext.getString(
                        R.string.notification_new_version_referencing_message,
                        vm.value,
                        vm.max
                    )
                )
                .withProgress(vm)
            noti = notificationBuilder.buildAndShow()
        }
        Timber.v("Got ${referencedImageFiles.size} referenced image files.")

        // Compare the md5 hash from the server and the downloaded file, and add to the list the
        // hashes that don't match.
        Timber.v("Running checksum of image files...")
        val imageFileReferences = getDownloadableImages(storage, referencedImageFiles) {
            val vm = ValueMax(it + cacheImageFilesCount, processedImageFilesCount)
            updateProgress(PROGRESS_STEP_IMAGE_CHECKSUM, vm.percentage)

            notificationBuilder = notificationBuilder
                .withLongText(
                    applicationContext.getString(
                        R.string.notification_new_version_image_check_message,
                        vm.value,
                        vm.max
                    )
                )
                .withProgress(vm)
        }
        Timber.v("Got ${imageFileReferences.size} updatable image files.")

        Timber.v("Downloading images from server...")
        updateFiles(imageFileReferences) { progress, individual ->
            val vm = ValueMax(progress, imageFileReferences.size)
            updateProgress(PROGRESS_STEP_IMAGE_DOWNLOAD, vm.percentage)

            val imageDownloadProgress = ValueMax(individual.percentage, 100)

            notificationBuilder = notificationBuilder
                .withLongText(
                    applicationContext.getString(
                        R.string.notification_new_version_downloading_images_message,
                        vm.value,
                        vm.max,
                        individual.percentage
                    )
                )
                .withProgress(imageDownloadProgress)
            noti = notificationBuilder.buildAndShow()
        }

        noti.destroy()

        Timber.v("Finished updating data.")
        return Result.success()
    }

    /**
     * Gets a list of all the cached and downloaded image files.
     * @author Arnau Mora
     * @since 20210926
     * @return A pair of list of files. The first element is a list of the cached image files, the
     * second one a list of the downloaded image files.
     */
    private suspend fun fetchImageFiles(progress: suspend (progress: ValueMax<Int>) -> Unit): Pair<List<File>, List<File>> {
        val cacheDir = applicationContext.cacheDir
        val cacheFiles = cacheDir.listFiles()
        val dataFiles = dataDir(applicationContext).listFiles()

        val cacheFilesCount = cacheFiles?.size ?: 0
        val dataFilesCount = dataFiles?.size ?: 0
        val max = cacheFilesCount + dataFilesCount

        suspend fun iterateFiles(offset: Int, files: Array<File>?): List<File> {
            val imageFiles = arrayListOf<File>()
            if (files != null)
                for ((f, file) in files.withIndex()) {
                    progress(ValueMax(offset + f, max))
                    if (file.isDirectory)
                        continue
                    val name = file.name
                    val isDataClassFile = name.startsWith(Area.NAMESPACE) ||
                            name.startsWith(Zone.NAMESPACE) || name.startsWith(Sector.NAMESPACE)
                    if (isDataClassFile)
                        imageFiles.add(file)
                }
            return imageFiles
        }

        val cacheImageFiles = iterateFiles(0, cacheFiles)
        val imageFiles = iterateFiles(cacheFilesCount, dataFiles)

        return cacheImageFiles to imageFiles
    }

    /**
     * Processes the image files from [imageFiles], and elaborates a list with the info required to
     * update only the files that need to be downloaded again from the server.
     * @author Arnau Mora
     * @since 20210926
     * @param firestore The [FirebaseFirestore] instance to access the database data.
     * @param imageFiles The list of files to check for updates.
     * @return A list of pairs of a File and a String. The first File is the matching image file
     * from the Filesystem. The String is the reference of the file in the Firebase Cloud storage.
     * @throws FirebaseFirestoreException When there occurs a problem while fetching data from the
     * server.
     * @throws IndexOutOfBoundsException Thrown when the loading of a collection group from firebase
     * returns an exception, or can't be loaded, that has not been caught by
     * [FirebaseFirestoreException]. This should never be thrown.
     * @throws RuntimeException When there's a type conversion issue.
     */
    @Throws(
        FirebaseFirestoreException::class,
        IndexOutOfBoundsException::class,
        RuntimeException::class
    )
    private suspend fun processImageFiles(
        firestore: FirebaseFirestore,
        imageFiles: List<File>,
        progress: suspend (count: Int) -> Unit
    ): List<Pair<File, String>> {
        val collections = hashMapOf<String, QuerySnapshot>()
        val builtImageRelationList = arrayListOf<Pair<File, String>>()

        // Now, for each image, compare its metadata with the one from the server, and create a new
        //   list with the images that have been updated.
        for ((i, imageFile) in imageFiles.withIndex()) {
            progress(i)
            val imageName = imageFile.name
            val hyphenPos = imageName.indexOf('-')
            val namespace = imageName.substring(0, hyphenPos)
            // Add 22 since the id of the objects is always length 20, and for substring the last
            // index is non-inclusive.
            val id = imageName.substring(hyphenPos + 1, hyphenPos + 22)

            // Add the final "s" for making the namespace plural
            val collectionName = "${namespace}s"
            // Make sure that collections contains all the data required. This is used for caching
            // the data, and not overloading the server
            if (!collections.containsKey(collectionName))
                collections[collectionName] = try {
                    firestore.collectionGroup(collectionName)
                        .get()
                        .await()
                } catch (e: FirebaseFirestoreException) {
                    Timber.e(
                        e,
                        "Could not get the collection group named \"$collectionName\" from " +
                                "Firebase Firestore."
                    )
                    throw e
                }
            // Now get all the collections that match the desired namespace
            val namespacedCollections = collections[collectionName] ?: run {
                Timber.e("Could not fetch collections with name $collectionName from server.")
                throw IndexOutOfBoundsException(
                    "There was an unhandled exception while loading the collections with name " +
                            "\"$collectionName\" that didn't allow it to be stored in memory to" +
                            "fetch data from the server."
                )
            }
            val documents = namespacedCollections.documents
            documents.find { it.id == id }?.let { document ->
                try {
                    val imageReference = document.getString("image") ?: throw RuntimeException()
                    builtImageRelationList.add(imageFile to imageReference)
                } catch (e: RuntimeException) {
                    Timber.e(
                        e,
                        "Tried to get the \"image\" field from element at " +
                                "\"${document.reference.path}\" but it was not an String."
                    )
                    throw e
                }
            } ?: run {
                Timber.e(
                    "Could not find the document of the image (id=\"$id\") in the collection" +
                            "\"$collectionName\"."
                )
            }
        }

        return builtImageRelationList
    }

    /**
     * Checks if each of the files in [referencedImageFiles] has to be downloaded or not, and
     * elaborates a list of the references from [FirebaseStorage] for downloading the new files.
     * @author Arnau Mora
     * @since 20210926
     * @param storage The [FirebaseStorage] reference for checking the files from the server.
     * @param referencedImageFiles A list of pairs of references from [storage] and image files from
     * the Filesystem.
     * @param progress A listener for knowing the progress of the function.
     */
    private suspend fun getDownloadableImages(
        storage: FirebaseStorage,
        referencedImageFiles: List<Pair<File, String>>,
        progress: suspend (progress: Int) -> Unit
    ): List<Pair<File, StorageReference>> {
        val updatableFiles = arrayListOf<Pair<File, StorageReference>>()
        for ((i, imageFileReference) in referencedImageFiles.withIndex()) {
            progress(i)
            val imageFile = imageFileReference.first
            val referenceUrl = imageFileReference.second
            val reference = storage.getReferenceFromUrl(referenceUrl)
            val serverMd5 = reference.metadata.await().md5Hash
            val fsMd5 = imageFile.md5Hash()
            Timber.v("Got MD5 hashes. Server=$serverMd5. FileSystem=$fsMd5")
            if (serverMd5 != fsMd5)
                updatableFiles.add(imageFile to reference)
        }
        return updatableFiles
    }

    /**
     * Downloads the new image files from the server, and replaces the ones in the device's filesystem.
     * @author Arnau Mora
     * @since 20210926
     * @param imagesList A list of pairs that contain the [File] from the device's filesystem to
     * update, and a [StorageReference] for reference on the server.
     */
    private suspend fun updateFiles(
        imagesList: List<Pair<File, StorageReference>>,
        progress: suspend (progress: Int, individual: ValueMax<Long>) -> Unit
    ) {
        for ((i, imageFileReference) in imagesList.withIndex()) {
            progress(i, ValueMax(0, 0))
            val imageFile = imageFileReference.first
            val imageFileName = imageFile.nameWithoutExtension
            val reference = imageFileReference.second

            Timber.v("Deleting file at \"$imageFile\"...")
            if (!imageFile.deleteIfExists()) {
                Timber.w("Could not delete file at \"$imageFile\"!")
                continue
            }

            Timber.v("Getting image scale...")
            val scale = if (imageFileName.contains("scale"))
                imageFileName.substringAfter("scale", "1").toFloat()
            else 1f

            val imageQuality = when {
                imageFileName.startsWith(Area.NAMESPACE) -> Area.IMAGE_QUALITY
                imageFileName.startsWith(Zone.NAMESPACE) -> Zone.IMAGE_QUALITY
                else -> Sector.IMAGE_QUALITY
            }

            Timber.v("Downloading image from server: ${reference.path}")
            val fileStreamSnapshot = reference
                .stream
                .addOnProgressListener { taskSnapshot ->
                    val current = taskSnapshot.bytesTransferred
                    val max = taskSnapshot.totalByteCount
                    val vm = ValueMax(current, max)
                    Timber.i("Download progress: ${vm.percentage}%")
                    doAsync { progress(i, vm) }
                }
                .await()
            Timber.v("Got stream, decoding...")
            val stream = fileStreamSnapshot.stream
            val bitmap: Bitmap? = BitmapFactory.decodeStream(
                stream,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = (1 / scale).toInt()
                }
            )
            if (bitmap != null) {
                Timber.v("Compressing image...")
                val baos = ByteArrayOutputStream()
                val compressedBitmap: Boolean =
                    bitmap.compress(WEBP_LOSSY_LEGACY, imageQuality, baos)
                if (!compressedBitmap) {
                    Timber.e("Could not compress image!")
                    throw ArithmeticException("Could not compress image for $this.")
                } else {
                    Timber.v("Storing image...")
                    baos.writeTo(imageFile.outputStream())
                    Timber.v("Image stored.")
                }
            }
        }
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
         * The key of the progress work data for the progress value.
         * @author Arnau Mora
         * @since 20210926
         */
        const val PROGRESS_KEY_VALUE = "Progress"

        /**
         * The key of the step in which the worker is at for the progress work data.
         * @author Arnau Mora
         * @since 20210926
         */
        const val PROGRESS_KEY_STEP = "ProgressStep"

        /**
         * The info key for the progress work data that contains the id of the notification that
         * is being displayed.
         * @author Arnau Mora
         * @since 20210926
         */
        const val PROGRESS_KEY_INFO_NOTIFICATION = "NotificationId"

        /**
         * The name of the first update step, before it starts downloading anything.
         * @author Arnau Mora
         * @since 20210926
         */
        const val PROGRESS_STEP_PRE = "PreLoad"

        /**
         * The name of the data download step.
         * @author Arnau Mora
         * @since 20210926
         */
        const val PROGRESS_STEP_DATA_DOWNLOAD = "DataDownload"

        /**
         * The name of the image processing step.
         * @author Arnau Mora
         * @since 20210926
         */
        const val PROGRESS_STEP_IMAGE_PROCESS = "ImageProcessing"

        /**
         * The name of the image referencing step.
         * @author Arnau Mora
         * @since 20210926
         */
        const val PROGRESS_STEP_IMAGE_REFERENCING = "ImageReferencing"

        /**
         * The name of the image hash checking step.
         * @author Arnau Mora
         * @since 20210926
         */
        const val PROGRESS_STEP_IMAGE_CHECKSUM = "ImageChecksum"

        /**
         * The name of the image download step.
         * @author Arnau Mora
         * @since 20210926
         */
        const val PROGRESS_STEP_IMAGE_DOWNLOAD = "ImageDownload"

        /**
         * Gets the [LiveData] with a list of [WorkInfo] matching all the jobs that are updating the
         * app's data.
         * @author Arnau Mora
         * @since 20210926
         * @param context The [Context] that is requesting the [LiveData].
         * @return A [LiveData] of the [WorkInfo] for the worker, or null if no worker running.
         */
        suspend fun getWorkInfo(context: Context): LiveData<WorkInfo>? {
            Timber.v("Getting WorkManager instance...")
            val workManager = WorkManager
                .getInstance(context)

            Timber.v("Getting work infos by tag...")
            val workInfosFuture = workManager.getWorkInfosByTag(UPDATE_WORKER_TAG)
            val workInfos = workInfosFuture.await()
            if (workInfos.isNotEmpty()) {
                Timber.v("Searching for a non-finished worker...")
                val workInfoIterator = workInfos.iterator()
                while (workInfoIterator.hasNext()) {
                    val workInfo = workInfoIterator.next()
                    val workInfoId = workInfo.id
                    val workState = workInfo.state
                    if (!workState.isFinished) {
                        Timber.v("Found a running worker. Id: $workInfoId")
                        return workManager.getWorkInfoByIdLiveData(workInfoId)
                    }
                }
            }
            Timber.v("Could not find a running worker.")
            return null
        }

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