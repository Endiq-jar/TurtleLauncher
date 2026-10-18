package net.endiq.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import net.endiq.launcher.utils.JREUtils;
import java.util.LinkedList;


public class AWTCanvasView extends TextureView implements TextureView.SurfaceTextureListener, Runnable {
    public static final int AWT_CANVAS_WIDTH = Math.min(
            (int) (Tools.currentDisplayMetrics.widthPixels * 0.8),
            com.endiq.turtlelauncher.setting.AllSettings.getAwtCanvasSizeCap().getValue());
    public static final int AWT_CANVAS_HEIGHT = Math.min(
            (int) (Tools.currentDisplayMetrics.heightPixels * 0.8),
            com.endiq.turtlelauncher.setting.AllSettings.getAwtCanvasSizeCap().getValue());
    private static final int MAX_SIZE = 100;
    private static final double NANOS = 1000000000.0;
    private volatile boolean mIsDestroyed = false;
    private volatile boolean mPaused = false;
    private volatile boolean mWindowVisible = true;
    private final Object mRenderLock = new Object();
    private volatile Thread mRenderThread;
    private volatile SurfaceTexture mRenderTexture;
    private final TextPaint mFpsPaint;

    // Temporary count fps https://stackoverflow.com/a/13729241
    private final LinkedList<Long> mTimes = new LinkedList<Long>(){{add(System.nanoTime());}};
    
    public AWTCanvasView(Context ctx) {
        this(ctx, null);
    }
    
    public AWTCanvasView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        
        mFpsPaint = new TextPaint();
        mFpsPaint.setColor(Color.WHITE);
        mFpsPaint.setTextSize(24);

        setSurfaceTextureListener(this);

        post(this::refreshSize);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) {
        texture.setDefaultBufferSize(AWT_CANVAS_WIDTH, AWT_CANVAS_HEIGHT);
        synchronized (mRenderLock) {
            if (mRenderThread != null) mRenderThread.interrupt();
            mRenderTexture = texture;
            mIsDestroyed = false;
            mRenderThread = new Thread(this, "AndroidAWTRenderer");
            mRenderThread.start();
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        synchronized (mRenderLock) {
            mIsDestroyed = true;
            mRenderTexture = null;
            if (mRenderThread != null) mRenderThread.interrupt();
            mRenderLock.notifyAll();
        }
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) {
        getSurfaceTexture().setDefaultBufferSize(AWT_CANVAS_WIDTH, AWT_CANVAS_HEIGHT);
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        // Buffer geometry changes only on availability/resize, not after every posted frame.
    }

    public void setRenderingPaused(boolean paused) {
        synchronized (mRenderLock) {
            mPaused = paused;
            mRenderLock.notifyAll();
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mWindowVisible = visibility == VISIBLE;
        // View callbacks can be invoked by the superclass constructor.
        if (mRenderLock != null) synchronized (mRenderLock) { mRenderLock.notifyAll(); }
    }

    private boolean ownsRenderer(Thread owner, SurfaceTexture texture) {
        return !mIsDestroyed && mRenderThread == owner && mRenderTexture == texture;
    }

    @Override
    public void run() {
        final Thread owner = Thread.currentThread();
        final SurfaceTexture texture;
        synchronized (mRenderLock) {
            if (mRenderThread != owner || mRenderTexture == null) return;
            texture = mRenderTexture;
        }
        Surface surface = null;
        Bitmap rgbArrayBitmap = null;
        Paint paint = new Paint();
        try {
            surface = new Surface(texture);
            rgbArrayBitmap = Bitmap.createBitmap(AWT_CANVAS_WIDTH, AWT_CANVAS_HEIGHT, Bitmap.Config.ARGB_8888);
            while (ownsRenderer(owner, texture) && surface.isValid()) {
                synchronized (mRenderLock) {
                    while (ownsRenderer(owner, texture) && (mPaused || !mWindowVisible)) mRenderLock.wait();
                }
                if (!ownsRenderer(owner, texture) || !surface.isValid()) break;
                final long started = System.nanoTime();
                Canvas canvas = null;
                try {
                    int[] rgbArray = JREUtils.renderAWTScreenFrame();
                    canvas = surface.lockCanvas(null);
                    canvas.drawRGB(0, 0, 0);
                    boolean mDrawing = rgbArray != null;
                    if (mDrawing) {
                        rgbArrayBitmap.setPixels(rgbArray, 0, AWT_CANVAS_WIDTH, 0, 0, AWT_CANVAS_WIDTH, AWT_CANVAS_HEIGHT);
                        canvas.drawBitmap(rgbArrayBitmap, 0, 0, paint);
                    }
                    canvas.drawText("FPS: " + (Math.round(fps() * 10) / 10) + ", drawing=" + mDrawing, 20, 20, mFpsPaint);
                } finally {
                    if (canvas != null) surface.unlockCanvasAndPost(canvas);
                }
                // This is the Swing/AWT installer canvas, NOT Minecraft's render surface.
                // Avoid copying huge pixel arrays as fast as the CPU can spin.
                java.util.concurrent.locks.LockSupport.parkNanos(
                        com.endiq.turtlelauncher.launch.FrameBudget.remainingNanos(started, System.nanoTime(), 60));
            }
        } catch (InterruptedException expected) {
            // Surface replacement or teardown wakes a paused worker immediately.
        } catch (Throwable throwable) {
            if (ownsRenderer(owner, texture)) post(() -> Tools.showError(getContext(), throwable));
        } finally {
            if (rgbArrayBitmap != null) rgbArrayBitmap.recycle();
            if (surface != null) surface.release();
            synchronized (mRenderLock) {
                if (mRenderThread == owner) mRenderThread = null;
            }
        }
    }

    /** Calculates and returns frames per second */
    private synchronized double fps() {
        long lastTime = System.nanoTime();
        double difference = (lastTime - mTimes.getFirst()) / NANOS;
        mTimes.addLast(lastTime);
        int size = mTimes.size();
        if (size > MAX_SIZE) {
            mTimes.removeFirst();
        }
        return difference > 0 ? mTimes.size() / difference : 0.0;
    }

    /** Make the view fit the proper aspect ratio of the surface */
    private void refreshSize(){
        ViewGroup.LayoutParams layoutParams = getLayoutParams();

        if(getHeight() < getWidth()){
            layoutParams.width = AWT_CANVAS_WIDTH * getHeight() / AWT_CANVAS_HEIGHT;
        }else{
            layoutParams.height = AWT_CANVAS_HEIGHT * getWidth() / AWT_CANVAS_WIDTH;
        }

        setLayoutParams(layoutParams);
    }

}
