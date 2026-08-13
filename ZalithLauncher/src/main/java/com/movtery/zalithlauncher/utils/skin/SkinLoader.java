package com.movtery.zalithlauncher.utils.skin;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.movtery.zalithlauncher.feature.log.Logging;
import com.movtery.zalithlauncher.utils.path.PathManager;

import net.kdt.pojavlaunch.value.MinecraftAccount;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class SkinLoader {
    /** Returns the raw, full skin texture bitmap (not cropped to a face/avatar) for the
     *  given account - falls back to the bundled default ("steve") skin if the account has
     *  no locally cached skin. Used to feed the 3D skin preview (SkinView3DSurfaceView),
     *  which needs the whole 64x64/64x32 texture rather than just the face crop getAvatar()
     *  produces. */
    public static Bitmap getSkinBitmap(Context context, MinecraftAccount account) throws IOException {
        File skin = new File(PathManager.DIR_USER_SKIN, account.getUniqueUUID() + ".png");
        if (skin.exists()) {
            try (InputStream is = Files.newInputStream(skin.toPath())) {
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                if (bitmap != null) return bitmap;
                Logging.e("SkinLoader", "Failed to read the skin picture and try to parse it to a bitmap");
            } catch (Exception e) {
                Logging.e("SkinLoader", "Failed to load skin bitmap from locally!", e);
            }
        }
        try (InputStream is = context.getAssets().open("steve.png")) {
            return BitmapFactory.decodeStream(is);
        }
    }

    public static Drawable getAvatarDrawable(Context context, MinecraftAccount account, int size) throws Exception {
        File skin = new File(PathManager.DIR_USER_SKIN, account.getUniqueUUID() + ".png");
        if (skin.exists()) {
            try (InputStream is = Files.newInputStream(skin.toPath())) {
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                if (bitmap == null) throw new IOException("Failed to read the skin picture and try to parse it to a bitmap");
                return new BitmapDrawable(context.getResources(), getAvatar(bitmap, size));
            } catch (Exception e) {
                //本地皮肤加载失败，输出到日志内，稍后尝试加载默认的头像“steve”
                Logging.e("SkinLoader", "Failed to load avatar from locally!", e);
            }
        }
        return getDefaultAvatar(context, size);
    }

    private static Drawable getDefaultAvatar(Context context, int size) throws Exception {
        InputStream is = context.getAssets().open("steve.png");
        Bitmap bitmap = BitmapFactory.decodeStream(is);
        return new BitmapDrawable(context.getResources(), getAvatar(bitmap, size));
    }

    public static Bitmap getAvatar(@NotNull Bitmap skin, int size) {
        float faceOffset = Math.round(size / 18.0);
        float scaleFactor = skin.getWidth() / 64.0f;
        int faceSize = Math.round(8 * scaleFactor);
        Bitmap faceBitmap = Bitmap.createBitmap(skin, faceSize, faceSize, faceSize, faceSize, (Matrix) null, false);
        Bitmap hatBitmap = Bitmap.createBitmap(skin, Math.round(40 * scaleFactor), faceSize, faceSize, faceSize, (Matrix) null, false);
        Bitmap avatar = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(avatar);
        Matrix matrix;
        float faceScale = ((size - 2 * faceOffset) / faceSize);
        float hatScale = ((float) size / faceSize);
        matrix = new Matrix();
        matrix.postScale(faceScale, faceScale);
        Bitmap newFaceBitmap = Bitmap.createBitmap(faceBitmap, 0, 0 , faceSize, faceSize, matrix, false);
        matrix = new Matrix();
        matrix.postScale(hatScale, hatScale);
        Bitmap newHatBitmap = Bitmap.createBitmap(hatBitmap, 0, 0, faceSize, faceSize, matrix, false);
        canvas.drawBitmap(newFaceBitmap, faceOffset, faceOffset, new Paint(Paint.ANTI_ALIAS_FLAG));
        canvas.drawBitmap(newHatBitmap, 0, 0, new Paint(Paint.ANTI_ALIAS_FLAG));
        return avatar;
    }
}
