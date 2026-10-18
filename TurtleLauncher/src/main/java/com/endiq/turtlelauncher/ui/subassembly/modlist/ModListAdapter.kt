package com.endiq.turtlelauncher.ui.subassembly.modlist

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.databinding.ItemModDownloadBinding
import com.endiq.turtlelauncher.feature.download.enums.DependencyType
import com.endiq.turtlelauncher.feature.download.enums.ModLoader
import com.endiq.turtlelauncher.feature.version.utils.VersionIconUtils
import com.endiq.turtlelauncher.feature.version.VersionsManager
import com.endiq.turtlelauncher.utils.anim.AnimatedLogo

class ModListAdapter(
    private val fragment: ModListFragment,
    private val mData: MutableList<ModListItemBean>?
) : RecyclerView.Adapter<ModListAdapter.InnerHolder>() {
    private val mCurrentVersion = VersionsManager.getCurrentVersion()
    private val mIconUtils = lazy { mCurrentVersion?.run { VersionIconUtils(mCurrentVersion) } }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InnerHolder {
        return InnerHolder(ItemModDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: InnerHolder, position: Int) {
        holder.setData(mData?.getOrNull(position) ?: return)
    }

    override fun onViewRecycled(holder: InnerHolder) {
        // Stop any hover/press logo animation before the row is pooled.
        holder.clearAnimatedLogos()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int {
        return mData?.size ?: 0
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newData: List<ModListItemBean>?) {
        mData?.clear()
        if (newData != null) mData?.addAll(newData)
        super.notifyDataSetChanged()
    }

    val data: List<ModListItemBean>?
        get() = mData

    inner class InnerHolder(private val binding: ItemModDownloadBinding) : RecyclerView.ViewHolder(
        binding.root
    ) {
        private val mContext = binding.root.context

        fun setData(item: ModListItemBean) {
            itemView.setOnClickListener {
                fragment.switchToChild(
                    item.getAdapter(),
                    item.title
                )
            }
            binding.apply {
                modVersionId.text = item.title
                val modLoaderInfo = getModLoaderInfo(item.modloader)
                // The vanilla grass block is an animated logo: poster until the
                // row is hovered/pressed/focused. Loader icons stay static.
                if (AnimatedLogo.isAnimatedLogo(modLoaderInfo.first)) {
                    AnimatedLogo.bind(modloaderIcon, modLoaderInfo.first)
                } else {
                    AnimatedLogo.clear(modloaderIcon)
                    modloaderIcon.setImageDrawable(ContextCompat.getDrawable(mContext, modLoaderInfo.first))
                }
                modLoaderInfo.second?.let { name ->
                    modloaderName.visibility = View.VISIBLE
                    modloaderName.text = name
                } ?: run {
                    modloaderName.visibility = View.GONE
                }

                val visibility = if (item.isAdapt) run {
                    mIconUtils.value?.start(currentVersionIcon)
                    root.background.setTint(DependencyType.OPTIONAL.color)
                    View.VISIBLE
                } else run {
                    root.background.setTintList(null)
                    View.GONE
                }

                isAdapt.visibility = visibility
                currentVersionIcon.visibility = visibility

                if (item.isAdapt) {
                    val versionName = mCurrentVersion?.getVersionName()
                    isAdapt.apply {
                        text = versionName?.run {
                            mContext.getString(R.string.download_install_is_adapt, versionName)
                        }
                        isSelected = true
                    }
                }
            }
        }

        fun clearAnimatedLogos() {
            AnimatedLogo.clear(binding.modloaderIcon)
            AnimatedLogo.clear(binding.currentVersionIcon)
        }

        /**
         * @return icon resource and name of the mod loader, or the vanilla grass block and an empty string
         */
        private fun getModLoaderInfo(modLoader: ModLoader?): Pair<Int, String?> {
            return when (modLoader) {
                ModLoader.FORGE -> Pair(R.drawable.ic_anvil, ModLoader.FORGE.loaderName)
                ModLoader.NEOFORGE -> Pair(R.drawable.ic_neoforge, ModLoader.NEOFORGE.loaderName)
                ModLoader.FABRIC -> Pair(R.drawable.ic_fabric, ModLoader.FABRIC.loaderName)
                ModLoader.QUILT -> Pair(R.drawable.ic_quilt, ModLoader.QUILT.loaderName)
                else -> Pair(R.drawable.ic_minecraft, null)
            }
        }
    }
}
