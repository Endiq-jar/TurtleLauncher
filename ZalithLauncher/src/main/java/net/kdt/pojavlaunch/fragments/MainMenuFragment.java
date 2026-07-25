package net.kdt.pojavlaunch.fragments;

import static com.movtery.zalithlauncher.event.single.RefreshVersionsEvent.MODE.END;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

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
import com.movtery.zalithlauncher.feature.version.Version;
import com.movtery.zalithlauncher.feature.version.utils.VersionIconUtils;
import com.movtery.zalithlauncher.feature.version.VersionInfo;
import com.movtery.zalithlauncher.feature.version.VersionsManager;
import com.movtery.zalithlauncher.task.TaskExecutors;
import com.movtery.zalithlauncher.ui.fragment.AboutFragment;
import com.movtery.zalithlauncher.ui.fragment.ControlButtonFragment;
import com.movtery.zalithlauncher.ui.fragment.FilesFragment;
import com.movtery.zalithlauncher.ui.fragment.FragmentWithAnim;
import com.movtery.zalithlauncher.ui.fragment.VersionManagerFragment;
import com.movtery.zalithlauncher.ui.fragment.VersionsListFragment;
import com.movtery.zalithlauncher.ui.subassembly.account.AccountViewWrapper;
import com.movtery.zalithlauncher.utils.path.PathManager;
import com.movtery.zalithlauncher.utils.ZHTools;
import com.movtery.zalithlauncher.utils.anim.ViewAnimUtils;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class MainMenuFragment extends FragmentWithAnim {
    public static final String TAG = "MainMenuFragment";
    private FragmentLauncherBinding binding;
    private AccountViewWrapper accountViewWrapper;

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
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

        // Footer: launcher name, version string, and GitHub link
        binding.footerAppName.setText(com.movtery.zalithlauncher.InfoDistributor.LAUNCHER_NAME);
        binding.footerVersionText.setText("v" + com.movtery.zalithlauncher.BuildConfig.VERSION_NAME);
        binding.footerGithubButton.setOnClickListener(v ->
            ZHTools.openLink(requireActivity(), com.movtery.zalithlauncher.utils.path.UrlManager.URL_HOME));

        // Install version widget button
        if (binding.installVersionButton != null) {
            binding.installVersionButton.setOnClickListener(v -> {
                ZHTools.swapFragmentWithAnim(this,
                    com.movtery.zalithlauncher.ui.fragment.VersionsListFragment.class,
                    com.movtery.zalithlauncher.ui.fragment.VersionsListFragment.TAG, null);
            });
        }

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

        refreshCurrentVersion();
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
