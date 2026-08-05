package com.movtery.zalithlauncher.ui.fragment.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.FragmentRendererManagerBinding
import com.movtery.zalithlauncher.feature.download.RendererCardAdapter
import com.movtery.zalithlauncher.renderer.RendererCatalog
import com.movtery.zalithlauncher.renderer.Renderers
import com.movtery.zalithlauncher.renderer.renderers.HolyGL4ESRenderer
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.fragment.FragmentWithAnim
import com.movtery.zalithlauncher.utils.ZHTools
import net.kdt.pojavlaunch.Tools
import java.io.File

/**
 * Material-styled renderer picker: one card per renderer with a stability badge
 * (Recommended/Stable/Experimental) and a compatibility note - version range, missing
 * bundled library, or a Vulkan requirement - plus a checkmark on the current selection
 * and a reset-to-defaults action. Complements rather than replaces the existing quick-pick
 * list in Video settings; that one's still there for a fast switch, this one's for
 * actually deciding what to pick.
 */
class RendererManagerFragment : FragmentWithAnim(R.layout.fragment_renderer_manager) {
    companion object {
        const val TAG: String = "RendererManagerFragment"
    }

    private lateinit var binding: FragmentRendererManagerBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRendererManagerBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rendererGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        render()

        binding.rendererManagerReset.setOnClickListener {
            val defaultUuid = HolyGL4ESRenderer().getUniqueIdentifier()
            AllSettings.renderer.put(defaultUuid).save()
            Renderers.setCurrentRenderer(requireContext(), defaultUuid)
            render()
        }

        binding.rendererManagerReturn.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }
    }

    private fun render() {
        val context = requireContext()
        val compatibleRenderers = Renderers.getCompatibleRenderers(context).second
        val currentUuid = AllSettings.renderer.getValue()
        val hasVulkan = Tools.checkVulkanSupport(context.packageManager)

        val cards = compatibleRenderers.map { renderer ->
            val catalogEntry = RendererCatalog.get(renderer.getRendererId())
            val compatNote = buildCompatNote(renderer, catalogEntry, hasVulkan)
            RendererCardAdapter.CardEntry(
                uniqueIdentifier = renderer.getUniqueIdentifier(),
                name = renderer.getRendererName(),
                badge = catalogEntry?.badge ?: RendererCatalog.Badge.EXPERIMENTAL,
                compatNote = compatNote,
                isSelected = renderer.getUniqueIdentifier() == currentUuid
            )
        }

        binding.rendererGrid.adapter = RendererCardAdapter(cards) { card ->
            AllSettings.renderer.put(card.uniqueIdentifier).save()
            Renderers.setCurrentRenderer(context, card.uniqueIdentifier)
            render()
        }
    }

    /** Missing-library warning takes priority over version-range notes - a renderer that
     *  will crash on load is more urgent to know about than its recommended version range. */
    private fun buildCompatNote(
        renderer: com.movtery.zalithlauncher.renderer.RendererInterface,
        catalogEntry: RendererCatalog.Entry?,
        hasVulkan: Boolean
    ): String? {
        val libMissing = !File(com.movtery.zalithlauncher.utils.path.PathManager.DIR_NATIVE_LIB, renderer.getRendererLibrary()).exists()
        if (libMissing) return getString(R.string.renderer_compat_missing_library)

        if (renderer.getRendererId() == com.movtery.zalithlauncher.renderer.renderers.ZinkRenderer.ID && !hasVulkan) {
            return getString(R.string.renderer_compat_requires_vulkan)
        }

        catalogEntry?.maxMinecraftVersion?.let { return getString(R.string.renderer_compat_max_version, it) }
        catalogEntry?.minMinecraftVersion?.let { return getString(R.string.renderer_compat_min_version, it) }
        return null
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, Animations.BounceInDown))
    }

    override fun slideOut(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, Animations.FadeOutUp))
    }
}
