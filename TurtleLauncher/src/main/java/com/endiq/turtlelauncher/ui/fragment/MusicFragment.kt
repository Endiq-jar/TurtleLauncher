package com.endiq.turtlelauncher.ui.fragment
import com.endiq.turtlelauncher.utils.anim.TurtleTransitions

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.endiq.anim.AnimPlayer
import com.endiq.anim.animations.Animations
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.databinding.FragmentMusicBinding
import com.endiq.turtlelauncher.databinding.ItemMusicTrackBinding
import com.endiq.turtlelauncher.feature.music.MusicManager
import com.endiq.turtlelauncher.feature.music.MusicTrack
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.dialog.EditTextDialog
import com.endiq.turtlelauncher.utils.ZHTools
import java.util.concurrent.TimeUnit

/**
 * Music screen - enable/scope settings on the left, playlist on the right. No left nav
 * sidebar or extra top bar here beyond a title + return, matching how other full-screen
 * destinations in this app (AccountFragment, CustomMouseFragment, etc.) are laid out.
 */
class MusicFragment : FragmentWithAnim(R.layout.fragment_music) {
    companion object {
        const val TAG: String = "MusicFragment"
    }

    private lateinit var binding: FragmentMusicBinding
    private lateinit var openAudioLauncher: ActivityResultLauncher<Array<String>>
    private var adapter: TrackAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openAudioLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { addLocalTrack(it) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMusicBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.returnButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }

        binding.musicEnableSwitch.isChecked = AllSettings.musicEnabled.getValue()
        binding.musicEnableSwitch.setOnCheckedChangeListener { _, checked ->
            AllSettings.musicEnabled.put(checked).save()
        }

        val scopeButton = when (MusicManager.getScope()) {
            MusicManager.Scope.FULL_GAME -> binding.scopeFullGame
            MusicManager.Scope.ONLY_STARTUP -> binding.scopeOnlyStartup
            MusicManager.Scope.ONLY_LAUNCHER -> binding.scopeOnlyLauncher
        }
        scopeButton.isChecked = true
        binding.musicScopeGroup.setOnCheckedChangeListener { _, checkedId ->
            val scope = when (checkedId) {
                binding.scopeFullGame.id -> MusicManager.Scope.FULL_GAME
                binding.scopeOnlyStartup.id -> MusicManager.Scope.ONLY_STARTUP
                else -> MusicManager.Scope.ONLY_LAUNCHER
            }
            MusicManager.setScope(scope)
        }

        binding.musicVolumeSeekbar.progress = AllSettings.musicVolume.getValue()
        binding.musicVolumeValue.text = AllSettings.musicVolume.getValue().toString()
        binding.musicVolumeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.musicVolumeValue.text = progress.toString()
                if (fromUser) MusicManager.setVolume(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.musicPlayPreview.setOnClickListener {
            val track = MusicManager.getSelectedTrack()
            if (track == null) {
                Toast.makeText(requireContext(), R.string.music_no_track_selected, Toast.LENGTH_SHORT).show()
            } else {
                MusicManager.playPreview(track)
            }
        }
        binding.musicStopPreview.setOnClickListener { MusicManager.stop() }

        binding.musicSelectFile.setOnClickListener { openAudioLauncher.launch(arrayOf("audio/*")) }
        binding.musicAddUrl.setOnClickListener { showAddUrlDialog() }

        adapter = TrackAdapter()
        binding.musicPlaylistRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.musicPlaylistRecycler.adapter = adapter

        refreshPlaylist()
        refreshCurrentTrackLabel()
    }

    private fun addLocalTrack(uri: Uri) {
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val fallbackName = uri.lastPathSegment?.substringAfterLast('/') ?: "Local Track"
        val track = MusicManager.probeLocalTrack(uri.toString(), fallbackName)
        MusicManager.addTrack(track)
        refreshPlaylist()
        refreshCurrentTrackLabel()
    }

    private fun showAddUrlDialog() {
        EditTextDialog.Builder(requireActivity())
            .setTitle(R.string.music_add_url_title)
            .setHintText(R.string.music_add_url_hint)
            .setConfirmListener { editText, _ ->
                val url = editText.text.toString().trim()
                if (url.isBlank()) return@setConfirmListener false
                if (isKnownStreamingLink(url)) {
                    Toast.makeText(requireContext(), R.string.music_add_url_not_direct, Toast.LENGTH_LONG).show()
                    return@setConfirmListener false
                }
                val title = url.substringAfterLast('/').substringBefore('?').ifBlank { "Remote Track" }
                MusicManager.addTrack(MusicTrack(title = title, artist = url, durationMs = 0L, source = url, isRemote = true))
                refreshPlaylist()
                refreshCurrentTrackLabel()
                true
            }
            .showDialog()
    }

    /**
     * Catches the links this feature can't actually play - YouTube/Spotify/YT Music
     * don't hand out a raw audio stream URL to third-party apps; that needs their own
     * official SDK/API (Spotify App Remote, YouTube Data API), not a pasted link.
     */
    private fun isKnownStreamingLink(url: String): Boolean {
        val lower = url.lowercase()
        return listOf("youtube.com", "youtu.be", "spotify.com", "music.youtube.com").any { lower.contains(it) }
    }

    private fun refreshPlaylist() {
        val tracks = MusicManager.getPlaylist()
        adapter?.submit(tracks)
        binding.musicEmptyHint.visibility = if (tracks.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshCurrentTrackLabel() {
        val track = MusicManager.getSelectedTrack()
        binding.musicCurrentTrackName.text = track?.title ?: getString(R.string.music_no_track_selected)
        binding.musicCurrentTrackDuration.text = track?.let { formatDuration(it.durationMs) } ?: "--:--"
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "--:--"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    private inner class TrackAdapter : RecyclerView.Adapter<TrackAdapter.Holder>() {
        private val items = mutableListOf<MusicTrack>()

        fun submit(newItems: List<MusicTrack>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemMusicTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(binding)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val track = items[position]
            val selected = track.id == AllSettings.musicSelectedTrackId.getValue()
            holder.binding.trackTitle.text = track.title
            holder.binding.trackArtist.text = track.artist
            holder.binding.trackDuration.text = formatDuration(track.durationMs)
            holder.binding.trackSelect.isChecked = selected
            holder.binding.trackSelect.setOnClickListener {
                MusicManager.setSelectedTrack(track.id)
                refreshCurrentTrackLabel()
                notifyDataSetChanged()
            }
            holder.binding.root.setOnClickListener { holder.binding.trackSelect.performClick() }
            holder.binding.trackDelete.setOnClickListener {
                MusicManager.removeTrack(track.id)
                refreshPlaylist()
                refreshCurrentTrackLabel()
            }
        }

        override fun getItemCount(): Int = items.size

        inner class Holder(val binding: ItemMusicTrackBinding) : RecyclerView.ViewHolder(binding.root)
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.musicTitle, TurtleTransitions.enter()))
            .apply(AnimPlayer.Entry(binding.playlistCard, TurtleTransitions.enter()))
    }

    override fun slideOut(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.musicTitle, TurtleTransitions.exit()))
            .apply(AnimPlayer.Entry(binding.playlistCard, TurtleTransitions.exit()))
    }
}
