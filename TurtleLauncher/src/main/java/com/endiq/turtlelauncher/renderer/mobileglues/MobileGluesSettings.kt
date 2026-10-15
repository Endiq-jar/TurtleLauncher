package com.endiq.turtlelauncher.renderer.mobileglues

import android.content.Context
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.google.gson.JsonParser
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.utils.path.PathManager
import java.io.File

/** Uses the same config and wire values as PGW/TeamPojavLauncher, not launcher-only flags. */
object MobileGluesSettings {
    private val file get() = File(PathManager.DIR_FILE, "mobileglues/config.json")

    @Synchronized
    private fun readRoot() = if (file.isFile) JsonParser.parseString(file.readText()).asJsonObject
        else com.google.gson.JsonObject()

    /** Called before either MobileGlues-based renderer starts. Never enable a removed backend. */
    @JvmStatic
    @Synchronized
    fun prepareDirectory(): String {
        val root = readRoot()
        root.addProperty("enableANGLE", 2) // ForceDisable, including old externally edited configs.
        root.remove("enableAngle") // obsolete Turtle ANGLE-policy spelling
        write(root)
        return file.parentFile!!.absolutePath
    }

    private fun write(root: com.google.gson.JsonObject) {
        val target = file
        check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
        val temp = File(target.parentFile, "config.json.tmp")
        temp.writeText(root.toString())
        check(temp.renameTo(target)) { "Could not save MobileGlues configuration" }
    }

    fun show(context: Context) {
        try {
            showEditor(context)
        } catch (e: Exception) {
            AlertDialog.Builder(context).setTitle(R.string.mobileglues_settings)
                .setMessage(context.getString(R.string.mobileglues_config_error, e.message))
                .setPositiveButton(android.R.string.ok, null).show()
        }
    }

    private fun showEditor(context: Context) {
        val root = readRoot()
        var config = MGConfigCodec.decode(root).copy(angle = AngleConfig.ForceDisable)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        fun label(text: CharSequence) { column.addView(TextView(context).apply { this.text = text }) }
        fun <T : SpinnerOption> choice(title: String, values: List<T>, current: T, changed: (T) -> Unit) {
            label(title)
            column.addView(Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, values.map { it.label(context) })
                setSelection(values.indexOf(current).coerceAtLeast(0))
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { changed(values[position]) }
                }
            })
        }
        fun toggle(title: String, value: Boolean, changed: (Boolean) -> Unit) {
            column.addView(androidx.appcompat.widget.SwitchCompat(context).apply {
                text = title; isChecked = value
                setOnCheckedChangeListener { _, checked -> changed(checked) }
            })
        }
        label(context.getString(R.string.mobileglues_restart_notice))
        choice("Error checking", NoErrorConfig.entries, config.noError) { config = config.copy(noError = it) }
        choice("Reported OpenGL version", GlVersion.entries, config.glVersion) { config = config.copy(glVersion = it) }
        toggle("Timer query extension", config.extTimerQuery) { config = config.copy(extTimerQuery = it) }
        toggle("Compute shader extension", config.extComputeShader) { config = config.copy(extComputeShader = it) }
        toggle("Direct state access extension", config.extDirectStateAccess) { config = config.copy(extDirectStateAccess = it) }
        label("FSR1 quality (0 = disabled, 1 = ultra quality, 2 = quality, 3 = balanced, 4 = performance)")
        val fsr = EditText(context).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(config.fsr1.wire.toString()) }
        column.addView(fsr)
        label("GLSL cache limit in MiB (0 = disabled)")
        val cache = EditText(context).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(config.glslCache.mebibytesOrZero.toString()) }
        column.addView(cache)
        label("MultiDraw priority (comma-separated; native, multiindirect, multibasevertex, multiarrays, indirect, basevertex, unroll, compute)")
        val order = EditText(context).apply { setText(config.multidraw.globalOrder.joinToString(",") { it.key }) }
        column.addView(order)
        val exceptions = MultidrawEntry.entries.associateWith { entry ->
            label("${entry.glFunction} override (blank = global priority)")
            EditText(context).apply {
                setText(config.multidraw.exceptions[entry]?.joinToString(",") { it.key }.orEmpty())
                column.addView(this)
            }
        }
        val dialog = AlertDialog.Builder(context).setTitle(R.string.mobileglues_settings)
            .setView(ScrollView(context).apply { addView(column) })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.mobileglues_save, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                runCatching {
                    val cacheSize = cache.text.toString().toInt().also { require(it in 0..16384) }
                    val fsrValue = fsr.text.toString().toInt().also { require(it in 0..4) }
                    val parsed = order.text.toString().split(',').map { requireNotNull(MultidrawOrderItem.parse(it)) }
                    var multidraw = config.multidraw.withGlobalOrder(parsed)
                    exceptions.forEach { (entry, editor) ->
                        val raw = editor.text.toString().trim()
                        multidraw = multidraw.withException(entry, raw.isNotEmpty())
                        if (raw.isNotEmpty()) {
                            val backends = raw.split(',').map { requireNotNull(MultidrawBackend.parse(it)).also { backend -> require(backend in entry.implemented) } }
                            multidraw = multidraw.withExceptionOrder(entry, backends)
                        }
                    }
                    config = config.copy(fsr1 = Fsr1Preset.entries.first { it.wire == fsrValue },
                        glslCache = GlslCacheSize.ofMebibytes(cacheSize), multidraw = multidraw)
                    synchronized(this) {
                        // Re-read unknown keys so an external writer's additions are not lost.
                        write(MGConfigCodec.encode(config, MGConfigCodec.foreignKeysOf(readRoot())).apply { remove("enableAngle") })
                    }
                    dialog.dismiss()
                }.onFailure { Toast.makeText(context, R.string.mobileglues_invalid_setting, Toast.LENGTH_LONG).show() }
            }
        }
        dialog.show()
    }
}
