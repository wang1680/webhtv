package com.fongmi.android.tv.ui.custom;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.player.lut.LutStore;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;

public class LutQuickPanel extends FrameLayout {

    private MaterialTextView delay;
    private MaterialTextView empty;
    private RecyclerView recycler;
    private final PanelAdapter adapter;
    private final View panel;
    private PlayerManager player;
    private PlayerView playerView;
    private Runnable refresh;
    private ImportCallback importCallback;
    private int selectSeq;

    public LutQuickPanel(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setVisibility(GONE);
        setClipChildren(true);
        panel = createPanel();
        adapter = new PanelAdapter();
        recycler.setAdapter(adapter);
        addView(panel);
    }

    public void toggle(PlayerManager player, PlayerView playerView, Runnable refresh, ImportCallback importCallback) {
        bind(player, playerView, refresh, importCallback);
        if (getVisibility() == VISIBLE) hide();
        else show();
    }

    public void bind(PlayerManager player, PlayerView playerView, Runnable refresh, ImportCallback importCallback) {
        this.player = player;
        this.playerView = playerView;
        this.refresh = refresh;
        this.importCallback = importCallback;
        refreshList();
    }

    public void refreshList() {
        if (adapter == null) return;
        List<Entry> items = new ArrayList<>();
        items.add(Entry.original());
        for (LutPreset preset : LutStore.getPresets()) items.add(Entry.preset(preset));
        empty.setVisibility(items.size() <= 1 ? VISIBLE : GONE);
        adapter.setItems(items);
        delay.setText(ResUtil.getString(R.string.lut_preview_delay_value, LutSetting.getPreviewSeconds()));
    }

    public void selectImported(LutPreset preset, PlayerManager player, PlayerView playerView, Runnable refresh) {
        bind(player, playerView, refresh, importCallback);
        setVisibility(VISIBLE);
        select(preset);
    }

    public boolean hideIfVisible() {
        if (getVisibility() != VISIBLE) return false;
        hide();
        return true;
    }

    private void show() {
        setVisibility(VISIBLE);
        refreshList();
        updatePanelWidth();
        panel.post(() -> {
            panel.setTranslationX(panel.getWidth());
            panel.animate().translationX(0).setDuration(180).start();
            recycler.requestFocus();
        });
    }

    private void hide() {
        panel.animate().translationX(panel.getWidth()).setDuration(160).withEndAction(() -> {
            setVisibility(GONE);
            panel.setTranslationX(0);
        }).start();
    }

    private void select(LutPreset preset) {
        if (player == null) return;
        int seq = ++selectSeq;
        if (preset == null) {
            LutSetting.select(null);
            player.applyLut(true);
            notifyChanged();
            return;
        }
        if (seq != selectSeq) return;
        LutSetting.select(preset);
        player.applyLutPreview(true);
        notifyChanged();
    }

    private void notifyChanged() {
        if (refresh != null) refresh.run();
        adapter.notifyDataSetChanged();
    }

    private View createPanel() {
        FrameLayout container = new FrameLayout(getContext());
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(dp(220), LayoutParams.MATCH_PARENT, Gravity.END);
        container.setLayoutParams(containerParams);
        container.setBackgroundColor(0xE6101118);
        container.setPadding(dp(12), dp(12), dp(12), dp(12));
        androidx.appcompat.widget.LinearLayoutCompat column = new androidx.appcompat.widget.LinearLayoutCompat(getContext());
        column.setOrientation(androidx.appcompat.widget.LinearLayoutCompat.VERTICAL);
        container.addView(column, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        androidx.appcompat.widget.LinearLayoutCompat header = new androidx.appcompat.widget.LinearLayoutCompat(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(androidx.appcompat.widget.LinearLayoutCompat.HORIZONTAL);
        column.addView(header, new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        MaterialTextView title = text(R.string.player_lut, 16, true);
        header.addView(title, new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        MaterialTextView close = chip();
        close.setText(R.string.lut_close);
        close.setOnClickListener(view -> hide());
        header.addView(close);

        androidx.appcompat.widget.LinearLayoutCompat tools = new androidx.appcompat.widget.LinearLayoutCompat(getContext());
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setOrientation(androidx.appcompat.widget.LinearLayoutCompat.HORIZONTAL);
        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams toolsParams = new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        toolsParams.setMargins(0, dp(10), 0, 0);
        column.addView(tools, toolsParams);

        delay = chip();
        delay.setOnClickListener(view -> cycleDelay());
        tools.addView(delay);
        MaterialTextView importView = chip();
        importView.setText(R.string.lut_import);
        importView.setOnClickListener(view -> {
            if (importCallback != null) importCallback.onImportLut();
        });
        tools.addView(importView);

        empty = text(R.string.lut_empty_presets, 14, false);
        empty.setGravity(Gravity.CENTER);
        column.addView(empty, new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));

        recycler = new RecyclerView(getContext());
        recycler.setOverScrollMode(OVER_SCROLL_NEVER);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        column.addView(recycler, new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        return container;
    }

    private void updatePanelWidth() {
        ViewGroup.LayoutParams params = panel.getLayoutParams();
        if (params == null || getWidth() <= 0) return;
        params.width = Math.max(dp(160), Math.min(dp(260), Math.round(getWidth() * 0.42f)));
        panel.setLayoutParams(params);
    }

    private void cycleDelay() {
        int current = LutSetting.getPreviewSeconds();
        int next = current < 2 ? 2 : current < 3 ? 3 : current < 5 ? 5 : current < 8 ? 8 : 1;
        LutSetting.putPreviewSeconds(next);
        delay.setText(ResUtil.getString(R.string.lut_preview_delay_value, next));
    }

    private MaterialTextView chip() {
        MaterialTextView view = text(0, 13, false);
        view.setFocusable(true);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(Color.WHITE);
        view.setPadding(dp(8), dp(6), dp(8), dp(6));
        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams params = new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(8));
        view.setLayoutParams(params);
        applyBg(view, false, false);
        view.setOnFocusChangeListener((v, focused) -> applyBg((MaterialTextView) v, false, focused));
        return view;
    }

    private MaterialTextView text(int resId, int sp, boolean bold) {
        MaterialTextView view = new MaterialTextView(getContext());
        if (resId != 0) view.setText(resId);
        view.setTextColor(Color.WHITE);
        view.setTextSize(sp);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private void applyBg(MaterialTextView view, boolean selected, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(selected ? 0xFF2F80ED : focused ? 0x55FFFFFF : 0x22FFFFFF);
        drawable.setStroke(dp(1), selected || focused ? 0xFFFFFFFF : 0x33FFFFFF);
        drawable.setCornerRadius(dp(6));
        view.setBackground(drawable);
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }

    public interface ImportCallback {
        void onImportLut();
    }

    private static class Entry {
        private final LutPreset preset;

        private Entry(LutPreset preset) {
            this.preset = preset;
        }

        static Entry original() {
            return new Entry(null);
        }

        static Entry preset(LutPreset preset) {
            return new Entry(preset);
        }

        boolean isOriginal() {
            return preset == null;
        }

        String getText() {
            return preset == null ? ResUtil.getString(R.string.lut_original) : preset.getName();
        }

        boolean isSelected() {
            return preset == null ? !LutSetting.isEnabled() : LutSetting.isEnabled() && preset.getId().equals(LutSetting.getPresetId());
        }
    }

    private class PanelAdapter extends RecyclerView.Adapter<PanelAdapter.ViewHolder> {
        private final List<Entry> items = new ArrayList<>();

        void setItems(List<Entry> next) {
            items.clear();
            items.addAll(next);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialTextView view = text(0, 14, false);
            view.setFocusable(true);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setSingleLine(true);
            view.setPadding(dp(12), 0, dp(12), 0);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
            params.setMargins(0, dp(8), 0, 0);
            view.setLayoutParams(params);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Entry entry = items.get(position);
            holder.text.setText(entry.getText());
            applyBg(holder.text, entry.isSelected(), holder.text.hasFocus());
            holder.text.setOnFocusChangeListener((view, focused) -> applyBg((MaterialTextView) view, entry.isSelected(), focused));
            holder.text.setOnClickListener(view -> select(entry.preset));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final MaterialTextView text;

            private ViewHolder(@NonNull MaterialTextView itemView) {
                super(itemView);
                this.text = itemView;
            }
        }
    }
}
