package com.fongmi.android.tv.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.databinding.AdapterDanmakuBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DanmakuAdapter extends RecyclerView.Adapter<DanmakuAdapter.ItemHolder> {

    private static final int EPISODE_UNKNOWN = Integer.MAX_VALUE;
    private static final Pattern SOURCE_FROM = Pattern.compile("\\bfrom\\s+([^\\s\\-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SOURCE_BRACKET = Pattern.compile("【([^】]{2,16})】");
    private static final Pattern SOURCE_HOST = Pattern.compile("https?://([^/]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SOURCE_INLINE = Pattern.compile("\\s*\\bfrom\\s+[^\\s\\-]+\\s*-?\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern SOURCE_MARK = Pattern.compile("[【\\[]([^】\\]]{1,20})[】\\]]");
    private static final Pattern EPISODE_CN = Pattern.compile("第\\s*0*([0-9]{1,5})\\s*[集话話回期章节節]");
    private static final Pattern EPISODE_SEASON = Pattern.compile("\\bS\\d{1,2}\\s*E\\s*0*([0-9]{1,5})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EPISODE_EP = Pattern.compile("\\b(?:EP|E|Episode)\\s*0*([0-9]{1,5})\\b", Pattern.CASE_INSENSITIVE);

    private final OnClickListener listener;
    private final List<Danmaku> mItems;
    private final List<Row> mRows;
    private final List<SourceGroup> mSources;
    private final Map<String, List<Danmaku>> mGrouped;
    private boolean groupBySource;
    private String selectedSource;
    private int currentEpisode = EPISODE_UNKNOWN;

    public DanmakuAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
        this.mRows = new ArrayList<>();
        this.mSources = new ArrayList<>();
        this.mGrouped = new LinkedHashMap<>();
    }

    public interface OnClickListener {

        void onItemClick(Danmaku item);
    }

    public DanmakuAdapter groupBySource(boolean groupBySource) {
        if (this.groupBySource == groupBySource) return this;
        this.groupBySource = groupBySource;
        rebuildRows();
        notifyDataSetChanged();
        return this;
    }

    public List<SourceGroup> getSources() {
        return Collections.unmodifiableList(mSources);
    }

    public String getSelectedSource() {
        return selectedSource == null ? "" : selectedSource;
    }

    public boolean setSelectedSource(String source) {
        if (!groupBySource || source == null || source.equals(selectedSource)) return false;
        selectedSource = source;
        rebuildRows();
        notifyDataSetChanged();
        return true;
    }

    public DanmakuAdapter setCurrentEpisode(CharSequence text) {
        int episode = parseEpisode(text);
        if (currentEpisode == episode) return this;
        currentEpisode = episode;
        rebuildRows();
        notifyDataSetChanged();
        return this;
    }

    public void clear() {
        int size = mRows.size();
        mItems.clear();
        mRows.clear();
        mSources.clear();
        mGrouped.clear();
        selectedSource = null;
        if (size > 0) notifyItemRangeRemoved(0, size);
    }

    public DanmakuAdapter addAll(List<Danmaku> items) {
        if (items == null) return this;
        int start = mRows.size();
        mItems.addAll(items);
        rebuildRows();
        if (groupBySource) {
            notifyDataSetChanged();
            return this;
        }
        int count = mRows.size() - start;
        if (count > 0) notifyItemRangeInserted(start, count);
        return this;
    }

    public int getSelected() {
        for (int i = 0; i < mRows.size(); i++) {
            Danmaku item = mRows.get(i).item;
            if (item != null && item.isSelected()) return i;
        }
        return 0;
    }

    @Override
    public int getItemCount() {
        return mRows.size();
    }

    @Override
    public ItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ItemHolder(AdapterDanmakuBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ItemHolder holder, int position) {
        Row row = mRows.get(position);
        Danmaku item = row.item;
        holder.binding.source.setVisibility(groupBySource ? View.GONE : View.VISIBLE);
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.getRoot().setActivated(isCurrentEpisode(item) && !item.isSelected());
        holder.binding.source.setText(displaySource(holder.itemView.getContext(), row.source));
        holder.binding.text.setText(groupBySource ? getGroupedName(item, row.source) : item.getName());
        holder.binding.text.setSelected(item.isSelected());
        holder.binding.text.setActivated(isCurrentEpisode(item) && !item.isSelected());
    }

    private void rebuildRows() {
        mRows.clear();
        mSources.clear();
        mGrouped.clear();
        if (!groupBySource) {
            for (Danmaku item : mItems) mRows.add(Row.item(getSource(item), item));
            return;
        }
        Map<String, List<IndexedDanmaku>> groups = new LinkedHashMap<>();
        for (int i = 0; i < mItems.size(); i++) {
            Danmaku item = mItems.get(i);
            String source = getSource(item);
            groups.computeIfAbsent(source, key -> new ArrayList<>()).add(new IndexedDanmaku(item, i));
        }
        for (Map.Entry<String, List<IndexedDanmaku>> entry : groups.entrySet()) {
            entry.getValue().sort(Comparator.comparingInt((IndexedDanmaku item) -> getEpisode(item.danmaku)).thenComparingInt(item -> item.index));
            List<Danmaku> items = new ArrayList<>();
            for (IndexedDanmaku item : entry.getValue()) items.add(item.danmaku);
            mGrouped.put(entry.getKey(), items);
            mSources.add(new SourceGroup(entry.getKey(), items.size()));
        }
        selectDefaultSource();
        List<Danmaku> items = selectedSource == null ? Collections.emptyList() : mGrouped.get(selectedSource);
        if (items == null) return;
        for (Danmaku item : items) mRows.add(Row.item(selectedSource, item));
    }

    private void selectDefaultSource() {
        if (selectedSource != null && mGrouped.containsKey(selectedSource)) return;
        selectedSource = null;
        for (Map.Entry<String, List<Danmaku>> entry : mGrouped.entrySet()) {
            for (Danmaku item : entry.getValue()) {
                if (!item.isSelected()) continue;
                selectedSource = entry.getKey();
                return;
            }
        }
        for (Map.Entry<String, List<Danmaku>> entry : mGrouped.entrySet()) {
            for (Danmaku item : entry.getValue()) {
                if (!isCurrentEpisode(item)) continue;
                selectedSource = entry.getKey();
                return;
            }
        }
        if (!mSources.isEmpty()) selectedSource = mSources.get(0).source;
    }

    private String getSource(@NonNull Danmaku item) {
        String source = matchSource(SOURCE_FROM, item.getName(), false);
        if (source.isEmpty()) source = matchSource(SOURCE_BRACKET, item.getName(), true);
        if (source.isEmpty()) source = matchSource(SOURCE_HOST, item.getUrl(), false);
        if (source.isEmpty()) return "";
        return normalizeSource(source);
    }

    public static String displaySource(@NonNull Context context, String source) {
        return source == null || source.isEmpty() ? context.getString(R.string.danmaku_source_default) : source;
    }

    private String getGroupedName(@NonNull Danmaku item, @NonNull String source) {
        String name = SOURCE_INLINE.matcher(item.getName()).replaceAll(" ");
        Matcher matcher = SOURCE_MARK.matcher(name);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String candidate = normalizeSource(matcher.group(1));
            matcher.appendReplacement(buffer, source.equals(candidate) ? "" : Matcher.quoteReplacement(matcher.group()));
        }
        matcher.appendTail(buffer);
        return buffer.toString().replaceAll("\\s{2,}", " ").trim();
    }

    private String matchSource(Pattern pattern, String text, boolean last) {
        if (text == null || text.isEmpty()) return "";
        Matcher matcher = pattern.matcher(text);
        String value = "";
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (last && isGenericSource(candidate)) continue;
            value = candidate;
            if (!last) break;
        }
        return value == null ? "" : value;
    }

    private boolean isGenericSource(String value) {
        if (value == null) return true;
        value = value.trim();
        return value.equals("电影") || value.equals("电视剧") || value.equals("剧集") || value.equals("综艺") || value.equals("动漫") || value.equals("动画") || value.equals("纪录片") || value.equalsIgnoreCase("movie") || value.equalsIgnoreCase("tv");
    }

    private String normalizeSource(String value) {
        value = value.replace("www.", "").replace("【", "").replace("】", "").replace("[", "").replace("]", "").trim();
        int dot = value.indexOf('.');
        if (dot > 0) value = value.substring(0, dot);
        if (value.length() > 10) value = value.substring(0, 10);
        return value.matches("[A-Za-z0-9_\\-]+") ? value.toUpperCase(Locale.ROOT) : value;
    }

    public static int parseEpisode(CharSequence text) {
        int episode = matchEpisode(EPISODE_CN, text);
        if (episode == EPISODE_UNKNOWN) episode = matchEpisode(EPISODE_SEASON, text);
        if (episode == EPISODE_UNKNOWN) episode = matchEpisode(EPISODE_EP, text);
        return episode;
    }

    public static int getEpisode(@NonNull Danmaku item) {
        return parseEpisode(item.getName());
    }

    private boolean isCurrentEpisode(@NonNull Danmaku item) {
        return currentEpisode != EPISODE_UNKNOWN && getEpisode(item) == currentEpisode;
    }

    private static int matchEpisode(Pattern pattern, CharSequence text) {
        if (text == null || text.length() == 0) return EPISODE_UNKNOWN;
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return EPISODE_UNKNOWN;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception ignored) {
            return EPISODE_UNKNOWN;
        }
    }

    public class ItemHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final AdapterDanmakuBinding binding;

        public ItemHolder(@NonNull AdapterDanmakuBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;
            Danmaku item = mRows.get(position).item;
            if (item != null) listener.onItemClick(item);
        }
    }

    public record SourceGroup(String source, int count) {
    }

    private record Row(String source, Danmaku item) {
        static Row item(String source, Danmaku item) {
            return new Row(source, item);
        }
    }

    private record IndexedDanmaku(Danmaku danmaku, int index) {
    }
}
