package com.fongmi.android.tv.ui.holder;

import androidx.annotation.NonNull;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.AdapterEpisodeGridBinding;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.base.BaseEpisodeHolder;

public class EpisodeGridHolder extends BaseEpisodeHolder {

    private final EpisodeAdapter.OnClickListener listener;
    private final AdapterEpisodeGridBinding binding;

    public EpisodeGridHolder(@NonNull AdapterEpisodeGridBinding binding, EpisodeAdapter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
    }

    @Override
    public void initView(Episode item) {
        binding.text.setActivated(item.isSelected());
        binding.text.setHorizontallyScrolling(true);
        binding.text.setText(item.getDesc().concat(item.getName()));
        setMarquee(binding.text.hasFocus());
        binding.text.setOnFocusChangeListener((view, hasFocus) -> setMarquee(hasFocus));
        binding.text.setOnClickListener(v -> {
            binding.text.requestFocus();
            setMarquee(true);
            listener.onItemClick(item);
        });
        binding.text.post(() -> setMarquee(binding.text.hasFocus()));
    }

    private void setMarquee(boolean focused) {
        binding.text.setEllipsize(focused ? TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.START);
        binding.text.setSelected(focused);
    }
}
