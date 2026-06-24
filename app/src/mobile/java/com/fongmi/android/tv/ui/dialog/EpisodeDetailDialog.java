package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.service.TmdbService;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.EpisodeStillAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbPersonAdapter;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class EpisodeDetailDialog {

    public static void show(FragmentActivity activity, Episode episode) {
        if (activity == null || episode == null) return;
        TmdbEpisode tmdbEpisode = episode.getTmdbEpisode();
        if (tmdbEpisode == null) {
            showSimpleDialog(activity, episode);
            return;
        }

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_episode_detail, null);
        ImageView still = view.findViewById(R.id.still);
        TextView title = view.findViewById(R.id.title);
        TextView meta = view.findViewById(R.id.meta);
        TextView overview = view.findViewById(R.id.overview);
        TextView photoTitle = view.findViewById(R.id.photoTitle);
        RecyclerView photoList = view.findViewById(R.id.photoList);
        TextView guestsTitle = view.findViewById(R.id.guestsTitle);
        RecyclerView guestsList = view.findViewById(R.id.guestsList);

        bindBasicInfo(activity, tmdbEpisode, still, title, meta, overview);
        bindHorizontalList(photoList, 12);
        bindHorizontalList(guestsList, 12);

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(view);
        view.findViewById(R.id.close).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        applyWindowSize(dialog);

        loadEpisodeMedia(activity, tmdbEpisode, photoTitle, photoList, guestsTitle, guestsList);
    }

    private static void applyWindowSize(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.setDimAmount(0f);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    private static void bindBasicInfo(Activity activity, TmdbEpisode episode, ImageView still, TextView title, TextView meta, TextView overview) {
        if (!TextUtils.isEmpty(episode.getStillUrl())) {
            still.setVisibility(View.VISIBLE);
            Glide.with(activity)
                    .load(tmdbImageUrl(episode.getStillUrl(), "w780"))
                    .placeholder(R.color.black)
                    .error(R.color.black)
                    .centerCrop()
                    .into(still);
        } else {
            still.setVisibility(View.GONE);
        }
        title.setText(episode.getDisplayTitle());
        List<String> metas = new ArrayList<>();
        if (episode.getVoteAverage() > 0) metas.add(String.format(java.util.Locale.US, "%.1f", episode.getVoteAverage()));
        if (!TextUtils.isEmpty(episode.getDate())) metas.add(episode.getDate());
        if (episode.getRuntime() > 0) metas.add(episode.getRuntime() + "m");
        meta.setText(TextUtils.join(" / ", metas));
        meta.setVisibility(metas.isEmpty() ? View.GONE : View.VISIBLE);
        overview.setText(TextUtils.isEmpty(episode.getOverview()) ? "暂无简介" : episode.getOverview());
    }

    private static void bindHorizontalList(RecyclerView view, int spacingDp) {
        view.setLayoutManager(new LinearLayoutManager(view.getContext(), LinearLayoutManager.HORIZONTAL, false));
        view.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View child, RecyclerView parent, @NonNull RecyclerView.State state) {
                RecyclerView.Adapter<?> adapter = parent.getAdapter();
                if (adapter != null && parent.getChildAdapterPosition(child) < adapter.getItemCount() - 1) outRect.right = ResUtil.dp2px(spacingDp);
            }
        });
    }

    private static void loadEpisodeMedia(FragmentActivity activity, TmdbEpisode episode, TextView photoTitle, RecyclerView photoList, TextView guestsTitle, RecyclerView guestsList) {
        if (episode.getTmdbId() == 0) return;
        Task.execute(() -> {
            try {
                TmdbConfig config = TmdbConfig.objectFrom(Setting.getTmdbConfig());
                if (config == null || !config.isReady()) return;
                TmdbService service = new TmdbService();
                JsonObject episodeJson = service.episode(episode.getTmdbId(), episode.getSeasonNumber(), episode.getNumber(), config);
                List<String> photos = service.episodePhotos(episodeJson, config);
                List<TmdbPerson> guests = service.episodeGuests(episodeJson, config);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    bindMedia(activity, photos, guests, photoTitle, photoList, guestsTitle, guestsList);
                });
            } catch (Exception e) {
                android.util.Log.w("EpisodeDetailDialog", "load episode detail failed", e);
            }
        });
    }

    private static void bindMedia(FragmentActivity activity, List<String> photos, List<TmdbPerson> guests, TextView photoTitle, RecyclerView photoList, TextView guestsTitle, RecyclerView guestsList) {
        if (photos != null && !photos.isEmpty()) {
            photoTitle.setVisibility(View.VISIBLE);
            photoList.setVisibility(View.VISIBLE);
            photoList.setAdapter(new EpisodeStillAdapter(photos, (url, position) -> PhotoViewerDialog.show(activity, photos, position, null)));
        }
        if (guests != null && !guests.isEmpty()) {
            guestsTitle.setVisibility(View.VISIBLE);
            guestsList.setVisibility(View.VISIBLE);
            TmdbPersonAdapter adapter = new TmdbPersonAdapter(person -> TmdbPersonDialog.show(activity, person, null));
            adapter.setItems(guests);
            guestsList.setAdapter(adapter);
        }
    }

    private static void showSimpleDialog(FragmentActivity activity, Episode episode) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(episode.getName())
                .setMessage("暂无 TMDB 详细信息")
                .setPositiveButton(R.string.dialog_negative, null)
                .show();
    }

    private static String tmdbImageUrl(String url, String size) {
        if (url == null || url.isEmpty()) return "";
        String result = url.replaceFirst("(/t/p/)([^/]+)(/)", "$1" + size + "$3");
        return result.equals(url) ? url.replaceFirst("/(w\\d+|h\\d+|original)/", "/" + size + "/") : result;
    }
}
