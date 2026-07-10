package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.api.config.DisabledDefaultRuleStore;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.UserAdRule;
import com.fongmi.android.tv.databinding.AdapterAdRuleBinding;
import com.fongmi.android.tv.utils.RuleIdUtil;

import java.util.ArrayList;
import java.util.List;

public class AdRuleAdapter extends RecyclerView.Adapter<AdRuleAdapter.ViewHolder> {

    public enum RuleType { USER_RULE, DEFAULT_RULE }

    public static class RuleItem {
        private final RuleType type;
        private UserAdRule userRule;
        private Rule defaultRule;
        private String defaultRuleId;

        public static RuleItem fromUser(UserAdRule rule) {
            RuleItem item = new RuleItem(RuleType.USER_RULE);
            item.userRule = rule;
            return item;
        }

        public static RuleItem fromDefault(Rule rule) {
            RuleItem item = new RuleItem(RuleType.DEFAULT_RULE);
            item.defaultRule = rule;
            item.defaultRuleId = RuleIdUtil.computeRuleId(rule);
            return item;
        }

        private RuleItem(RuleType type) {
            this.type = type;
        }

        public RuleType getType() { return type; }
        public UserAdRule getUserRule() { return userRule; }
        public Rule getDefaultRule() { return defaultRule; }
        public String getDefaultRuleId() { return defaultRuleId; }

        public String getName() {
            return type == RuleType.USER_RULE ? userRule.getName() : defaultRule.getName();
        }

        public String getSummary() {
            if (type == RuleType.USER_RULE) return userRule.getSummary();
            // 默认规则摘要: 域名 N · URL规则 N · 白名单 N
            return "域名 " + defaultRule.getHosts().size() + " · URL规则 " + defaultRule.getRegex().size() + " · 白名单 " + defaultRule.getExclude().size();
        }

        public boolean isEnabled() {
            if (type == RuleType.USER_RULE) return userRule.isEnabled();
            return !DisabledDefaultRuleStore.isDisabled(defaultRuleId);
        }

        public boolean isEditable() {
            return type == RuleType.USER_RULE;
        }
    }

    private final OnClickListener listener;
    private List<RuleItem> mItems;

    public AdRuleAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {
        void onUserRuleClick(UserAdRule item);
        void onDefaultRuleClick(Rule rule, String ruleId, boolean currentEnabled);
        void onUserToggleClick(UserAdRule item, boolean enabled);
        void onDefaultToggleClick(String ruleId, boolean enabled);
        void onDeleteClick(UserAdRule item);
    }

    public AdRuleAdapter setItems(List<RuleItem> items) {
        this.mItems = items;
        notifyDataSetChanged();
        return this;
    }

    public int removeUserRule(UserAdRule rule) {
        int position = -1;
        for (int i = 0; i < mItems.size(); i++) {
            RuleItem item = mItems.get(i);
            if (item.getType() == RuleType.USER_RULE && item.getUserRule() == rule) {
                position = i;
                break;
            }
        }
        if (position == -1) return -1;
        mItems.remove(position);
        notifyItemRemoved(position);
        return getItemCount();
    }

    @Override
    public int getItemCount() {
        return mItems == null ? 0 : mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterAdRuleBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RuleItem item = mItems.get(position);
        holder.binding.name.setText(item.getName());
        holder.binding.summary.setText(item.getSummary());
        holder.binding.toggle.setChecked(item.isEnabled());

        // 用户规则:可点击编辑,可删除
        // 默认规则:点击弹确认框,不显示删除按钮
        if (item.getType() == RuleType.USER_RULE) {
            holder.binding.text.setOnClickListener(v -> listener.onUserRuleClick(item.getUserRule()));
            holder.binding.toggle.setOnClickListener(v -> listener.onUserToggleClick(item.getUserRule(), holder.binding.toggle.isChecked()));
            holder.binding.delete.setVisibility(View.VISIBLE);
            holder.binding.delete.setOnClickListener(v -> listener.onDeleteClick(item.getUserRule()));
        } else {
            holder.binding.text.setOnClickListener(v -> listener.onDefaultRuleClick(item.getDefaultRule(), item.getDefaultRuleId(), item.isEnabled()));
            holder.binding.toggle.setOnClickListener(v -> listener.onDefaultToggleClick(item.getDefaultRuleId(), holder.binding.toggle.isChecked()));
            holder.binding.delete.setVisibility(View.GONE);
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterAdRuleBinding binding;

        public ViewHolder(@NonNull AdapterAdRuleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
