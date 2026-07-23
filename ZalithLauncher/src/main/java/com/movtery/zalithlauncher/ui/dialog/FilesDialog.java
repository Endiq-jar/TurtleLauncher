package com.movtery.zalithlauncher.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.movtery.zalithlauncher.R;
import com.movtery.zalithlauncher.databinding.DialogOperationFileBinding;
import com.movtery.zalithlauncher.task.Task;
import com.movtery.zalithlauncher.utils.file.FileTools;
import com.movtery.zalithlauncher.utils.file.PasteFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FilesDialog extends FullScreenDialog implements DraggableDialog.DialogInitializationListener {

    private final DialogOperationFileBinding binding;
    private final FilesButton mFilesButton;
    private final Task<?> mEndTask;
    private final File mRoot;
    private final List<File> mSelectedFiles;
    private String mFileSuffix;
    private OnCopyButtonClickListener mCopyClick;
    private OnMoreButtonClickListener mMoreClick;
    private OnZipButtonClickListener mZipClick;
    private OnExtractButtonClickListener mExtractClick;
    private OnEditButtonClickListener mEditClick;

    public FilesDialog(@NonNull Context context, FilesButton filesButton, Task<?> endTask, File root, List<File> selectedFiles) {
        super(context);
        this.binding = DialogOperationFileBinding.inflate(getLayoutInflater());
        this.mFilesButton = filesButton;
        this.mEndTask = endTask;
        this.mRoot = root;
        this.mSelectedFiles = selectedFiles;
    }

    public FilesDialog(@NonNull Context context, FilesButton filesButton, Task<?> endTask, File root, File file) {
        super(context);
        this.binding = DialogOperationFileBinding.inflate(getLayoutInflater());
        this.mFilesButton = filesButton;
        this.mEndTask = endTask;
        this.mRoot = root;
        List<File> list = new ArrayList<>();
        list.add(file);
        this.mSelectedFiles = list;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
        handleButtons();
    }

    private void init() {
        this.setCancelable(true);
        this.setContentView(binding.getRoot());
        binding.closeButton.setOnClickListener(v -> dismiss());

        if (mFilesButton.more) {
            binding.moreView.setOnClickListener(v -> {
                if (mMoreClick != null) mMoreClick.onButtonClick();
                dismiss();
            });
        }

        DraggableDialog.initDialog(this);
    }

    private void handleButtons() {
        // Delete
        binding.deleteView.setOnClickListener(v -> {
            DeleteDialog deleteDialog = new DeleteDialog(getContext(), mEndTask, mSelectedFiles);
            deleteDialog.show();
            dismiss();
        });

        // Copy / Move
        PasteFile pasteFile = PasteFile.getInstance();
        binding.copyView.setOnClickListener(v -> {
            if (mCopyClick != null) {
                pasteFile.setPaste(mRoot, mSelectedFiles, PasteFile.PasteType.COPY);
                mCopyClick.onButtonClick();
            }
            dismiss();
        });
        binding.moveView.setOnClickListener(v -> {
            if (mCopyClick != null) {
                pasteFile.setPaste(mRoot, mSelectedFiles, PasteFile.PasteType.MOVE);
                mCopyClick.onButtonClick();
            }
            dismiss();
        });

        // Zip
        binding.zipView.setOnClickListener(v -> {
            if (mZipClick != null) mZipClick.onButtonClick(mSelectedFiles, mRoot);
            dismiss();
        });

        // Extract — only enabled for single .zip file
        boolean isZipFile = mSelectedFiles.size() == 1
                && mSelectedFiles.get(0).isFile()
                && mSelectedFiles.get(0).getName().toLowerCase().endsWith(".zip");
        setButtonClickable(mFilesButton.extract && isZipFile, binding.extractView);
        binding.extractView.setOnClickListener(v -> {
            if (mExtractClick != null) mExtractClick.onButtonClick(mSelectedFiles.get(0), mRoot);
            dismiss();
        });

        // Edit — open in the in-launcher text editor; single non-directory file only
        boolean isSingleFile = mSelectedFiles.size() == 1 && mSelectedFiles.get(0).isFile();
        setButtonClickable(mFilesButton.edit && isSingleFile, binding.editView);
        binding.editView.setOnClickListener(v -> {
            if (isSingleFile && mEditClick != null) mEditClick.onButtonClick(mSelectedFiles.get(0));
            dismiss();
        });

        // Share / Rename — single file only
        if (mSelectedFiles.size() == 1) {
            File file = mSelectedFiles.get(0);
            binding.shareView.setOnClickListener(v -> {
                FileTools.shareFile(getContext(), file);
                dismiss();
            });
            binding.renameView.setOnClickListener(v -> {
                if (file.isFile()) {
                    String suffix = mFileSuffix != null
                            ? mFileSuffix
                            : file.getName().substring(file.getName().lastIndexOf('.'));
                    FileTools.renameFileListener(getContext(), mEndTask, file, suffix);
                } else {
                    FileTools.renameFileListener(getContext(), mEndTask, file);
                }
                dismiss();
            });
            setButtonClickable(mFilesButton.share, binding.shareView);
            setButtonClickable(mFilesButton.rename, binding.renameView);
        } else {
            setButtonClickable(false, binding.shareView);
            setButtonClickable(false, binding.renameView);
        }

        setDialogTexts();

        setButtonClickable(mFilesButton.delete, binding.deleteView);
        setButtonClickable(mFilesButton.copy,   binding.copyView);
        setButtonClickable(mFilesButton.move,   binding.moveView);
        setButtonClickable(mFilesButton.more,   binding.moreView);
        setButtonClickable(mFilesButton.zip,    binding.zipView);
    }

    private void setDialogTexts() {
        if (mFilesButton.titleText != null)      binding.titleView.setText(mFilesButton.titleText);
        if (mFilesButton.messageText != null)    binding.messageView.setText(mFilesButton.messageText);
        if (mFilesButton.moreButtonText != null) binding.moreTextView.setText(mFilesButton.moreButtonText);

        if (!mSelectedFiles.isEmpty() && mSelectedFiles.get(0).isDirectory()) {
            binding.titleView.setText(getContext().getString(R.string.file_folder_tips));
        }
    }

    private void setButtonClickable(boolean clickable, RelativeLayout button) {
        button.setClickable(clickable);
        button.setAlpha(clickable ? 1f : 0.5f);
    }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setCopyButtonClick(OnCopyButtonClickListener click)       { this.mCopyClick    = click; }
    public void setMoreButtonClick(OnMoreButtonClickListener click)       { this.mMoreClick    = click; }
    public void setZipButtonClick(OnZipButtonClickListener click)         { this.mZipClick     = click; }
    public void setExtractButtonClick(OnExtractButtonClickListener click) { this.mExtractClick = click; }
    public void setEditButtonClick(OnEditButtonClickListener click)       { this.mEditClick    = click; }
    public void setFileSuffix(String suffix)                              { this.mFileSuffix   = suffix; }

    @Override
    public Window onInit() { return getWindow(); }

    // ── Listener interfaces ──────────────────────────────────────────────────

    public interface OnCopyButtonClickListener    { void onButtonClick(); }
    public interface OnMoreButtonClickListener    { void onButtonClick(); }
    public interface OnZipButtonClickListener     { void onButtonClick(List<File> files, File outputDir); }
    public interface OnExtractButtonClickListener { void onButtonClick(File zipFile, File outputDir); }
    public interface OnEditButtonClickListener    { void onButtonClick(File file); }

    // ── FilesButton ──────────────────────────────────────────────────────────

    public static class FilesButton {
        boolean copy, move, share, rename, delete, more, zip, extract, edit;
        String titleText, messageText, moreButtonText;

        public void setButtonVisibility(boolean copy, boolean move, boolean share,
                                        boolean rename, boolean delete, boolean more) {
            this.copy    = copy;
            this.move    = move;
            this.share   = share;
            this.rename  = rename;
            this.delete  = delete;
            this.more    = more;
            this.zip     = true;
            this.extract = true;
            this.edit    = true;
        }

        public void setButtonVisibility(boolean copy, boolean move, boolean share,
                                        boolean rename, boolean delete, boolean more,
                                        boolean zip, boolean extract) {
            this.copy    = copy;
            this.move    = move;
            this.share   = share;
            this.rename  = rename;
            this.delete  = delete;
            this.more    = more;
            this.zip     = zip;
            this.extract = extract;
            this.edit    = true;
        }

        public void setDialogText(String title, String message, String moreBtn) {
            this.titleText      = title;
            this.messageText    = message;
            this.moreButtonText = moreBtn;
        }

        public void setTitleText(String t)       { this.titleText      = t; }
        public void setMessageText(String m)     { this.messageText    = m; }
        public void setMoreButtonText(String b)  { this.moreButtonText = b; }
    }
}
