package com.endiq.turtlelauncher.ui.subassembly.about;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.endiq.turtlelauncher.databinding.ItemAboutViewBinding;
import com.endiq.turtlelauncher.utils.anim.AnimatedIcon;
import com.endiq.turtlelauncher.utils.ZHTools;

import java.util.List;

public class AboutRecyclerAdapter extends RecyclerView.Adapter<AboutRecyclerAdapter.InnerHolder> {
    private final List<AboutItemBean> itemBeans;

    public AboutRecyclerAdapter(List<AboutItemBean> data) {
        this.itemBeans = data;
    }

    @NonNull
    @Override
    public AboutRecyclerAdapter.InnerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new InnerHolder(ItemAboutViewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull AboutRecyclerAdapter.InnerHolder holder, int position) {
        holder.setData(this.itemBeans.get(position));
    }

    @Override
    public int getItemCount() {
        if (this.itemBeans != null) {
            return this.itemBeans.size();
        }
        return 0;
    }

    @Override
    public void onViewRecycled(@NonNull InnerHolder holder) {
        com.bumptech.glide.Glide.with(holder.binding.imageView).clear(holder.binding.imageView);
        super.onViewRecycled(holder);
    }

    public static class InnerHolder extends RecyclerView.ViewHolder {
        private final ItemAboutViewBinding binding;

        public InnerHolder(@NonNull ItemAboutViewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void setData(AboutItemBean data) {
            // Recycled rows must restore both actions and visibility, not retain the last row.
            com.bumptech.glide.Glide.with(binding.imageView).clear(binding.imageView);
            binding.imageView.setImageDrawable(data.getIcon());
            // ShapeableImageView bypasses the XML AnimatedIconView conversion, so bind its
            // interaction style here. The key includes the row title, giving every credit
            // row its own stable style/seed instead of one shared animation.
            AnimatedIcon.bindNamed(binding.imageView, "about_" + data.getTitle());
            if (data.getImageResource() != 0) {
                // Decode to the thumbnail's bounds, not 1024px multiplied by screen density.
                com.bumptech.glide.Glide.with(binding.imageView).load(data.getImageResource())
                        .placeholder(data.getIcon()).fitCenter().into(binding.imageView);
            }
            binding.imageView.setContentDescription(data.getTitle());
            binding.imageView.setOnClickListener(v -> {
                android.widget.ImageView preview = new android.widget.ImageView(v.getContext());
                android.graphics.drawable.Drawable.ConstantState state = data.getIcon().getConstantState();
                preview.setImageDrawable(state != null ? state.newDrawable(v.getResources()).mutate() : data.getIcon());
                preview.setAdjustViewBounds(true);
                preview.setContentDescription(data.getTitle());
                int padding = (int) (24 * v.getResources().getDisplayMetrics().density);
                preview.setPadding(padding, padding, padding, padding);
                if (data.getImageResource() != 0) {
                    com.bumptech.glide.Glide.with(preview).load(data.getImageResource())
                            .override(1024, 1024).fitCenter().into(preview);
                }
                androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(v.getContext())
                        .setTitle(data.getTitle()).setView(preview)
                        .setPositiveButton(android.R.string.ok, null).create();
                dialog.setOnDismissListener(ignored -> com.bumptech.glide.Glide.with(preview).clear(preview));
                dialog.show();
            });
            binding.titleView.setText(data.getTitle());
            binding.descView.setText(data.getDesc());

            if (data.getButtonBean() != null) {
                AboutItemBean.AboutItemButtonBean buttonBean = data.getButtonBean();
                String buttonName = buttonBean.getName();

                binding.buttonView.setVisibility(View.VISIBLE);
                binding.buttonView.setText(buttonName);

                binding.buttonView.setOnClickListener(v -> {
                    String url = buttonBean.getUrl();
                    ZHTools.openLink(buttonBean.getActivity(), url);
                });
            } else {
                binding.buttonView.setVisibility(View.GONE);
                binding.buttonView.setOnClickListener(null);
            }
        }
    }
}