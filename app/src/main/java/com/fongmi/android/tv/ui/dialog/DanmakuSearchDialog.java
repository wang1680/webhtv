package com.fongmi.android.tv.ui.dialog;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.MediaMetadata;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.databinding.DialogDanmakuSearchBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.ui.adapter.DanmakuAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.textview.MaterialTextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public final class DanmakuSearchDialog extends BaseBottomSheetDialog implements DanmakuAdapter.OnClickListener, Callback {

    private final DanmakuAdapter adapter;
    private final List<MaterialTextView> sourceViews;
    private DialogDanmakuSearchBinding binding;
    private PlayerManager player;

    public static DanmakuSearchDialog create() {
        return new DanmakuSearchDialog();
    }

    public DanmakuSearchDialog() {
        this.adapter = new DanmakuAdapter(this).groupBySource(true);
        this.sourceViews = new ArrayList<>();
    }

    public DanmakuSearchDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof DanmakuSearchDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDanmakuSearchBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, Util.isLeanback() ? 12 : 10));
        binding.sourceScroller.setVisibility(GONE);
        binding.empty.setVisibility(GONE);
        setKeyword(player.getMetadata().title);
        Util.showKeyboard(binding.keyword);
    }

    @Override
    protected void initEvent() {
        binding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH && !binding.keyword.getText().toString().trim().isEmpty()) search();
            return true;
        });
        binding.keyword.setOnKeyListener((view, keyCode, event) -> {
            if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event) && binding.recycler.getVisibility() == VISIBLE) return focusResultGroup();
            return false;
        });
        binding.search.setOnKeyListener((view, keyCode, event) -> {
            if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event) && binding.recycler.getVisibility() == VISIBLE) return focusResultGroup();
            return false;
        });
        binding.search.setOnClickListener(view -> {
            if (!binding.keyword.getText().toString().trim().isEmpty()) search();
        });
    }

    @Override
    public void onItemClick(Danmaku item) {
        player.setDanmaku(item.isSelected() ? Danmaku.empty() : item);
        refreshDanmakuDialogs();
        dismiss();
    }

    private void refreshDanmakuDialogs() {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof DanmakuDialog dialog) dialog.refresh();
    }

    private void setKeyword(CharSequence text) {
        binding.keyword.setText(text);
        binding.keyword.setSelection(text.length());
    }

    private void showProgress() {
        binding.empty.setVisibility(GONE);
        binding.recycler.setVisibility(GONE);
        binding.sourceScroller.setVisibility(GONE);
        binding.progress.setVisibility(VISIBLE);
    }

    private void hideProgress(boolean empty) {
        binding.progress.setVisibility(GONE);
        binding.recycler.setVisibility(empty ? GONE : VISIBLE);
        binding.sourceScroller.setVisibility(!empty && adapter.getSources().size() > 1 ? VISIBLE : GONE);
        binding.empty.setVisibility(empty ? VISIBLE : GONE);
    }

    private void search() {
        showProgress();
        adapter.clear();
        renderSourceTabs();
        Util.hideKeyboard(binding.keyword);
        Call call = DanmakuApi.newCall(binding.keyword.getText().toString().trim(), player.getMetadata().artist.toString().trim());
        if (call == null) {
            hideProgress(true);
            Notify.show(R.string.danmaku_api_invalid);
            return;
        }
        call.enqueue(this);
    }

    private void onSuccess(List<Danmaku> items) {
        syncCurrentState(items);
        adapter.addAll(items);
        renderSourceTabs();
        hideProgress(items.isEmpty());
        focusResultGroup();
    }

    private void syncCurrentState(List<Danmaku> items) {
        adapter.setCurrentEpisode(getCurrentEpisodeText());
        String selectedUrl = getSelectedDanmakuUrl();
        for (Danmaku item : items) item.setSelected(!TextUtils.isEmpty(selectedUrl) && selectedUrl.equals(item.getUrl()));
    }

    private CharSequence getCurrentEpisodeText() {
        MediaMetadata metadata = player == null ? null : player.getMetadata();
        if (metadata == null) return "";
        return !TextUtils.isEmpty(metadata.artist) ? metadata.artist : metadata.title;
    }

    private String getSelectedDanmakuUrl() {
        if (player == null || player.getDanmakus() == null) return "";
        for (Danmaku item : player.getDanmakus()) if (item != null && item.isSelected()) return item.getUrl();
        return "";
    }

    private void onError(Exception e) {
        hideProgress(true);
        Notify.show(e.getMessage());
    }

    private void renderSourceTabs() {
        sourceViews.clear();
        binding.sourceTabs.removeAllViews();
        List<DanmakuAdapter.SourceGroup> sources = adapter.getSources();
        binding.sourceScroller.setVisibility(sources.size() > 1 ? VISIBLE : GONE);
        for (int i = 0; i < sources.size(); i++) {
            DanmakuAdapter.SourceGroup source = sources.get(i);
            MaterialTextView view = createSourceView(source);
            LinearLayoutCompat.LayoutParams params = createSourceParams(sources.size(), i);
            binding.sourceTabs.addView(view, params);
            sourceViews.add(view);
        }
        updateSourceTabs();
    }

    private LinearLayoutCompat.LayoutParams createSourceParams(int count, int index) {
        int height = ResUtil.dp2px(Util.isLeanback() ? 42 : 36);
        LinearLayoutCompat.LayoutParams params = count <= 3 ? new LinearLayoutCompat.LayoutParams(0, height, 1) : new LinearLayoutCompat.LayoutParams(ResUtil.dp2px(Util.isLeanback() ? 150 : 112), height);
        if (index > 0) params.setMarginStart(ResUtil.dp2px(Util.isLeanback() ? 10 : 8));
        return params;
    }

    private MaterialTextView createSourceView(DanmakuAdapter.SourceGroup source) {
        MaterialTextView view = new MaterialTextView(requireContext());
        String name = DanmakuAdapter.displaySource(requireContext(), source.source());
        view.setText(name);
        view.setTag(source.source());
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextSize(Util.isLeanback() ? 15 : 13);
        view.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.selector_danmaku_source_tab_text));
        view.setBackgroundResource(R.drawable.selector_danmaku_source_tab);
        view.setClickable(true);
        view.setFocusable(true);
        view.setContentDescription(getString(R.string.danmaku_source_count, source.count()) + " " + name);
        view.setOnClickListener(this::onSourceClick);
        return view;
    }

    private void onSourceClick(View view) {
        Object tag = view.getTag();
        if (!(tag instanceof String source)) return;
        adapter.setSelectedSource(source);
        updateSourceTabs();
        binding.recycler.scrollToPosition(0);
        if (Util.isLeanback()) view.requestFocus();
    }

    private void updateSourceTabs() {
        String selected = adapter.getSelectedSource();
        for (MaterialTextView view : sourceViews) view.setSelected(selected.equals(view.getTag()));
    }

    private boolean focusResultGroup() {
        if (Util.isLeanback() && binding.sourceScroller.getVisibility() == VISIBLE && !sourceViews.isEmpty()) {
            for (MaterialTextView view : sourceViews) {
                if (!view.isSelected()) continue;
                return view.requestFocus();
            }
            return sourceViews.get(0).requestFocus();
        }
        return binding.recycler.requestFocus();
    }

    @Override
    public void onResponse(@NonNull Call call, @NonNull Response response) {
        try {
            List<Danmaku> items = Danmaku.arrayFrom(response.body().string());
            if (items.isEmpty()) throw new Exception(ResUtil.getString(R.string.error_empty));
            else App.post(() -> onSuccess(items));
        } catch (Exception e) {
            App.post(() -> onError(e));
        }
    }

    @Override
    public void onFailure(@NonNull Call call, @NonNull IOException e) {
        App.post(() -> onError(e));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        DanmakuApi.cancel();
    }

    @Override
    protected boolean transparent() {
        return !Util.isLeanback();
    }

    @Override
    protected boolean stableOverlay() {
        return !Util.isLeanback();
    }
}
