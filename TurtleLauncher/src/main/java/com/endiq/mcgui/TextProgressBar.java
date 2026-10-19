package com.endiq.mcgui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.widget.ProgressBar;

import androidx.annotation.StringRes;

import com.endiq.turtlelauncher.ui.miuix.MiuixColorScheme;
import com.endiq.turtlelauncher.ui.miuix.MiuixTheme;

/**
 * Miuix-styled capsule progress bar with inline task label (`ProgressIndicator.kt`).
 */
public class TextProgressBar extends ProgressBar {

    private int mTextPadding = 0;
    private Paint mTextPaint;
    private Paint mTrackPaint;
    private Paint mFillPaint;
    private final RectF mBoundsRect = new RectF();
    private final RectF mFillRect = new RectF();
    private final Path mClipPath = new Path();
    private String mText = "";

    public TextProgressBar(Context context) {
        super(context, null, android.R.attr.progressBarStyleHorizontal);
        init();
    }

    public TextProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.progressBarStyleHorizontal);
        init();
    }

    public TextProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, android.R.attr.progressBarStyleHorizontal);
        init();
    }

    public TextProgressBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, android.R.attr.progressBarStyleHorizontal, defStyleRes);
        init();
    }

    private void init() {
        super.setProgressDrawable(new ColorDrawable(0));
        setProgress(0);
        mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTrackPaint.setStyle(Paint.Style.FILL);
        mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFillPaint.setStyle(Paint.Style.FILL);
        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setFlags(Paint.FAKE_BOLD_TEXT_FLAG);
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) return;

        MiuixColorScheme colors = MiuixTheme.colors(getContext());
        float cornerRadius = h / 2f;

        mBoundsRect.set(0f, 0f, w, h);
        mClipPath.reset();
        mClipPath.addRoundRect(mBoundsRect, cornerRadius, cornerRadius, Path.Direction.CW);

        int save = canvas.save();
        canvas.clipPath(mClipPath);

        mTrackPaint.setColor(colors.getSliderBackground());
        canvas.drawRoundRect(mBoundsRect, cornerRadius, cornerRadius, mTrackPaint);

        int maxVal = Math.max(getMax(), 1);
        float fraction = Math.max(0f, Math.min(1f, (float) getProgress() / (float) maxVal));
        if (fraction > 0f) {
            float minWidth = Math.min(h, w);
            float fillWidth = minWidth + (w - minWidth) * fraction;
            mFillRect.set(0f, 0f, fillWidth, h);
            mFillPaint.setColor(colors.getPrimary());
            canvas.drawRoundRect(mFillRect, cornerRadius, cornerRadius, mFillPaint);
        }

        mTextPaint.setTextSize((float) ((h - getPaddingBottom() - getPaddingTop()) * 0.50));
        mTextPaint.setColor(colors.getOnSurface());
        int xPos = (int) Math.max(
                Math.min((getProgress() * w / maxVal) + mTextPadding, w - mTextPaint.measureText(mText) - mTextPadding),
                mTextPadding
        );
        int yPos = (int) ((h / 2f) - ((mTextPaint.descent() + mTextPaint.ascent()) / 2f));
        canvas.drawText(mText, xPos, yPos, mTextPaint);

        canvas.restoreToCount(save);
    }

    public final void setText(@StringRes int resid) {
        setText(getContext().getResources().getText(resid).toString());
    }

    public final void setText(String text) {
        mText = text != null ? text : "";
        invalidate();
    }

    public final void setTextPadding(int padding) {
        mTextPadding = padding;
    }
}
