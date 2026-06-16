package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.databinding.AdapterEpisodeBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final OnLongClickListener mLongClickListener;
    private final List<Episode> mItems;
    private final int maxWidth;
    private final int spacing;
    private int nextFocusDown;
    private int nextFocusUp;
    private int column;
    private boolean useTmdbCard = false;

    public EpisodeAdapter(OnClickListener listener) {
        this(listener, null);
    }

    public EpisodeAdapter(OnClickListener listener, OnLongClickListener longClickListener) {
        mListener = listener;
        mLongClickListener = longClickListener;
        mItems = new ArrayList<>();
        maxWidth = ResUtil.getScreenWidth() - ResUtil.dp2px(48);
        spacing = ResUtil.dp2px(8);
        column = 1;
    }

    public void addAll(List<Episode> items) {
        mItems.clear();
        mItems.addAll(items);
        useTmdbCard = false;
        notifyDataSetChanged();
    }

    public boolean isUsingTmdbCard() {
        return useTmdbCard;
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public int getPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return 0;
    }

    public List<Episode> getItems() {
        return mItems;
    }

    public int getSelectedPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return RecyclerView.NO_POSITION;
    }

    public int indexOf(Episode item) {
        return mItems.indexOf(item);
    }

    public void notifySelectionChanged(int oldPosition, int newPosition) {
        if (oldPosition != RecyclerView.NO_POSITION) notifyItemChanged(oldPosition);
        if (newPosition != RecyclerView.NO_POSITION && newPosition != oldPosition) notifyItemChanged(newPosition);
    }

    public Episode getActivated() {
        return mItems.isEmpty() ? new Episode() : mItems.get(getPosition());
    }

    public Episode getNext() {
        int current = getPosition();
        int max = getItemCount() - 1;
        current = ++current > max ? max : current;
        return mItems.get(current);
    }

    public Episode getPrev() {
        int current = getPosition();
        current = --current < 0 ? 0 : current;
        return mItems.get(current);
    }

    public void setNextFocusDown(int nextFocusDown) {
        if (this.nextFocusDown == nextFocusDown) return;
        this.nextFocusDown = nextFocusDown;
        notifyDataSetChanged();
    }

    public void setNextFocusUp(int nextFocusUp) {
        if (this.nextFocusUp == nextFocusUp) return;
        this.nextFocusUp = nextFocusUp;
        notifyDataSetChanged();
    }

    public void setColumn(int column) {
        column = Math.max(1, column);
        if (this.column == column) return;
        this.column = column;
        notifyDataSetChanged();
    }

    public static int getColumn(List<Episode> items) {
        int max = 1;
        for (Episode item : items) max = Math.max(max, item.getName().length());
        if (max <= 1) return 8;
        if (max <= 3) return 6;
        if (max <= 5) return 5;
        if (max <= 8) return 4;
        if (max <= 14) return 3;
        return 2;
    }

    public static String getTitle(Episode item) {
        return item.getDesc().concat(item.getDisplayName());
    }

    private int getWidth() {
        return Math.min((maxWidth - spacing * (column - 1)) / column, ResUtil.dp2px(120));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AdapterEpisodeBinding binding = AdapterEpisodeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        // 强制禁用系统默认焦点高亮（灰色遮盖）
        binding.cardContainer.setDefaultFocusHighlightEnabled(false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Episode item = mItems.get(position);
        TmdbEpisode tmdbEpisode = item.getTmdbEpisode();

        if (useTmdbCard && tmdbEpisode != null) {
            // 卡片模式
            holder.binding.text.setVisibility(View.GONE);
            holder.binding.cardContainer.setVisibility(View.VISIBLE);

            // 设置选中状态（用于边框颜色）
            holder.binding.cardContainer.setSelected(item.isSelected());

            // 加载剧照
            if (!tmdbEpisode.getStillUrl().isEmpty()) {
                Glide.with(holder.binding.still.getContext())
                    .load(tmdbEpisode.getStillUrl())
                    .placeholder(R.color.black)
                    .error(R.color.black)
                    .into(holder.binding.still);
            } else {
                holder.binding.still.setImageResource(R.color.black);
            }

            // 设置标题
            holder.binding.cardTitle.setText(tmdbEpisode.getDisplayTitle());

            // 设置评分
            if (tmdbEpisode.getVoteAverage() > 0) {
                holder.binding.rating.setText(String.format("★%.1f", tmdbEpisode.getVoteAverage()));
                holder.binding.rating.setVisibility(View.VISIBLE);
            } else {
                holder.binding.rating.setVisibility(View.GONE);
            }

            // 设置简介
            if (!tmdbEpisode.getOverview().isEmpty()) {
                holder.binding.overview.setText(tmdbEpisode.getOverview());
                holder.binding.overview.setVisibility(View.VISIBLE);
            } else {
                holder.binding.overview.setVisibility(View.GONE);
            }

            // 点击和长按事件
            holder.binding.cardContainer.setOnClickListener(v -> mListener.onItemClick(item));
            if (mLongClickListener != null) {
                holder.binding.cardContainer.setOnLongClickListener(v -> {
                    mLongClickListener.onItemLongClick(item);
                    return true;
                });
            }

        } else {
            // 文本模式
            holder.binding.cardContainer.setVisibility(View.GONE);
            holder.binding.text.setVisibility(View.VISIBLE);

            holder.binding.text.getLayoutParams().width = getWidth();
            holder.binding.text.setNextFocusUpId(position < column && nextFocusUp != 0 ? nextFocusUp : View.NO_ID);
            holder.binding.text.setNextFocusDownId(position >= getItemCount() - column && nextFocusDown != 0 ? nextFocusDown : View.NO_ID);
            holder.binding.text.setSelected(item.isSelected());
            holder.binding.text.setText(item.getDesc().concat(item.getName()));
            holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
        }
    }

    public interface OnClickListener {
        void onItemClick(Episode item);
    }

    public interface OnLongClickListener {
        void onItemLongClick(Episode item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterEpisodeBinding binding;

        ViewHolder(@NonNull AdapterEpisodeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
