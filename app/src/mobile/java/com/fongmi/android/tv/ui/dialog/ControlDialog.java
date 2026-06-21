package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.databinding.DialogControlBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.player.lut.LutStore;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Timer;
import com.google.android.material.textview.MaterialTextView;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ControlDialog extends BaseBottomSheetDialog implements ParseAdapter.OnClickListener {

    private final String[] scale;
    private DialogControlBinding binding;
    private ActivityVideoBinding parent;
    private List<TextView> scales;
    private List<TextView> speeds;
    private LutAdapter lutAdapter;
    private PlayerManager player;
    private History history;
    private boolean parse;

    public ControlDialog() {
        this.scale = ResUtil.getStringArray(R.array.select_scale);
    }

    public static ControlDialog create() {
        return new ControlDialog();
    }

    public ControlDialog parent(ActivityVideoBinding parent) {
        this.parent = parent;
        return this;
    }

    public ControlDialog history(History history) {
        this.history = history;
        return this;
    }

    public ControlDialog parse(boolean parse) {
        this.parse = parse;
        return this;
    }

    public ControlDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public ControlDialog show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof ControlDialog) return this;
        show(activity.getSupportFragmentManager(), null);
        return this;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        binding = DialogControlBinding.inflate(inflater, container, false);
        scales = Arrays.asList(binding.scale0, binding.scale1, binding.scale2, binding.scale3, binding.scale4);
        speeds = Arrays.asList(binding.speed05, binding.speed075, binding.speed10, binding.speed12, binding.speed15, binding.speed20, binding.speed30);
        return binding;
    }

    @Override
    protected void initView() {
        binding.decode.setText(parent.control.action.decode.getText());
        binding.lut.setText(parent.control.action.lut.getText());
        binding.ending.setText(parent.control.action.ending.getText());
        binding.opening.setText(parent.control.action.opening.getText());
        binding.repeat.setSelected(parent.control.action.repeat.isSelected());
        binding.timer.setSelected(Timer.get().isRunning());
        setTrackVisible();
        setTitleVisible();
        setScaleText();
        setPlayer();
        setParse();
        setLut();
    }

    @Override
    protected void initEvent() {
        binding.timer.setOnClickListener(this::onTimer);
        binding.speed.addOnChangeListener(this::setSpeed);
        for (TextView view : speeds) view.setOnClickListener(this::setSpeedPreset);
        for (TextView view : scales) view.setOnClickListener(this::setScale);
        binding.text.setOnClickListener(v -> dismiss(parent.control.action.text));
        binding.audio.setOnClickListener(v -> dismiss(parent.control.action.audio));
        binding.video.setOnClickListener(v -> dismiss(parent.control.action.video));
        binding.title.setOnClickListener(v -> dismiss(parent.control.action.title));
        binding.player.setOnClickListener(v -> click(binding.player, parent.control.action.player));
        binding.danmaku.setOnClickListener(v -> dismiss(parent.control.action.danmaku));
        binding.repeat.setOnClickListener(v -> active(binding.repeat, parent.control.action.repeat));
        binding.decode.setOnClickListener(v -> click(binding.decode, parent.control.action.decode));
        binding.lut.setOnClickListener(v -> toggleLutList());
        binding.ending.setOnClickListener(v -> click(binding.ending, parent.control.action.ending));
        binding.opening.setOnClickListener(v -> click(binding.opening, parent.control.action.opening));
        binding.player.setOnLongClickListener(v -> longClick(binding.player, parent.control.action.player));
        binding.ending.setOnLongClickListener(v -> longClick(binding.ending, parent.control.action.ending));
        binding.opening.setOnLongClickListener(v -> longClick(binding.opening, parent.control.action.opening));
    }

    private void onTimer(View view) {
        TimerDialog.create().show(getActivity());
        dismiss();
    }

    private void setSpeed(@NonNull Slider slider, float value, boolean fromUser) {
        if (!fromUser) return;
        applySpeed(value);
    }

    private void applySpeed(float speed) {
        PlayerSetting.putDefaultSpeed(speed);
        parent.control.action.speed.setText(player.setSpeed(speed));
        setSpeedPresets();
        binding.speed.setValue(Math.max(player.getSpeed(), 0.5f));
        if (history != null) history.setSpeed(player.getSpeed());
    }

    private void setSpeedPreset(View view) {
        applySpeed(Float.parseFloat(view.getTag().toString()));
    }

    private void setSpeedPresets() {
        float speed = player.getSpeed();
        for (TextView view : speeds) view.setSelected(Math.abs(Float.parseFloat(view.getTag().toString()) - speed) < 0.01f);
    }

    private void setScaleText() {
        for (int i = 0; i < scales.size(); i++) {
            scales.get(i).setText(scale[i]);
            scales.get(i).setSelected(scales.get(i).getText().equals(parent.control.action.scale.getText()));
        }
    }

    private void setParse() {
        setParseVisible(parse);
        binding.parse.setHasFixedSize(true);
        binding.parse.setItemAnimator(null);
        binding.parse.addItemDecoration(new SpaceItemDecoration(8));
        binding.parse.setAdapter(new ParseAdapter(this, ViewType.LIGHT));
    }

    private void setLut() {
        binding.lutList.setHasFixedSize(true);
        binding.lutList.setItemAnimator(null);
        binding.lutList.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        binding.lutList.setAdapter(lutAdapter = new LutAdapter());
        lutAdapter.refresh();
    }

    private void setScale(View view) {
        for (TextView textView : scales) textView.setSelected(false);
        ((Listener) requireActivity()).onScale(Integer.parseInt(view.getTag().toString()));
        view.setSelected(true);
    }

    private void active(View view, TextView target) {
        target.performClick();
        view.setSelected(target.isSelected());
    }

    private void click(TextView view, TextView target) {
        target.performClick();
        view.setText(target.getText());
    }

    private void toggleLutList() {
        boolean show = binding.lutList.getVisibility() != View.VISIBLE;
        binding.lutText.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.lutList.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) lutAdapter.refresh();
    }

    private void selectLut(LutEntry entry) {
        if (entry.importFile) {
            ((Listener) requireActivity()).onLutImport();
            dismiss();
            return;
        }
        ((Listener) requireActivity()).onLutSelected(entry.preset);
        binding.lut.setText(parent.control.action.lut.getText());
        lutAdapter.refresh();
    }

    private boolean longClick(TextView view, TextView target) {
        target.performLongClick();
        view.setText(target.getText());
        return true;
    }

    private void dismiss(View view) {
        App.post(view::performClick, 200);
        dismiss();
    }

    public void setPlayer() {
        binding.speed.setValue(Math.max(player.getSpeed(), 0.5f));
        setSpeedPresets();
        binding.player.setText(parent.control.action.player.getText());
        binding.decode.setVisibility(parent.control.action.decode.getVisibility());
        binding.danmaku.setVisibility(parent.control.action.danmaku.getVisibility());
    }

    public void setParseVisible(boolean visible) {
        binding.parse.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.parseText.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setTrackVisible() {
        binding.text.setVisibility(parent.control.action.text.getVisibility());
        binding.audio.setVisibility(parent.control.action.audio.getVisibility());
        binding.video.setVisibility(parent.control.action.video.getVisibility());
        binding.track.setVisibility(binding.text.getVisibility() == View.GONE && binding.audio.getVisibility() == View.GONE && binding.video.getVisibility() == View.GONE ? View.GONE : View.VISIBLE);
    }

    public void setTitleVisible() {
        binding.title.setVisibility(parent.control.action.title.getVisibility());
    }

    @Override
    public void onItemClick(Parse item) {
        ((Listener) requireActivity()).onParse(item);
        binding.parse.getAdapter().notifyItemRangeChanged(0, binding.parse.getAdapter().getItemCount());
    }

    public interface Listener {

        void onScale(int tag);

        void onParse(Parse item);

        void onLutSelected(LutPreset preset);

        void onLutImport();
    }

    private static class LutEntry {

        private final LutPreset preset;
        private final boolean importFile;

        private LutEntry(LutPreset preset, boolean importFile) {
            this.preset = preset;
            this.importFile = importFile;
        }

        static LutEntry original() {
            return new LutEntry(null, false);
        }

        static LutEntry importFile() {
            return new LutEntry(null, true);
        }

        static LutEntry preset(LutPreset preset) {
            return new LutEntry(preset, false);
        }

        String getText() {
            if (importFile) return ResUtil.getString(R.string.lut_import);
            return preset == null ? ResUtil.getString(R.string.lut_original) : preset.getName();
        }

        boolean isSelected() {
            return !importFile && (preset == null ? !LutSetting.isEnabled() : LutSetting.isEnabled() && preset.getId().equals(LutSetting.getPresetId()));
        }
    }

    private class LutAdapter extends RecyclerView.Adapter<LutAdapter.ViewHolder> {

        private final List<LutEntry> items = new ArrayList<>();

        void refresh() {
            items.clear();
            items.add(LutEntry.original());
            items.add(LutEntry.importFile());
            for (LutPreset preset : LutStore.getPresets()) items.add(LutEntry.preset(preset));
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder((MaterialTextView) LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_value, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            LutEntry entry = items.get(position);
            holder.text.setText(entry.getText());
            holder.text.setSelected(entry.isSelected());
            holder.text.setOnClickListener(view -> selectLut(entry));
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
