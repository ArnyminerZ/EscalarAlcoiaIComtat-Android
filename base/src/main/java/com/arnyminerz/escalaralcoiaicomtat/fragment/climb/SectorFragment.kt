package com.arnyminerz.escalaralcoiaicomtat.fragment.climb

import android.graphics.Bitmap
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.recyclerview.widget.LinearLayoutManager
import com.arnyminerz.escalaralcoiaicomtat.R
import com.arnyminerz.escalaralcoiaicomtat.activity.climb.SectorActivity
import com.arnyminerz.escalaralcoiaicomtat.data.climb.area.get
import com.arnyminerz.escalaralcoiaicomtat.data.climb.path.Path
import com.arnyminerz.escalaralcoiaicomtat.data.climb.sector.Sector
import com.arnyminerz.escalaralcoiaicomtat.databinding.FragmentSectorBinding
import com.arnyminerz.escalaralcoiaicomtat.fragment.model.NetworkChangeListenerFragment
import com.arnyminerz.escalaralcoiaicomtat.generic.doAsync
import com.arnyminerz.escalaralcoiaicomtat.generic.getDisplaySize
import com.arnyminerz.escalaralcoiaicomtat.generic.uiContext
import com.arnyminerz.escalaralcoiaicomtat.list.adapter.PathsAdapter
import com.arnyminerz.escalaralcoiaicomtat.network.base.ConnectivityProvider
import com.arnyminerz.escalaralcoiaicomtat.shared.AREAS
import com.arnyminerz.escalaralcoiaicomtat.shared.ARGUMENT_AREA_ID
import com.arnyminerz.escalaralcoiaicomtat.shared.ARGUMENT_SECTOR_INDEX
import com.arnyminerz.escalaralcoiaicomtat.shared.ARGUMENT_ZONE_ID
import com.arnyminerz.escalaralcoiaicomtat.shared.CROSSFADE_DURATION
import com.arnyminerz.escalaralcoiaicomtat.shared.SECTOR_THUMBNAIL_SIZE
import com.arnyminerz.escalaralcoiaicomtat.view.ImageLoadParameters
import com.arnyminerz.escalaralcoiaicomtat.view.show
import com.arnyminerz.escalaralcoiaicomtat.view.visibility
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.flow.toCollection
import timber.log.Timber

@ExperimentalBadgeUtils
class SectorFragment : NetworkChangeListenerFragment() {
    private lateinit var areaId: String
    private lateinit var zoneId: String
    private var sectorIndex: Int = -1
    private lateinit var sector: Sector

    private var loaded = false
    private var isDownloaded = false
    private var maximized = false
    private var notMaximizedImageHeight = 0

    private var _binding: FragmentSectorBinding? = null
    internal val binding get() = _binding!!

    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private val sectorActivity: SectorActivity
        get() = (requireActivity() as SectorActivity)

    @UiThread
    private fun refreshMaximizeStatus() {
        binding.sizeChangeFab.setImageResource(
            if (maximized) R.drawable.round_flip_to_front_24
            else R.drawable.round_flip_to_back_24
        )

        sectorActivity.userInputEnabled(!maximized)
    }

    @UiThread
    fun minimize() {
        maximized = false
        if (_binding != null)
            refreshMaximizeStatus()
    }

    /**
     * Load's the sector's image
     * @author Arnau Mora
     * @since 20210323
     */
    private suspend fun loadImage() {
        uiContext {
            binding.sectorProgressBar.visibility(true)
        }
        sector.loadImage(
            requireActivity(),
            storage,
            firestore,
            binding.sectorImageView,
            ImageLoadParameters<Bitmap>().apply {
                withTransitionOptions(BitmapTransitionOptions.withCrossFade(CROSSFADE_DURATION))
                withThumbnailSize(SECTOR_THUMBNAIL_SIZE)
                withResultImageScale(1f)
                withRequestOptions(
                    RequestOptions().apply {
                        centerInside()
                        format(DecodeFormat.PREFER_RGB_565)
                    }
                )
                setShowPlaceholder(false)
            })
        uiContext {
            binding.sectorProgressBar.visibility(false)
        }
        Timber.v("Finished loading image")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        areaId = requireArguments().getString(ARGUMENT_AREA_ID)!!
        zoneId = requireArguments().getString(ARGUMENT_ZONE_ID)!!
        sectorIndex = requireArguments().getInt(ARGUMENT_SECTOR_INDEX, 0)

        firestore = Firebase.firestore
        storage = Firebase.storage
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStateChange(state: ConnectivityProvider.NetworkState) {
        if (isResumed)
            doAsync {
                load()
            }
    }

    /**
     * Loads the sector data.
     * @author Arnau Mora
     * @since 20210314
     */
    @WorkerThread
    suspend fun load() {
        if (!this::zoneId.isInitialized)
            return Timber.w("Could not load since class is not initialized")

        if (loaded && this::sector.isInitialized) {
            uiContext {
                sectorActivity.updateTitle(sector.displayName, isDownloaded)
                doAsync {
                    loadImage()
                }
            }
            return
        }

        Timber.d("Loading sector #$sectorIndex of $areaId/$zoneId")
        uiContext {
            sectorActivity.setLoading(true)
        }
        val sectors = arrayListOf<Sector>()
        AREAS[areaId]!![zoneId].getChildren(sectorActivity.firestore).toCollection(sectors)
        sector = sectors[sectorIndex]

        uiContext {
            binding.sectorTextView.text = sector.displayName
        }

        isDownloaded =
            sector.downloadStatus(requireContext(), sectorActivity.firestore).isDownloaded()

        val size = getDisplaySize(requireActivity())
        notMaximizedImageHeight = size.second / 2

        uiContext {
            binding.sectorImageViewLayout.layoutParams.height = notMaximizedImageHeight
            binding.sectorImageViewLayout.requestLayout()
        }

        if (activity != null) {
            Timber.v("Loading paths...")
            val children = arrayListOf<Path>()
            sector.getChildren(sectorActivity.firestore).toCollection(children)
            Timber.v("Finished loading children sectors")

            Timber.v("Loading sector fragment")
            loadImage()

            uiContext {
                Timber.v("Finished loading paths, performing UI updates")
                (this as? SectorActivity?)?.updateTitle(sector.displayName, isDownloaded)

                binding.sizeChangeFab.setOnClickListener {
                    maximized = !maximized

                    (binding.sectorImageViewLayout.layoutParams as ViewGroup.MarginLayoutParams).apply {
                        val tv = TypedValue()
                        requireContext().theme.resolveAttribute(
                            android.R.attr.actionBarSize,
                            tv,
                            true
                        )
                        val actionBarHeight = resources.getDimensionPixelSize(tv.resourceId)
                        setMargins(0, if (maximized) actionBarHeight else 0, 0, 0)
                        height =
                            if (maximized) LinearLayout.LayoutParams.MATCH_PARENT else notMaximizedImageHeight
                    }
                    binding.sectorImageViewLayout.requestLayout()

                    refreshMaximizeStatus()
                }
                binding.dataScrollView.show()
                refreshMaximizeStatus()

                // Load Paths
                binding.pathsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
                binding.pathsRecyclerView.layoutAnimation =
                    AnimationUtils.loadLayoutAnimation(
                        requireContext(),
                        R.anim.item_enter_left_animator
                    )
                binding.pathsRecyclerView.adapter =
                    PathsAdapter(children, requireActivity() as SectorActivity)
                binding.pathsRecyclerView.show()

                // Load info bar
                sector.sunTime.appendChip(requireContext(), binding.sunChip)
                sector.kidsAptChip(requireContext(), binding.kidsAptChip)
                sector.walkingTimeView(requireContext(), binding.walkingTimeTextView)

                // Load chart
                sector.loadChart(requireActivity(), binding.sectorBarChart, children)

                (this as? SectorActivity?)?.setLoading(false)
            }
        } else
            Timber.e("Could not start loading sectors since context is null")

        loaded = true
    }
}
