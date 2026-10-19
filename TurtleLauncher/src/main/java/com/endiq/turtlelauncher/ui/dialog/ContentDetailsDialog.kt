package com.endiq.turtlelauncher.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.download.ScreenshotAdapter
import com.endiq.turtlelauncher.feature.download.platform.modrinth.ModrinthHelper
import com.endiq.turtlelauncher.task.TaskExecutors
import com.endiq.turtlelauncher.ui.subassembly.aichat.ChatMarkdown
import net.endiq.launcher.modloaders.modpacks.api.ApiHandler
import java.net.URLEncoder
import java.util.concurrent.Future

/** Re-creatable detail pages; versions/installation remain in DownloadModFragment. */
class ContentDetailsDialog : DialogFragment() {
    private var task: Future<*>? = null
    private lateinit var body: LinearLayout

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        return AlertDialog.Builder(context).setTitle(requireArguments().getString("title"))
            .setView(ScrollView(context).apply { addView(body) })
            .setPositiveButton(android.R.string.ok, null).create()
    }

    override fun onStart() {
        super.onStart()
        load()
    }

    private fun text(value: CharSequence) {
        body.addView(TextView(requireContext()).apply {
            text = value; setTextIsSelectable(true)
            movementMethod = LinkMovementMethod.getInstance()
            setPadding(0, 8, 0, 16)
        })
    }

    private fun load() {
        task?.cancel(true)
        body.removeAllViews()
        body.addView(com.endiq.turtlelauncher.ui.miuix.MiuixProgressBar(requireContext()))
        val id = URLEncoder.encode(requireArguments().getString("id")!!, "UTF-8")
        val page = requireArguments().getString("page")!!
        task = TaskExecutors.getDefault().submit {
            val result = runCatching {
                val api = ApiHandler("https://api.modrinth.com/v2")
                when (page) {
                    "gallery" -> ModrinthHelper().getScreenshots(id)
                    "changelog" -> api.get("project/$id/version", JsonArray::class.java)
                        ?: error("No response from Modrinth")
                    else -> api.get("project/$id", JsonObject::class.java)
                        ?.get("body")?.asString ?: error("No response from Modrinth")
                }
            }
            TaskExecutors.runInUIThread {
                if (!isAdded || dialog?.isShowing != true) return@runInUIThread
                body.removeAllViews()
                result.onSuccess { data ->
                    when (page) {
                        "gallery" -> {
                            @Suppress("UNCHECKED_CAST")
                            val images = data as List<com.endiq.turtlelauncher.feature.download.item.ScreenshotItem>
                            if (images.isEmpty()) text(getString(R.string.content_details_empty))
                            else body.addView(RecyclerView(requireContext()).apply {
                                layoutManager = LinearLayoutManager(requireContext())
                                adapter = ScreenshotAdapter(images)
                                isNestedScrollingEnabled = false
                            })
                        }
                        "changelog" -> {
                            val versions = data as JsonArray
                            if (versions.size() == 0) text(getString(R.string.content_details_empty))
                            if (versions.size() > 0) {
                                val changelog = TextView(requireContext()).apply {
                                    setTextIsSelectable(true)
                                    movementMethod = LinkMovementMethod.getInstance()
                                }
                                body.addView(Spinner(requireContext()).apply {
                                    adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                                        versions.map { version ->
                                            version.asJsonObject.get("name")?.asString.orEmpty() + " · " +
                                                version.asJsonObject.get("date_published")?.asString.orEmpty()
                                        })
                                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                                        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                                        override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                                            val log = versions[position].asJsonObject.get("changelog")
                                                ?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                                            changelog.text = ChatMarkdown.render(log.ifBlank { getString(R.string.content_details_empty) }, requireContext())
                                        }
                                    }
                                })
                                body.addView(changelog)
                            }
                        }
                        else -> text(ChatMarkdown.render((data as String).ifBlank { getString(R.string.content_details_empty) }, requireContext()))
                    }
                }.onFailure { error ->
                    text(getString(R.string.content_details_failed, error.message))
                    body.addView(Button(requireContext()).apply {
                        setText(R.string.content_details_retry); setOnClickListener { load() }
                    })
                }
            }
        }
    }

    override fun onDestroyView() {
        task?.cancel(true)
        task = null
        super.onDestroyView()
    }

    companion object {
        fun create(id: String, title: String, page: String) = ContentDetailsDialog().apply {
            arguments = Bundle().apply { putString("id", id); putString("title", title); putString("page", page) }
        }
    }
}
