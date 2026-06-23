package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.databinding.DialogEpisodeListBinding;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeGroupAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.List;

public class EpisodeListDialog extends AppCompatDialogFragment implements FlagAdapter.OnClickListener, EpisodeGroupAdapter.OnClickListener, EpisodeAdapter.OnClickListener {

    private DialogEpisodeListBinding binding;
    private EpisodeGroupAdapter groupAdapter;
    private EpisodeAdapter episodeAdapter;
    private SpaceItemDecoration episodeDecoration;
    private FlagAdapter flagAdapter;
    private List<Flag> flags;
    private int episodeSpanCount = 4;
    private boolean reverse;

    public static EpisodeListDialog create() {
        return new EpisodeListDialog();
    }

    public EpisodeListDialog flags(List<Flag> flags) {
        this.flags = flags;
        return this;
    }

    public EpisodeListDialog reverse(boolean reverse) {
        this.reverse = reverse;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof EpisodeListDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        configureWindow(dialog);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        configureWindow(getDialog());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogEpisodeListBinding.inflate(inflater, container, false);
        FrameLayout overlay = new FrameLayout(requireContext());
        overlay.setBackgroundColor(Color.TRANSPARENT);
        overlay.setOnClickListener(view -> dismiss());
        binding.getRoot().setClickable(true);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(getWidth(), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        overlay.addView(binding.getRoot(), params);
        return overlay;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView();
    }

    private int getWidth() {
        int screen = ResUtil.getScreenWidth(requireContext());
        return Math.max(ResUtil.dp2px(360), Math.min(ResUtil.dp2px(560), Math.round(screen * 0.44f)));
    }

    private void configureWindow(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setDimAmount(0f);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        Util.hideSystemUI(window);
    }

    private void initView() {
        setRecyclerView();
        flagAdapter.addAll(flags == null ? new ArrayList<>() : flags);
        setGroups(getSelectedFlag());
        binding.flag.scrollToPosition(flagAdapter.getPosition());
    }

    private void setRecyclerView() {
        binding.flag.setHasFixedSize(true);
        binding.flag.setItemAnimator(null);
        binding.flag.setAdapter(flagAdapter = new FlagAdapter(this));
        binding.group.setHasFixedSize(true);
        binding.group.setItemAnimator(null);
        binding.group.setAdapter(groupAdapter = new EpisodeGroupAdapter(this));
        binding.episode.setHasFixedSize(true);
        binding.episode.setItemAnimator(null);
        binding.episode.setLayoutManager(new GridLayoutManager(requireContext(), episodeSpanCount));
        binding.episode.addItemDecoration(episodeDecoration = new SpaceItemDecoration(episodeSpanCount, 8));
        binding.episode.setAdapter(episodeAdapter = new EpisodeAdapter(this, ViewType.GRID));
    }

    private Flag getSelectedFlag() {
        if (flagAdapter.isEmpty()) return null;
        return flagAdapter.get(flagAdapter.getPosition());
    }

    private void setGroups(Flag flag) {
        if (flag == null) return;
        List<Episode> episodes = flag.getEpisodes();
        groupAdapter.addAll(EpisodeGroupAdapter.build(episodes.size(), getSelectedEpisodePosition(episodes), reverse));
        EpisodeGroupAdapter.Group group = groupAdapter.isEmpty() ? null : groupAdapter.getItems().get(groupAdapter.getPosition());
        setEpisodes(episodes, group);
        binding.group.scrollToPosition(groupAdapter.getPosition());
    }

    private void setEpisodes(List<Episode> episodes, EpisodeGroupAdapter.Group group) {
        if (group == null) {
            updateEpisodeSpan(episodes);
            episodeAdapter.addAll(episodes);
            return;
        }
        int start = Math.max(0, Math.min(group.start, episodes.size()));
        int end = Math.max(start, Math.min(group.end, episodes.size()));
        ArrayList<Episode> visible = new ArrayList<>(episodes.subList(start, end));
        updateEpisodeSpan(visible);
        episodeAdapter.addAll(visible);
        binding.episode.scrollToPosition(episodeAdapter.getPosition());
    }

    private void updateEpisodeSpan(List<Episode> episodes) {
        int span = getEpisodeSpan(episodes);
        if (span == episodeSpanCount) return;
        episodeSpanCount = span;
        binding.episode.setLayoutManager(new GridLayoutManager(requireContext(), episodeSpanCount));
        if (episodeDecoration != null) binding.episode.removeItemDecoration(episodeDecoration);
        binding.episode.addItemDecoration(episodeDecoration = new SpaceItemDecoration(episodeSpanCount, 8));
    }

    private int getEpisodeSpan(List<Episode> episodes) {
        int maxLen = 0;
        for (Episode item : episodes) maxLen = Math.max(maxLen, item.getDesc().concat(item.getName()).length());
        int ideal = maxLen >= 14 ? 160 : maxLen >= 10 ? 130 : maxLen >= 7 ? 104 : 80;
        int available = Math.max(ResUtil.dp2px(240), getWidth() - ResUtil.dp2px(28));
        int span = available / ResUtil.dp2px(ideal);
        return Math.max(2, Math.min(4, span));
    }

    private int getSelectedEpisodePosition(List<Episode> episodes) {
        for (int i = 0; i < episodes.size(); i++) if (episodes.get(i).isSelected()) return i;
        return 0;
    }

    @Override
    public void onItemClick(Flag item) {
        ((FlagAdapter.OnClickListener) requireActivity()).onItemClick(item);
        flagAdapter.notifyItemRangeChanged(0, flagAdapter.getItemCount());
        setGroups(item);
    }

    @Override
    public void onItemClick(EpisodeGroupAdapter.Group item) {
        groupAdapter.setSelected(item);
        Flag flag = getSelectedFlag();
        if (flag != null) setEpisodes(flag.getEpisodes(), item);
        binding.group.scrollToPosition(groupAdapter.getPosition());
    }

    @Override
    public void onItemClick(Episode item) {
        ((EpisodeAdapter.OnClickListener) requireActivity()).onItemClick(item);
        dismiss();
    }
}
