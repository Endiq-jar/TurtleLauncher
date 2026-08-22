package net.kdt.pojavlaunch.fragments;

import static com.movtery.zalithlauncher.event.single.RefreshVersionsEvent.MODE.END;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.movtery.anim.AnimPlayer;
import com.movtery.anim.animations.Animations;
import com.movtery.zalithlauncher.InfoCenter;
import com.movtery.zalithlauncher.R;
import com.movtery.zalithlauncher.databinding.FragmentLauncherBinding;
import com.movtery.zalithlauncher.event.single.AccountUpdateEvent;
import com.movtery.zalithlauncher.event.single.LaunchGameEvent;
import com.movtery.zalithlauncher.event.single.RefreshVersionsEvent;
import com.movtery.zalithlauncher.event.value.InstallLocalModpackEvent;
import com.movtery.zalithlauncher.feature.mod.modpack.install.InstallExtra;
import com.movtery.zalithlauncher.feature.log.CrashAnalyzer;
import com.movtery.zalithlauncher.feature.turtle.DailyPlaytimeStats;
import com.movtery.zalithlauncher.feature.turtle.HomeChangelog;
import com.movtery.zalithlauncher.feature.version.Version;
import com.movtery.zalithlauncher.feature.version.utils.VersionIconUtils;
import com.movtery.zalithlauncher.feature.version.VersionInfo;
import com.movtery.zalithlauncher.feature.version.VersionsManager;
import com.movtery.zalithlauncher.task.TaskExecutors;
import com.movtery.zalithlauncher.ui.fragment.AboutFragment;
import com.movtery.zalithlauncher.ui.fragment.ControlButtonFragment;
import com.movtery.zalithlauncher.ui.fragment.FilesFragment;
import com.movtery.zalithlauncher.ui.fragment.FragmentWithAnim;
import com.movtery.zalithlauncher.ui.fragment.LogViewerFragment;
import com.movtery.zalithlauncher.ui.fragment.VersionManagerFragment;
import com.movtery.zalithlauncher.ui.fragment.VersionsListFragment;
import com.movtery.zalithlauncher.ui.subassembly.account.AccountViewWrapper;
import com.movtery.zalithlauncher.utils.file.FileTools;
import com.movtery.zalithlauncher.utils.path.PathManager;
import com.movtery.zalithlauncher.utils.ZHTools;
import com.movtery.zalithlauncher.utils.anim.ViewAnimUtils;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;

public class MainMenuFragment extends FragmentWithAnim {
    public static final String TAG = "MainMenuFragment";
    private FragmentLauncherBinding binding;
    private AccountViewWrapper accountViewWrapper;
    private ActivityResultLauncher<Object> modpackImportLauncher;

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // TurtleLauncher: Modpack Importer (see quick_actions_rail's modpack_import_button).
        // OpenDocumentWithExtension(null) falls back to "*/*" since neither .mrpack nor a
        // generic-ZIP modpack has a MIME type Android's database would recognize anyway -
        // ModPackUtils.determineModpack() sniffs the actual content after picking, same as
        // every other entry point into this install pipeline.
        modpackImportLauncher = registerForActivityResult(new OpenDocumentWithExtension(null), uris -> {
            if (uris == null || uris.isEmpty()) return;
            importModpackFromUri(uris.get(0));
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLauncherBinding.inflate(getLayoutInflater());
        accountViewWrapper = new AccountViewWrapper(this, binding.viewAccount);
        accountViewWrapper.refreshAccountInfo();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.aboutText.setText(InfoCenter.replaceName(requireActivity(), R.string.about_tab));
        binding.aboutButton.setOnClickListener(v -> ZHTools.swapFragmentWithAnim(this, AboutFragment.class, AboutFragment.TAG, null));
        binding.customControlButton.setOnClickListener(v -> ZHTools.swapFragmentWithAnim(this, ControlButtonFragment.class, ControlButtonFragment.TAG, null));
        binding.installJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
        binding.installJarButton.setOnLongClickListener(v -> {
            runInstallerWithConfirmation(true);
            return true;
        });
        binding.shareLogsButton.setOnClickListener(v -> ZHTools.swapFragmentWithAnim(this, com.movtery.zalithlauncher.ui.fragment.ShareLogsFragment.class, com.movtery.zalithlauncher.ui.fragment.ShareLogsFragment.TAG, null));
        binding.modpackImportButton.setOnClickListener(v -> {
            if (ProgressKeeper.getTaskCount() == 0) {
                modpackImportLauncher.launch(null);
            } else {
                Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            }
        });
        // Footer: launcher name, version string, and GitHub link
        binding.footerAppName.setText(com.movtery.zalithlauncher.InfoDistributor.LAUNCHER_NAME);
        binding.footerVersionText.setText("v" + com.movtery.zalithlauncher.BuildConfig.VERSION_NAME);
        binding.footerGithubButton.setOnClickListener(v ->
            ZHTools.openLink(requireActivity(), com.movtery.zalithlauncher.utils.path.UrlManager.URL_HOME));

        binding.version.setOnClickListener(v -> {
            if (!isTaskRunning()) {
                ZHTools.swapFragmentWithAnim(this, VersionsListFragment.class, VersionsListFragment.TAG, null);
            } else {
                ViewAnimUtils.setViewAnim(binding.version, Animations.Shake);
                TaskExecutors.runInUIThread(() -> Toast.makeText(requireContext(), R.string.version_manager_task_in_progress, Toast.LENGTH_SHORT).show());
            }
        });
        binding.managerProfileButton.setOnClickListener(v -> {
            if (!isTaskRunning()) {
                ViewAnimUtils.setViewAnim(binding.managerProfileButton, Animations.Pulse);
                ZHTools.swapFragmentWithAnim(this, VersionManagerFragment.class, VersionManagerFragment.TAG, null);
            } else {
                ViewAnimUtils.setViewAnim(binding.managerProfileButton, Animations.Shake);
                TaskExecutors.runInUIThread(() -> Toast.makeText(requireContext(), R.string.version_manager_task_in_progress, Toast.LENGTH_SHORT).show());
            }
        });

        binding.playButton.setOnClickListener(v -> EventBus.getDefault().post(new LaunchGameEvent()));

        // Community links
        binding.linkDiscordButton.setOnClickListener(v -> openUrl("https://discord.gg/8TfuMhM8tD"));
        binding.linkWebsiteButton.setOnClickListener(v -> openUrl("https://endiq-jar.github.io/endiq-shop/"));
        binding.linkYoutubeButton.setOnClickListener(v -> openUrl("https://youtube.com/@endiq-jar?si=9sb9OnKDJG2kUnO1"));

        // Nav buttons in left panel
        binding.homeButton.setOnClickListener(v -> {
            ViewAnimUtils.setViewAnim(binding.homeButton, Animations.Pulse);
            Tools.backToMainMenu(requireActivity());
        });
        binding.storageButton.setOnClickListener(v -> {
            ViewAnimUtils.setViewAnim(binding.storageButton, Animations.Pulse);
            Bundle bundle = new Bundle();
            bundle.putString(FilesFragment.BUNDLE_LIST_PATH, com.movtery.zalithlauncher.utils.path.PathManager.DIR_GAME_HOME);
            ZHTools.swapFragmentWithAnim(this, FilesFragment.class, FilesFragment.TAG, bundle);
        });
        binding.downloadButton.setOnClickListener(v -> {
            ViewAnimUtils.setViewAnim(binding.downloadButton, Animations.Pulse);
            ZHTools.swapFragmentWithAnim(this,
                com.movtery.zalithlauncher.ui.fragment.DownloadFragment.class,
                com.movtery.zalithlauncher.ui.fragment.DownloadFragment.TAG, null);
        });
        binding.settingButton.setOnClickListener(v -> {
            ViewAnimUtils.setViewAnim(binding.settingButton, Animations.Pulse);
            ZHTools.swapFragmentWithAnim(this,
                com.movtery.zalithlauncher.ui.fragment.settings.SettingsFragment.class,
                com.movtery.zalithlauncher.ui.fragment.settings.SettingsFragment.TAG, null);
        });

        binding.versionName.setSelected(true);
        binding.versionInfo.setSelected(true);

        // Top app bar: title/subtitle + quick-action icon row
        binding.homeTopBarTitle.setText(com.movtery.zalithlauncher.InfoDistributor.LAUNCHER_NAME);
        binding.topBarStorageButton.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(FilesFragment.BUNDLE_LIST_PATH, PathManager.DIR_GAME_HOME);
            ZHTools.swapFragmentWithAnim(this, FilesFragment.class, FilesFragment.TAG, bundle);
        });
        binding.topBarDownloadButton.setOnClickListener(v -> ZHTools.swapFragmentWithAnim(this,
            com.movtery.zalithlauncher.ui.fragment.DownloadFragment.class,
            com.movtery.zalithlauncher.ui.fragment.DownloadFragment.TAG, null));
        // TurtleLauncher: top-bar shortcut straight to the cursor manager/editor - previously
        // only reachable via Settings -> Control Settings -> Custom Mouse, several taps deep.
        binding.topBarCursorButton.setOnClickListener(v -> ZHTools.swapFragmentWithAnim(this,
            com.movtery.zalithlauncher.ui.fragment.CustomMouseFragment.class,
            com.movtery.zalithlauncher.ui.fragment.CustomMouseFragment.TAG, null));
        binding.topBarSettingsButton.setOnClickListener(v -> ZHTools.swapFragmentWithAnim(this,
            com.movtery.zalithlauncher.ui.fragment.settings.SettingsFragment.class,
            com.movtery.zalithlauncher.ui.fragment.settings.SettingsFragment.TAG, null));

        // Today's Statistics dashboard
        binding.changelogTitle.setText(HomeChangelog.getTitle());
        binding.changelogSummary.setText(HomeChangelog.getSummary());
        binding.changelogCard.setOnClickListener(v ->
            ZHTools.openLink(requireActivity(), com.movtery.zalithlauncher.utils.path.UrlManager.URL_HOME));
        refreshStatistics();
        refreshLastGameLog();

        populateFeaturePlugins();
        refreshCurrentVersion();
    }

    /** Populates the weekly playtime chart and today's total from {@link DailyPlaytimeStats}. */
    private void refreshStatistics() {
        long[] weekMs = DailyPlaytimeStats.getThisWeekMs();
        float[] weekHours = new float[weekMs.length];
        for (int i = 0; i < weekMs.length; i++) weekHours[i] = weekMs[i] / 3600000f;
        binding.statsChart.setData(weekHours);
    }

    /**
     * Populates the "Last Game Log" card from {@link CrashAnalyzer}'s own tracking of the
     * most recent game session, and wires it to open the full log in {@link LogViewerFragment}
     * when one is actually available.
     */
    private void refreshLastGameLog() {
        String lastLogText = CrashAnalyzer.INSTANCE.getLastLogText();
        if (lastLogText == null || lastLogText.isEmpty()) {
            binding.lastLogPreview.setText(R.string.main_last_log_none);
            binding.lastLogCard.setOnClickListener(null);
            return;
        }

        String[] lines = lastLogText.split("\n");
        String preview = lines.length > 0 ? lines[lines.length - 1] : lastLogText;
        binding.lastLogPreview.setText(preview.trim());

        binding.lastLogCard.setOnClickListener(v -> {
            File logFile = new File(PathManager.DIR_GAME_HOME, "latestlog.txt");
            if (logFile.isFile()) {
                ZHTools.swapFragmentWithAnim(this, LogViewerFragment.class, LogViewerFragment.TAG,
                    LogViewerFragment.Companion.createArgs(logFile));
            }
        });
    }

    /**
     * Adds one Quick Actions row per discovered {@link com.movtery.zalithlauncher.plugins.feature.FeaturePlugin}
     * (see {@link com.movtery.zalithlauncher.plugins.feature.FeaturePluginManager} for the discovery
     * contract). This is the whole point of the feature-plugin architecture: a new launcher feature
     * shipped as a separate installed app shows up here automatically, without this Fragment/the
     * launcher APK needing to change at all.
     */
    private void populateFeaturePlugins() {
        java.util.List<com.movtery.zalithlauncher.plugins.feature.FeaturePlugin> plugins =
                com.movtery.zalithlauncher.plugins.feature.FeaturePluginManager.getFeaturePluginList();
        binding.featurePluginsContainer.removeAllViews();
        if (plugins.isEmpty()) {
            binding.featurePluginsContainer.setVisibility(View.GONE);
            return;
        }
        binding.featurePluginsContainer.setVisibility(View.VISIBLE);
        android.content.pm.PackageManager packageManager = requireContext().getPackageManager();
        for (com.movtery.zalithlauncher.plugins.feature.FeaturePlugin plugin : plugins) {
            View row = getLayoutInflater().inflate(R.layout.item_feature_plugin, binding.featurePluginsContainer, false);
            android.widget.ImageView icon = row.findViewById(R.id.feature_plugin_icon);
            android.widget.TextView title = row.findViewById(R.id.feature_plugin_title);
            android.widget.TextView desc = row.findViewById(R.id.feature_plugin_desc);

            title.setText(plugin.getDisplayName());
            if (plugin.getDescription().isEmpty()) {
                desc.setVisibility(View.GONE);
            } else {
                desc.setText(plugin.getDescription());
            }
            try {
                icon.setImageDrawable(packageManager.getApplicationIcon(plugin.getApplicationInfo()));
            } catch (Exception ignored) {
                icon.setImageResource(R.drawable.ic_puzzle_piece);
            }

            row.setOnClickListener(v -> {
                Intent launchIntent = packageManager.getLaunchIntentForPackage(plugin.getPackageName());
                if (launchIntent != null) {
                    try {
                        startActivity(launchIntent);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), plugin.getPackageName(), Toast.LENGTH_LONG).show();
                    }
                }
            });

            binding.featurePluginsContainer.addView(row);
        }
    }

    private void refreshCurrentVersion() {
        Version version = VersionsManager.INSTANCE.getCurrentVersion();

        int versionInfoVisibility;
        if (version != null) {
            binding.versionName.setText(version.getVersionName());
            VersionInfo versionInfo = version.getVersionInfo();
            if (versionInfo != null) {
                binding.versionInfo.setText(versionInfo.getInfoString());
                versionInfoVisibility = View.VISIBLE;
            } else versionInfoVisibility = View.GONE;

            new VersionIconUtils(version).start(binding.versionIcon);
            binding.managerProfileButton.setVisibility(View.VISIBLE);
        } else {
            binding.versionName.setText(R.string.version_no_versions);
            binding.managerProfileButton.setVisibility(View.GONE);
            versionInfoVisibility = View.GONE;
        }
        binding.versionInfo.setVisibility(versionInfoVisibility);
    }

    @Subscribe()
    public void event(RefreshVersionsEvent event) {
        if (event.getMode() == END) {
            TaskExecutors.runInUIThread(this::refreshCurrentVersion);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void event(AccountUpdateEvent event) {
        if (accountViewWrapper != null) accountViewWrapper.refreshAccountInfo();
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), url, Toast.LENGTH_LONG).show();
        }
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }

    /**
     * TurtleLauncher: Modpack Importer. Copies whatever was picked (a content:// URI, since
     * it came from the system file picker) into DIR_CACHE as a real file - everything past
     * this point (format detection, name prompt, download, loader install) is the exact same
     * pipeline ModPackDownloadFragment already uses for modpacks downloaded in-app, just
     * triggered by an EventBus event instead of a direct call. See LauncherActivity's
     * InstallLocalModpackEvent subscriber and ModPackUtils.determineModpack for what happens
     * next - format is sniffed from content, not the picked file's name, so it doesn't matter
     * what extension (if any) the person's file manager shows it with.
     */
    private void importModpackFromUri(Uri uri) {
        TaskExecutors.getDefault().execute(() -> {
            try {
                java.io.File copiedFile = FileTools.copyFileInBackground(requireContext(), uri, PathManager.DIR_CACHE_STRING);
                EventBus.getDefault().post(new InstallLocalModpackEvent(new InstallExtra(true, copiedFile.getAbsolutePath())));
            } catch (Exception e) {
                TaskExecutors.runInUIThread(() ->
                    Toast.makeText(requireContext(), R.string.modpack_install_download_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    public void slideIn(AnimPlayer animPlayer) {
        animPlayer.apply(new AnimPlayer.Entry(binding.launcherMenu, Animations.BounceInDown))
                .apply(new AnimPlayer.Entry(binding.playLayout, Animations.BounceInLeft))
                .apply(new AnimPlayer.Entry(binding.playButtonsLayout, Animations.BounceEnlarge));
    }

    @Override
    public void slideOut(AnimPlayer animPlayer) {
        animPlayer.apply(new AnimPlayer.Entry(binding.launcherMenu, Animations.FadeOutUp))
                .apply(new AnimPlayer.Entry(binding.playLayout, Animations.FadeOutRight))
                .apply(new AnimPlayer.Entry(binding.playButtonsLayout, Animations.BounceShrink));
    }
}
