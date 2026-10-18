package com.endiq.turtlelauncher.ui.subassembly.about;

import android.app.Activity;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

public class AboutItemBean {
    private final Drawable icon;
    private final int imageResource;
    private final String title, desc;
    private final AboutItemButtonBean buttonBean;

    public AboutItemBean(@NonNull Drawable icon, @NonNull String title, @NonNull String desc, AboutItemButtonBean buttonBean) {
        this(icon, title, desc, buttonBean, 0);
    }

    public AboutItemBean(@NonNull Drawable icon, @NonNull String title, @NonNull String desc,
                         AboutItemButtonBean buttonBean, int imageResource) {
        this.imageResource = imageResource;
        this.icon = icon;
        this.title = title;
        this.desc = desc;
        this.buttonBean = buttonBean;
    }

    public int getImageResource() { return imageResource; }

    public Drawable getIcon() {
        return icon;
    }

    public String getTitle() {
        return title;
    }

    public String getDesc() {
        return desc;
    }

    public AboutItemButtonBean getButtonBean() {
        return buttonBean;
    }

    public static class AboutItemButtonBean {
        private final Activity activity;
        private final String name, url;

        public AboutItemButtonBean(@NonNull Activity activity, @NonNull String name, @NonNull String url) {
            this.activity = activity;
            this.name = name;
            this.url = url;
        }

        public Activity getActivity() {
            return activity;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }
    }
}