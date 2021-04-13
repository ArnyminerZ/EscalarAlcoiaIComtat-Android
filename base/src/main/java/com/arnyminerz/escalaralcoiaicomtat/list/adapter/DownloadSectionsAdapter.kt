package com.arnyminerz.escalaralcoiaicomtat.list.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.annotation.MainThread
import androidx.annotation.UiThread
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import com.arnyminerz.escalaralcoiaicomtat.R
import com.arnyminerz.escalaralcoiaicomtat.activity.MainActivity
import com.arnyminerz.escalaralcoiaicomtat.data.climb.DownloadedSection
import com.arnyminerz.escalaralcoiaicomtat.data.climb.area.Area
import com.arnyminerz.escalaralcoiaicomtat.data.climb.dataclass.DataClass
import com.arnyminerz.escalaralcoiaicomtat.data.climb.dataclass.DataClass.Companion.getIntent
import com.arnyminerz.escalaralcoiaicomtat.data.climb.dataclass.DownloadStatus
import com.arnyminerz.escalaralcoiaicomtat.data.climb.zone.Zone
import com.arnyminerz.escalaralcoiaicomtat.fragment.dialog.DownloadDialog
import com.arnyminerz.escalaralcoiaicomtat.generic.humanReadableByteCountBin
import com.arnyminerz.escalaralcoiaicomtat.generic.toast
import com.arnyminerz.escalaralcoiaicomtat.list.holder.DownloadSectionViewHolder
import com.arnyminerz.escalaralcoiaicomtat.shared.appNetworkState
import com.arnyminerz.escalaralcoiaicomtat.view.visibility
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import timber.log.Timber
import java.util.concurrent.CompletableFuture.runAsync

class DownloadSectionsAdapter(
    private val downloadedSections: ArrayList<DownloadedSection>,
    private val mainActivity: MainActivity
) : RecyclerView.Adapter<DownloadSectionViewHolder>() {
    private val firestore: FirebaseFirestore
        get() = mainActivity.firestore

    override fun getItemCount(): Int = downloadedSections.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadSectionViewHolder =
        DownloadSectionViewHolder(
            LayoutInflater.from(mainActivity).inflate(
                R.layout.download_section_item, parent, false
            )
        )

    @UiThread
    private fun updateDownloadStatus(
        holder: DownloadSectionViewHolder,
        downloadStatus: DownloadStatus,
        section: DataClass<*, *>
    ) {
        Timber.v("Updating download status for $section: $downloadStatus")

        visibility(holder.downloadProgressBar, downloadStatus == DownloadStatus.DOWNLOADING)
        visibility(holder.downloadButton, downloadStatus != DownloadStatus.DOWNLOADED)
        visibility(holder.deleteButton, downloadStatus == DownloadStatus.DOWNLOADED)
        visibility(holder.viewButton, downloadStatus == DownloadStatus.DOWNLOADED)
        visibility(holder.sizeChip, true)

        if (downloadStatus != DownloadStatus.DOWNLOADED) {
            holder.sizeChip.text = mainActivity.getString(R.string.status_not_downloaded)
        } else {
            val size = section.size(mainActivity)

            holder.sizeChip.text = humanReadableByteCountBin(size)

            holder.sizeChip.setOnClickListener {
                Timber.v("Showing download dialog for ZONE")
                DownloadDialog(mainActivity, section, firestore).show()
            }
        }
    }

    /**
     * Requests the download of a [DataClass].
     * @author Arnau Mora
     * @since 20210412
     * @param section The [DataClass] to download.
     * @param holder The view holder of the section.
     */
    @MainThread
    private fun downloadSection(
        section: DataClass<*, *>,
        holder: DownloadSectionViewHolder
    ) {
        val mapStyle = mainActivity.mapFragment.mapStyle
        val mapUri = mapStyle?.uri
        runAsync {
            Timber.v("Downloading section \"$section\"")
            val downloadStatus = section.downloadStatus(mainActivity, firestore)

            if (!appNetworkState.hasInternet) {
                Timber.v("Cannot download, there's no internet connection.")
                toast(mainActivity, R.string.toast_error_no_internet)
            } else if (downloadStatus == DownloadStatus.NOT_DOWNLOADED)
                section.download(mainActivity, mapUri)
                    .observe(mainActivity) { workInfo ->
                        val state = workInfo.state
                        val data = workInfo.outputData
                        Timber.v("Current download status: ${workInfo.state}")
                        mainActivity.runOnUiThread {
                            when (state) {
                                WorkInfo.State.FAILED -> {
                                    mainActivity.toast(R.string.toast_error_internal)
                                    visibility(holder.downloadProgressBar, false)
                                    Timber.w("Download failed! Error: ${data.getString("error")}")
                                }
                                WorkInfo.State.SUCCEEDED -> {
                                    visibility(holder.downloadProgressBar, false)
                                    Timber.v("Finished downloading. Updating Downloads Recycler View...")
                                    mainActivity.downloadsFragment.reloadSizeTextView()
                                    updateDownloadStatus(holder, DownloadStatus.DOWNLOADED, section)
                                }
                                else -> holder.downloadProgressBar.isIndeterminate = true
                            }
                        }
                    }
            else if (downloadStatus == DownloadStatus.DOWNLOADING) {
                Timber.v("Already downloading.")
                toast(mainActivity, R.string.message_already_downloading)
            }
        }
    }

    /**
     * Shows a dialog to the user to confirm the deletion of a [DataClass].
     * @author Arnau Mora
     * @since 20210412
     * @param holder The view holder of the section
     * @param section The [DataClass] to delete.
     */
    private fun requestSectionDelete(holder: DownloadSectionViewHolder, section: DataClass<*, *>) {
        MaterialAlertDialogBuilder(
            mainActivity,
            R.style.ThemeOverlay_App_MaterialAlertDialog
        )
            .setTitle(R.string.downloads_delete_dialog_title)
            .setMessage(
                mainActivity.getString(
                    R.string.downloads_delete_dialog_msg,
                    section.displayName
                )
            )
            .setPositiveButton(R.string.action_delete) { _, _ -> deleteSection(holder, section) }
            .setNegativeButton(R.string.action_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    /**
     * Deletes a section, and updates the corresponding views.
     * @author Arnau Mora
     * @since 20210412
     * @param holder The view holder of the section
     * @param section The [DataClass] to delete.
     */
    @MainThread
    fun deleteSection(holder: DownloadSectionViewHolder, section: DataClass<*, *>) {
        runAsync {
            Timber.v("Deleting section \"$section\"")
            section.delete(mainActivity)
            Timber.v("Section deleted, getting new download status...")
            val newDownloadStatus = section.downloadStatus(mainActivity, firestore)
            Timber.v("Section download status loaded, getting downloaded section list...")
            val newChildSectionList =
                section.downloadedSectionList(mainActivity, firestore, true)
            Timber.v("Got downloaded section list. Updating UI...")

            mainActivity.runOnUiThread {
                mainActivity.downloadsFragment.reloadSizeTextView()
                updateDownloadStatus(holder, newDownloadStatus, section)
                holder.recyclerView.adapter =
                    DownloadSectionsAdapter(newChildSectionList, mainActivity)
                // TODO: This should remove the item from the list
            }
        }
    }

    override fun onBindViewHolder(holder: DownloadSectionViewHolder, position: Int) {
        val downloadedSection = downloadedSections[position]
        val section = downloadedSection.section

        // Update the name of the section
        holder.titleTextView.text = section.displayName

        // Hide all extra progress bars and buttons
        visibility(holder.progressBar, true)
        visibility(holder.downloadProgressBar, false)
        visibility(holder.downloadButton, false)
        visibility(holder.deleteButton, false)
        visibility(holder.viewButton, false)
        visibility(holder.toggleButton, false)
        visibility(holder.sizeChip, false)

        runAsync {
            Timber.v("Checking section's downloaded children.")
            val sectionDownloadStatus = section.downloadStatus(mainActivity, firestore)
            // Get all the children for the section
            Timber.v("Loading section list for \"${section.displayName}\"...")
            val childSectionList =
                section.downloadedSectionList(mainActivity, firestore, true)

            mainActivity.runOnUiThread {
                updateDownloadStatus(holder, sectionDownloadStatus, section)

                // Show the toggle button just to Zones and Areas
                visibility(
                    holder.toggleButton,
                    section is Zone || section is Area
                )

                holder.downloadButton.setOnClickListener {
                    downloadSection(section, holder)
                }
                holder.toggleButton.setOnClickListener {
                    downloadedSection.toggle(
                        holder.cardView,
                        it as ImageButton,
                        holder.recyclerView
                    )
                }
                downloadedSection.updateView(
                    holder.cardView,
                    holder.toggleButton,
                    holder.recyclerView
                )

                holder.viewButton.setOnClickListener {
                    val intent =
                        getIntent(mainActivity, section.displayName, firestore)
                    if (intent == null) {
                        Timber.w("Could not launch activity.")
                        toast(mainActivity, R.string.toast_error_internal)
                    } else {
                        Timber.v("Loading intent...")
                        mainActivity.startActivity(intent)
                    }
                }

                holder.recyclerView.layoutManager = LinearLayoutManager(mainActivity)
                Timber.v("  Section List has ${childSectionList.count()} sections")
                Timber.v("Loading data for \"${section.displayName}\"...")
                holder.recyclerView.adapter =
                    DownloadSectionsAdapter(childSectionList, mainActivity)

                holder.deleteButton.setOnClickListener {
                    requestSectionDelete(holder, section)
                }

                visibility(holder.progressBar, false)
            }
        }
    }
}
