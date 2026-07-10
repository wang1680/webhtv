package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.DisabledDefaultRuleStore;
import com.fongmi.android.tv.api.config.RuleConfig;
import com.fongmi.android.tv.api.config.UserAdRuleStore;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.UserAdRule;
import com.fongmi.android.tv.databinding.DialogAdRuleManageBinding;
import com.fongmi.android.tv.ui.adapter.AdRuleAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class AdRuleManageDialog extends BaseAlertDialog implements AdRuleAdapter.OnClickListener {

    private DialogAdRuleManageBinding binding;
    private AdRuleAdapter adapter;
    private Callback callback;

    public interface Callback {
        void onRuleChanged();
    }

    public static AdRuleManageDialog create() {
        return new AdRuleManageDialog();
    }

    public void show(FragmentActivity activity, Callback callback) {
        this.callback = callback;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogAdRuleManageBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new AdRuleAdapter(this);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        binding.recycler.setAdapter(adapter);
        loadData();
    }

    @Override
    protected void initEvent() {
        binding.add.setOnClickListener(v -> onAddManual());
        binding.stats.setOnClickListener(v -> onStats());
    }

    private void loadData() {
        List<AdRuleAdapter.RuleItem> items = new ArrayList<>();

        // AI 识别规则 + 手动添加规则
        List<UserAdRule> userRules = UserAdRuleStore.load();
        for (UserAdRule rule : userRules) {
            items.add(AdRuleAdapter.RuleItem.fromUser(rule));
        }

        // APP 默认规则
        List<Rule> defaultRules = RuleConfig.get().getDefaultRules();
        for (Rule rule : defaultRules) {
            items.add(AdRuleAdapter.RuleItem.fromDefault(rule));
        }

        adapter.setItems(items);

        // 空态提示(仅当两部分都为空时显示)
        boolean isEmpty = userRules.isEmpty() && defaultRules.isEmpty();
        binding.customEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

        // 更新分区标题计数
        binding.serverRules.setText(getString(R.string.ad_rule_section_summary, userRules.size(), defaultRules.size()));
    }

    private void onAddManual() {
        AdRuleEditDialog.create(null).show(requireActivity(), this::onRuleEdited);
    }

    private void onStats() {
        AdBlockStatsDialog.create((FragmentActivity) requireActivity()).show();
    }

    private void onRuleEdited() {
        loadData();
        if (callback != null) callback.onRuleChanged();
    }

    @Override
    public void onUserRuleClick(UserAdRule item) {
        AdRuleEditDialog.create(item).show(requireActivity(), this::onRuleEdited);
    }

    @Override
    public void onDefaultRuleClick(Rule rule, String ruleId, boolean currentEnabled) {
        // 默认规则点击:弹确认框,切换禁用状态
        String message = currentEnabled
                ? getString(R.string.ad_rule_default_disable_confirm)
                : getString(R.string.ad_rule_default_enable_confirm);
        new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(rule.getName())
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    DisabledDefaultRuleStore.setDisabled(ruleId, !currentEnabled);
                    loadData();
                    if (callback != null) callback.onRuleChanged();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onUserToggleClick(UserAdRule item, boolean enabled) {
        item.setEnabled(enabled);
        UserAdRuleStore.update(item);
        if (callback != null) callback.onRuleChanged();
    }

    @Override
    public void onDefaultToggleClick(String ruleId, boolean enabled) {
        DisabledDefaultRuleStore.setDisabled(ruleId, !enabled);
        if (callback != null) callback.onRuleChanged();
    }

    @Override
    public void onDeleteClick(UserAdRule item) {
        UserAdRuleStore.delete(item.getId());
        if (adapter.removeUserRule(item) == 0) {
            // 若删除后列表为空,重新加载以显示空态
            loadData();
        }
        if (callback != null) callback.onRuleChanged();
    }

    @Override
    public void onStart() {
        super.onStart();
        configureWindow();
    }

    private void configureWindow() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        Window window = getDialog().getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(requireContext());
        int width = Math.min(Math.round(ResUtil.getScreenWidth(requireContext()) * (land ? 0.6f : 0.9f)), ResUtil.dp2px(560));
        params.width = Math.max(width, ResUtil.dp2px(320));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
    }
}
