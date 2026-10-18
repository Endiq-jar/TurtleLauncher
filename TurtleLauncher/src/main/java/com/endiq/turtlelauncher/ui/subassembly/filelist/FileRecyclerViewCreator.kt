package com.endiq.turtlelauncher.ui.subassembly.filelist

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.mod.ModUtils
import com.endiq.turtlelauncher.ui.subassembly.filelist.FileRecyclerAdapter.OnMultiSelectListener
import com.endiq.turtlelauncher.utils.stringutils.StringFilter.Companion.containsSubstring
import java.io.File
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import com.endiq.turtlelauncher.utils.anim.TurtleTransitions


class FileRecyclerViewCreator(
    context: Context?,
    recyclerView: RecyclerView,
    onItemClickListener: FileRecyclerAdapter.OnItemClickListener?,
    onItemLongClickListener: FileRecyclerAdapter.OnItemLongClickListener?,
    data: MutableList<FileItemBean> = ArrayList()
) {
    @JvmField
    val fileRecyclerAdapter: FileRecyclerAdapter = FileRecyclerAdapter()
    private val mainRecyclerView: RecyclerView
    private val itemBeans: MutableList<FileItemBean> = mutableListOf()
    private var filterString: String? = null

    init {
        fileRecyclerAdapter.setOnItemClickListener(onItemClickListener)
        fileRecyclerAdapter.setOnItemLongClickListener(onItemLongClickListener)

        this.mainRecyclerView = recyclerView

        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(context)
        context?.let { mainRecyclerView.layoutAnimation = TurtleTransitions.listLayoutAnimationController(it) }
        mainRecyclerView.layoutManager = layoutManager
        mainRecyclerView.adapter = this.fileRecyclerAdapter

        if (data.isNotEmpty()) loadData(data)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun loadData(itemBeans: List<FileItemBean>) {
        this.itemBeans.apply {
            clear()
            addAll(itemBeans)
        }
        updateWithFilter()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setFilterString(string: String?) {
        this.filterString = string
        updateWithFilter()
    }

    private fun updateWithFilter() {
        fileRecyclerAdapter.updateItems(
            filterString?.takeIf { it.isNotEmpty() }?.let { string ->
                itemBeans.filter {
                    it.name.contains(string, true)
                }
            } ?: itemBeans
        )
        mainRecyclerView.scheduleLayoutAnimation()
    }

    fun setOnMultiSelectListener(listener: OnMultiSelectListener?) {
        fileRecyclerAdapter.setOnMultiSelectListener(listener)
    }

    fun isNoFile(): Boolean {
        return fileRecyclerAdapter.isNoFile
    }

    companion object {
        fun loadItemBeansFromPath(context: Context, path: File, fileIcon: FileIcon,
            showFile: Boolean, showFolder: Boolean
        ): MutableList<FileItemBean> {
            return loadItemBeansFromPath(
                context,
                null,
                showSearchResultsOnly = false,
                caseSensitive = false,
                null,
                path,
                fileIcon,
                showFile,
                showFolder
            )
        }

        @JvmStatic
        @SuppressLint("UseCompatLoadingForDrawables")
        fun loadItemBeansFromPath(
            context: Context,
            filterString: String?,
            showSearchResultsOnly: Boolean,
            caseSensitive: Boolean,
            searchCount: AtomicInteger?,
            path: File,
            fileIcon: FileIcon,
            showFile: Boolean,
            showFolder: Boolean
        ): MutableList<FileItemBean> {
            val itemBeans: MutableList<FileItemBean> = ArrayList()
            val files = path.listFiles()
            if (files != null) {
                for (file in files) {
                    if (!showFileOrFolder(file, showFile, showFolder)) continue

                    val itemBean = FileItemBean(file)
                    if (!filterString.isNullOrEmpty()) {
                        if (containsSubstring(file.name, filterString, caseSensitive)) {
                            itemBean.isHighlighted = true
                            searchCount?.addAndGet(1)
                        } else if (showSearchResultsOnly) {
                            continue
                        }
                    }
                    val iconRes = iconResFor(file, fileIcon)
                    itemBean.fileIconRes = iconRes
                    itemBean.image = ContextCompat.getDrawable(context, iconRes)
                    itemBeans.add(itemBean)
                }
            }
            return itemBeans
        }

        private fun showFileOrFolder(file: File, showFile: Boolean, showFolder: Boolean): Boolean {
            // Show files and show folders.
            if (file.isDirectory && !showFolder) return false
            return !file.isFile || showFile
        }

        /** Icon resource for one browser row: mod jars get the pastel jar
         *  (disabled ones the ban sign), everything else goes through the
         *  extension-based pastel-green file-type family (FileTypeIcons). */
        private fun iconResFor(file: File, fileIcon: FileIcon): Int {
            if (file.isDirectory) return R.drawable.ic_filetype_folder
            if (fileIcon == FileIcon.MOD) {
                if (file.name.endsWith(ModUtils.DISABLE_JAR_FILE_SUFFIX)) return R.drawable.ic_disabled
                if (file.name.endsWith(ModUtils.JAR_FILE_SUFFIX)) return R.drawable.ic_filetype_jar
            }
            return FileTypeIcons.iconResFor(file)
        }

        @JvmStatic
        fun loadItemBean(drawable: Drawable, namesPair: Array<Pair<String, Date>>): List<FileItemBean> {
            return loadItemBean(drawable, 0, namesPair)
        }

        /** [animatedIconRes] rows render through AnimatedLogo (hover/click to animate). */
        @JvmStatic
        fun loadItemBean(drawable: Drawable, animatedIconRes: Int, namesPair: Array<Pair<String, Date>>): List<FileItemBean> {
            val itemBeans: MutableList<FileItemBean> = ArrayList()
            for (pair in namesPair) {
                val bean = FileItemBean(pair.first, pair.second, drawable)
                bean.animatedIconRes = animatedIconRes
                itemBeans.add(bean)
            }
            return itemBeans
        }
    }
}
