package com.arnyminerz.escalaralcoiaicomtat.fragment.climb

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.arnyminerz.escalaralcoiaicomtat.R
import com.arnyminerz.escalaralcoiaicomtat.activity.AREAS
import com.arnyminerz.escalaralcoiaicomtat.activity.climb.SectorActivity
import com.arnyminerz.escalaralcoiaicomtat.data.climb.data.Sector
import com.arnyminerz.escalaralcoiaicomtat.databinding.FragmentSectorBinding
import com.arnyminerz.escalaralcoiaicomtat.fragment.model.NetworkChangeListenerFragment
import com.arnyminerz.escalaralcoiaicomtat.generic.getDisplaySize
import com.arnyminerz.escalaralcoiaicomtat.list.adapter.PathsAdapter
import com.arnyminerz.escalaralcoiaicomtat.network.base.ConnectivityProvider
import com.arnyminerz.escalaralcoiaicomtat.view.ImageLoadParameters
import com.arnyminerz.escalaralcoiaicomtat.view.show
import com.arnyminerz.escalaralcoiaicomtat.view.visibility
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions
import com.bumptech.glide.request.RequestOptions
import timber.log.Timber

const val CROSSFADE_DURATION = 50
const val THUMBNAIL_SIZE = .1f

const val ARGUMENT_AREA_ID = "area_id"
const val ARGUMENT_ZONE_ID = "zone_id"
const val ARGUMENT_SECTOR_INDEX = "sector_index"

class SectorFragment : NetworkChangeListenerFragment() {
    private lateinit var areaId: String
    private lateinit var zoneId: String
    private var sectorIndex: Int = -1
    private lateinit var sector: Sector

    private var loaded = false
    private var maximized = false
    private var notMaximizedImageHeight = 0

    private var _binding: FragmentSectorBinding? = null
    private val binding get() = _binding!!

    private val sectorActivity: SectorActivity?
        get() = (activity as? SectorActivity?)

    private fun refreshMaximizeStatus() {
        binding.sizeChangeFab.setImageResource(
            if (maximized) R.drawable.round_flip_to_front_24
            else R.drawable.round_flip_to_back_24
        )

        sectorActivity?.userInputEnabled(!maximized)
    }

    fun minimize() {
        maximized = false
        if (_binding != null)
            refreshMaximizeStatus()
    }

    private fun loadImage() {
        sector.asyncLoadImage(
            requireContext(),
            binding.sectorImageView,
            binding.sectorProgressBar,
            ImageLoadParameters().apply {
                withTransitionOptions(BitmapTransitionOptions.withCrossFade(CROSSFADE_DURATION))
                withThumbnailSize(THUMBNAIL_SIZE)
                withRequestOptions(
                    with(RequestOptions()) {
                        fitCenter()
                        format(DecodeFormat.PREFER_RGB_565)
                        this
                    }
                )
            })
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
        sectorIndex = requireArguments().getInt(ARGUMENT_SECTOR_INDEX)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStateChange(state: ConnectivityProvider.NetworkState) {
        if (isResumed && view != null && loaded)
            loadImage()
    }

    /**
     * Loads the sector data.
     * @author Arnau Mora
     * @since 20210314
     */
    fun load() {
        if (loaded) {
            sectorActivity?.updateTitle(sector.displayName)
            return
        }

        Timber.d("Loading sector #$sectorIndex of $areaId/$zoneId")
        sectorActivity?.setLoading(true)
        sector = AREAS[areaId]!![zoneId][sectorIndex]

        binding.sectorTextView.text = sector.displayName

        val size = getDisplaySize(requireActivity())
        notMaximizedImageHeight = size.second / 2
        binding.sectorImageViewLayout.layoutParams.height = notMaximizedImageHeight
        binding.sectorImageViewLayout.requestLayout()

        loadImage()

        if (context != null) {
            val context = requireContext()
            Timber.v("Loading paths...")

            // Load Paths
            binding.pathsRecyclerView.layoutManager = LinearLayoutManager(context)
            binding.pathsRecyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(context, R.anim.item_enter_left_animator)
            binding.pathsRecyclerView.adapter = PathsAdapter(sector.children, requireActivity())
            context.visibility(binding.pathsRecyclerView, true)

            // Load info bar
            sector.sunTime.appendChip(context, binding.sunChip)
            sector.kidsAptChip(context, binding.kidsAptChip)
            sector.walkingTimeView(context, binding.walkingTimeTextView)

            // Load chart
            sector.loadChart(context, binding.sectorBarChart)
        } else
            Timber.e("Could not start loading sectors since context is null")

        binding.sizeChangeFab.setOnClickListener {
            maximized = !maximized

            (binding.sectorImageViewLayout.layoutParams as ViewGroup.MarginLayoutParams).apply {
                val tv = TypedValue()
                requireContext().theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)
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

        sectorActivity?.setLoading(false)
        loaded = true
    }
}
