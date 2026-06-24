package com.fongmi.android.tv.ui.holder;

import android.content.Context;
import android.content.ContextWrapper;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.databinding.AdapterEpisodeHoriBinding;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.base.BaseEpisodeHolder;
import com.fongmi.android.tv.ui.dialog.EpisodeDetailDialog;
import com.fongmi.android.tv.utils.ResUtil;

public class EpisodeHoriHolder extends BaseEpisodeHolder {

    private final EpisodeAdapter.OnClickListener listener;
    private final AdapterEpisodeHoriBinding binding;
    private final int maxWidth;
    private boolean useTmdbCard;

    public EpisodeHoriHolder(@NonNull AdapterEpisodeHoriBinding binding, EpisodeAdapter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
        this.maxWidth = ResUtil.getScreenWidth() - ResUtil.dp2px(32);
    }

    @Override
    public void setUseTmdbCard(boolean useTmdbCard) {
        this.useTmdbCard = useTmdbCard;
    }

    @Override
    public void initView(Episode item) {
        TmdbEpisode tmdbEpisode = item.getTmdbEpisode();

        if (useTmdbCard && tmdbEpisode != null) {
            // TMDB 模式：显示卡片，隐藏文本
            binding.text.setVisibility(View.GONE);
            binding.card.setVisibility(View.VISIBLE);

            binding.card.setSelected(item.isSelected());
            bindCardActions(item, binding.getRoot(), binding.card, binding.still, binding.cardTitle, binding.overview);

            // 标题
            binding.cardTitle.setText(EpisodeAdapter.getTitle(item));
            binding.cardTitle.setSelected(item.isSelected());

            // 剧照
            if (!TextUtils.isEmpty(tmdbEpisode.getStillUrl())) {
                binding.still.setVisibility(View.VISIBLE);
                Glide.with(binding.still.getContext())
                        .load(tmdbEpisode.getStillUrl())
                        .into(binding.still);
            } else {
                binding.still.setVisibility(View.GONE);
            }

            // 简介
            if (!TextUtils.isEmpty(tmdbEpisode.getOverview())) {
                binding.overview.setVisibility(View.VISIBLE);
                binding.overview.setText(tmdbEpisode.getOverview());
            } else {
                binding.overview.setVisibility(View.GONE);
            }

            // 评分
            if (tmdbEpisode.getVoteAverage() > 0) {
                binding.rating.setVisibility(View.VISIBLE);
                binding.rating.setText(String.format(java.util.Locale.US, "★%.1f", tmdbEpisode.getVoteAverage()));
            } else {
                binding.rating.setVisibility(View.GONE);
            }
        } else {
            // 非 TMDB 模式：显示文本，隐藏卡片
            binding.text.setVisibility(View.VISIBLE);
            binding.card.setVisibility(View.GONE);

            binding.text.setMaxWidth(maxWidth);
            binding.text.setSelected(item.isSelected());
            binding.text.setText(EpisodeAdapter.getNativeTitle(item));
            binding.text.setOnClickListener(v -> listener.onItemClick(item));
            EpisodeAdapter.bindNativeTitlePopup(binding.getRoot(), item);
            EpisodeAdapter.bindNativeTitlePopup(binding.text, item);
        }
    }

    private void bindDetailLongClick(Episode item, View... views) {
        View.OnLongClickListener longClickListener = view -> {
            FragmentActivity activity = getActivity(view);
            if (activity == null) return false;
            EpisodeDetailDialog.show(activity, item);
            return true;
        };
        for (View view : views) {
            if (view == null) continue;
            view.setOnTouchListener(null);
            view.setOnLongClickListener(longClickListener);
        }
    }

    private void bindCardActions(Episode item, View... views) {
        View.OnClickListener clickListener = view -> listener.onItemClick(item);
        for (View view : views) {
            if (view == null) continue;
            view.setOnClickListener(clickListener);
        }
        bindDetailLongClick(item, views);
    }

    private FragmentActivity getActivity(View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof FragmentActivity) return (FragmentActivity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}

