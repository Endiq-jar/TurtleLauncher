package com.endiq.turtlelauncher.ui.fragment
import com.endiq.turtlelauncher.utils.anim.TurtleTransitions

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.endiq.anim.AnimPlayer
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.databinding.FragmentLogoPickerBinding
import com.endiq.turtlelauncher.feature.LogoPackUnpacker
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.task.Task
import com.endiq.turtlelauncher.task.TaskExecutors
import com.endiq.turtlelauncher.ui.dialog.FilesDialog
import com.endiq.turtlelauncher.ui.dialog.FilesDialog.FilesButton
import com.endiq.turtlelauncher.ui.subassembly.filelist.FileIcon
import com.endiq.turtlelauncher.ui.subassembly.filelist.FileItemBean
import com.endiq.turtlelauncher.ui.subassembly.filelist.FileRecyclerViewCreator
import com.endiq.turtlelauncher.utils.path.PathManager
import com.endiq.turtlelauncher.utils.ZHTools
import com.endiq.turtlelauncher.utils.file.FileTools
import com.endiq.turtlelauncher.utils.file.FileTools.Companion.mkdirs
import com.endiq.turtlelauncher.utils.stringutils.StringUtils
import net.endiq.launcher.Tools
import java.io.File

/**
 * Lets the user pick the launcher's logo (shown on the splash screen) from a
 * pack of logo images - either imported one at a time via [openDocumentLauncher],
 * or bulk-dropped into [PathManager.DIR_CUSTOM_LOGO] ahead of time. Mirrors
 * [CustomMouseFragment]'s file-picker pattern, minus the drawing tool.
 */
class LogoPickerFragment : FragmentWithAnim(R.layout.fragment_logo_picker) {
    companion object {
        const val TAG: String = "LogoPickerFragment"
    }

    private lateinit var binding: FragmentLogoPickerBinding
    private lateinit var openDocumentLauncher: ActivityResultLauncher<Array<String>>
    private var fileRecyclerViewCreator: FileRecyclerViewCreator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openDocumentLauncher = registerForActivityResult<Array<String>, Uri>(ActivityResultContracts.OpenDocument()) { result: Uri? ->
            result?.let { uri ->
                val dialog = ZHTools.showTaskRunningDialog(requireContext())
                // Snapshot the context up front: requireActivity() from the worker
                // thread would crash if the user navigates away mid-copy.
                val appContext = requireContext().applicationContext
                val destPath = logoPath().absolutePath
                Task.runTask {
                    FileTools.copyFileInBackground(appContext, uri, destPath)
                }.ended(TaskExecutors.getAndroidUI()) {
                    if (!isAdded) return@ended
                    Toast.makeText(requireActivity(), getString(R.string.file_added), Toast.LENGTH_SHORT).show()
                    loadData()
                }.onThrowable { e ->
                    Tools.showErrorRemote(e)
                }.finallyTask(TaskExecutors.getAndroidUI()) {
                    dialog.dismiss()
                }.execute()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLogoPickerBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initViews()

        binding.actionBar.apply {
            returnButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }
            addFileButton.setOnClickListener { openDocumentLauncher.launch(arrayOf("image/*")) }
            refreshButton.setOnClickListener { loadData() }
        }

        ensurePackUnpacked()
    }

    /** Extracts the bundled 2000-icon pack into DIR_CUSTOM_LOGO on first open only. */
    private fun ensurePackUnpacked() {
        if (AllSettings.logoPackUnpacked.getValue()) {
            loadData()
            return
        }
        val dialog = ZHTools.showTaskRunningDialog(requireContext())
        val appContext = requireContext().applicationContext
        Task.runTask {
            LogoPackUnpacker.unpackIfNeeded(appContext)
        }.ended(TaskExecutors.getAndroidUI()) {
            if (!isAdded) return@ended
            loadData()
        }.onThrowable { e ->
            Tools.showErrorRemote(e)
        }.finallyTask(TaskExecutors.getAndroidUI()) {
            dialog.dismiss()
        }.execute()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun loadData() {
        val fileItemBeans = FileRecyclerViewCreator.loadItemBeansFromPath(
            requireActivity(),
            logoPath(),
            FileIcon.FILE,
            showFile = true,
            showFolder = false
        )
        fileItemBeans.add(0, FileItemBean(
            getString(R.string.custom_logo_default),
            ContextCompat.getDrawable(requireActivity(), R.drawable.ic_turtle_full)
        ))
        TaskExecutors.runInUIThread {
            fileRecyclerViewCreator?.loadData(fileItemBeans)
            // Show the currently selected logo by default.
            refreshIcon()
        }
    }

    private fun logoPath(): File {
        val path = File(PathManager.DIR_CUSTOM_LOGO)
        if (!path.exists()) mkdirs(path)
        return path
    }

    private fun refreshIcon() {
        binding.logoIcon.apply {
            ZHTools.getCustomLogo()?.let { file ->
                Glide.with(requireActivity())
                    .load(file)
                    .override(width, height)
                    .fitCenter()
                    .into(DrawableImageViewTarget(this))
                return@apply
            }
            setImageDrawable(ZHTools.customLogo(context))
        }
    }

    private fun initViews() {
        binding.actionBar.apply {
            addFileButton.setContentDescription(getString(R.string.custom_logo_add))
            searchButton.visibility = View.GONE
            pasteButton.visibility = View.GONE
            createFolderButton.visibility = View.GONE

            ZHTools.setTooltipText(
                returnButton,
                addFileButton,
                refreshButton
            )
        }

        fileRecyclerViewCreator = FileRecyclerViewCreator(requireActivity(), binding.recyclerView, { position: Int, fileItemBean: FileItemBean ->
                val file = fileItemBean.file
                val fileName = file?.name
                val isDefaultLogo = position == 0

                val filesButton = FilesButton()
                filesButton.setButtonVisibility(false, false,
                    !isDefaultLogo, !isDefaultLogo, !isDefaultLogo, isDefaultLogo) // the default logo cannot be shared, renamed or deleted

                var message = getString(R.string.file_message)
                if (isDefaultLogo) message += """
     
     ${getString(R.string.custom_logo_message_default)}
     """.trimIndent()
                filesButton.setMessageText(message)
                filesButton.setMoreButtonText(getString(R.string.generic_select))

                val filesDialog = FilesDialog(requireActivity(), filesButton, Task.runTask { loadData() }, logoPath(), file)
                filesDialog.setMoreButtonClick {
                    AllSettings.selectedLogo.put(fileName ?: "").save()
                    refreshIcon()
                    Toast.makeText(requireActivity(),
                        StringUtils.insertSpace(getString(R.string.custom_logo_added), (fileName ?: getString(R.string.custom_logo_default))),
                        Toast.LENGTH_SHORT).show()
                    filesDialog.dismiss()
                }
                filesDialog.show()
            },
            null
        )
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.logoLayout, TurtleTransitions.enter()))
            .apply(AnimPlayer.Entry(binding.operateLayout, TurtleTransitions.enter()))
    }

    override fun slideOut(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.logoLayout, TurtleTransitions.exit()))
            .apply(AnimPlayer.Entry(binding.operateLayout, TurtleTransitions.exit()))
    }
}
