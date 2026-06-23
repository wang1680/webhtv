package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterLiveEpgBinding;
import com.fongmi.android.tv.setting.LiveEpgSetting;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class LiveEpgAdapter extends RecyclerView.Adapter<LiveEpgAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<String> items;

    public LiveEpgAdapter(OnClickListener listener) {
        this.listener = listener;
        this.items = new ArrayList<>();
        reload();
    }

    public void reload() {
        items.clear();
        items.add("");
        for (String item : LiveEpgSetting.getHistory()) add(item);
        notifyDataSetChanged();
    }

    private void add(String item) {
        if (!items.contains(item)) items.add(item);
    }

    public interface OnClickListener {

        void onEpgClick(String url);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterLiveEpgBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String item = items.get(position);
        holder.binding.text.setText(item.isEmpty() ? ResUtil.getString(R.string.live_epg_default) : item);
        holder.binding.text.setSelected(item.equals(LiveEpgSetting.getUrl()));
        holder.binding.text.setOnClickListener(v -> listener.onEpgClick(item));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterLiveEpgBinding binding;

        ViewHolder(@NonNull AdapterLiveEpgBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
