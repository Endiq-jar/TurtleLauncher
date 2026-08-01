package com.movtery.zalithlauncher.ui.dialog

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.DialogSkinCapeBinding
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.task.Task
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.ui.dialog.DraggableDialog.DialogInitializationListener
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.utils.DownloadUtils
import net.kdt.pojavlaunch.value.MinecraftAccount
import java.io.File
import java.io.FileOutputStream

/**
 * Dialog for changing skin or cape from URL or gallery pick.
 * @param mode "skin" or "cape"
 */
class SkinCapeDialog(
    private val activity: AppCompatActivity,
    private val account: MinecraftAccount,
    private val mode: String
) : FullScreenDialog(activity), DialogInitializationListener {

    private val binding = DialogSkinCapeBinding.inflate(LayoutInflater.from(activity))
    private var galleryLauncher: ActivityResultLauncher<Intent>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val titleRes = if (mode == "cape") R.string.skin_cape_change_cape else R.string.skin_cape_change_skin
        binding.title.setText(titleRes)

        galleryLauncher = activity.activityResultRegistry.register(
            "SkinCapeGallery_$mode",
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri -> applyFromUri(uri) }
            }
        }

        binding.buttonGallery.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "image/png"
            galleryLauncher?.launch(intent)
        }

        binding.buttonApplyUrl.setOnClickListener {
            val url = binding.urlEdit.text.toString().trim()
            if (url.isEmpty()) {
                binding.urlEdit.error = context.getString(R.string.generic_error_field_empty)
                return@setOnClickListener
            }
            applyFromUrl(url)
        }

        binding.buttonCancel.setOnClickListener { dismiss() }

        DraggableDialog.initDialog(this)
    }

    private fun applyFromUrl(url: String) {
        val destFile = getDestFile()
        showProgress(true)
        Task.runTask {
            destFile.parentFile?.mkdirs()
            DownloadUtils.downloadFile(url, destFile)
        }.ended(TaskExecutors.getAndroidUI()) {
            showProgress(false)
            notifySuccess()
            dismiss()
        }.onThrowable { e ->
            TaskExecutors.runInUIThread {
                showProgress(false)
                Toast.makeText(
                    context,
                    context.getString(R.string.skin_cape_apply_failed) + ": " + e.message,
                    Toast.LENGTH_LONG
                ).show()
                Logging.e("SkinCapeDialog", "Failed to download from URL", e)
            }
        }.execute()
    }

    private fun applyFromUri(uri: Uri) {
        val destFile = getDestFile()
        showProgress(true)
        Task.runTask {
            activity.contentResolver.openInputStream(uri)?.use { input ->
                destFile.parentFile?.mkdirs()
                FileOutputStream(destFile).use { out -> input.copyTo(out) }
            } ?: throw RuntimeException("Cannot open image")
        }.ended(TaskExecutors.getAndroidUI()) {
            showProgress(false)
            notifySuccess()
            dismiss()
        }.onThrowable { e ->
            TaskExecutors.runInUIThread {
                showProgress(false)
                Toast.makeText(
                    context,
                    context.getString(R.string.skin_cape_apply_failed) + ": " + e.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }.execute()
    }

    private fun getDestFile(): File {
        return if (mode == "cape") {
            File(PathManager.DIR_USER_SKIN, account.uniqueUUID + "_cape.png")
        } else {
            File(PathManager.DIR_USER_SKIN, account.uniqueUUID + ".png")
        }
    }

    private fun showProgress(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.buttonApplyUrl.isEnabled = !show
        binding.buttonGallery.isEnabled = !show
    }

    private fun notifySuccess() {
        val msgRes = if (mode == "cape") R.string.skin_cape_cape_applied else R.string.skin_cape_skin_applied
        Toast.makeText(context, msgRes, Toast.LENGTH_SHORT).show()
    }

    override fun onInit(): Window? = window
}
