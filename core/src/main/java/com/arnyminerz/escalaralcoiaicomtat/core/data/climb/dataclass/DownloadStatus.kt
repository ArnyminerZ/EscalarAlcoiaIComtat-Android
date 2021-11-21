package com.arnyminerz.escalaralcoiaicomtat.core.data.climb.dataclass

import androidx.annotation.DrawableRes
import com.arnyminerz.escalaralcoiaicomtat.core.R

/**
 * Specifies the download status of a [DataClass].
 * @author Arnau Mora
 * @since 20210413
 */
enum class DownloadStatus {
    /**
     * The [DataClass] is not downloaded.
     * @author Arnau Mora
     * @since 20210413
     */
    NOT_DOWNLOADED,

    /**
     * The [DataClass] is downloaded.
     * @author Arnau Mora
     * @since 20210413
     */
    DOWNLOADED,

    /**
     * The [DataClass] is currently being downloaded.
     * @author Arnau Mora
     * @since 20210413
     */
    DOWNLOADING,

    /**
     * The [DataClass] is not completely downloaded, this means that maybe a child or more is
     * downloaded, but the whole class isn't.
     * @author Arnau Mora
     * @since 20210413
     */
    PARTIALLY;

    override fun toString(): String = name

    operator fun not(): Boolean = this != DOWNLOADED

    /**
     * Checks if the [DataClass] is completely downloaded.
     * @author Arnau Mora
     * @since 20210413
     */
    val downloaded: Boolean
        get() = this == DOWNLOADED

    /**
     * Checks if the [DataClass] is partially downloaded.
     * @author Arnau Mora
     * @since 20210413
     */
    val partialDownload: Boolean
        get() = this == PARTIALLY

    /**
     * Checks if the [DataClass] is being downloaded.
     * @author Arnau Mora
     * @since 20210820
     */
    val downloading: Boolean
        get() = this == DOWNLOADING

    /**
     * Gets the icon that should be displayed for the download status.
     * @author Arnau Mora
     * @since 20210820
     */
    @DrawableRes
    fun getIcon(): Int =
        when (this) {
            NOT_DOWNLOADED -> R.drawable.download
            DOWNLOADED -> R.drawable.cloud_check
            DOWNLOADING -> R.drawable.cloud_sync
            PARTIALLY -> R.drawable.cloud_braces
        }
}