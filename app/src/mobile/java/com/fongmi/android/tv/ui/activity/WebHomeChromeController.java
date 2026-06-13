package com.fongmi.android.tv.ui.activity;

import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.web.WebHomeChrome;
import com.fongmi.android.tv.web.WebHomeChromeOptions;
import com.fongmi.android.tv.web.WebHomeViewport;
import com.google.gson.JsonObject;

final class WebHomeChromeController {

    private static final String STATE_MODE = "webHomeChromeMode";
    private static final String STATE_PREVIOUS_MODE = "webHomeChromePreviousMode";

    interface Host {

        void onWebHomeChromeChanged(String mode);

        void onWebHomeViewportChanged(WebHomeViewport viewport);
    }

    private final HomeActivity activity;
    private final ActivityHomeBinding binding;
    private final Host host;
    private final int navigationBaseHeight;
    private WindowInsetsCompat insets;
    private WebHomeChromeOptions options;
    private WebHomeViewport viewport;
    private String mode;
    private String previousMode;
    private int safeBottomMax;

    WebHomeChromeController(HomeActivity activity, ActivityHomeBinding binding, Host host, Bundle savedInstanceState) {
        this.activity = activity;
        this.binding = binding;
        this.host = host;
        this.navigationBaseHeight = binding.navigation.getLayoutParams().height;
        this.mode = savedInstanceState == null ? WebHomeChrome.NORMAL : WebHomeChrome.normalize(savedInstanceState.getString(STATE_MODE), WebHomeChrome.NORMAL);
        this.previousMode = savedInstanceState == null ? WebHomeChrome.NORMAL : WebHomeChrome.normalize(savedInstanceState.getString(STATE_PREVIOUS_MODE), WebHomeChrome.NORMAL);
        this.options = WebHomeChromeOptions.from(null, mode);
        this.viewport = WebHomeViewport.EMPTY.withChrome(mode, isImmersive());
        init();
    }

    private void init() {
        binding.getRoot().setFitsSystemWindows(false);
        binding.chromeRestore.setOnClickListener(view -> restore());
        binding.chromeRestore.setOnLongClickListener(view -> {
            apply(WebHomeChromeOptions.normal());
            return true;
        });
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            insets = windowInsets;
            applyLayout();
            dispatchViewport();
            return windowInsets;
        });
        apply(options);
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    void save(Bundle outState) {
        outState.putString(STATE_MODE, mode);
        outState.putString(STATE_PREVIOUS_MODE, previousMode);
    }

    void applyDefault(Site site) {
        apply(WebHomeChromeOptions.fromSite(site));
    }

    void setChrome(JsonObject payload) {
        apply(WebHomeChromeOptions.from(payload, mode));
    }

    void setLegacyToolbar(boolean visible) {
        apply(visible ? WebHomeChromeOptions.normal() : WebHomeChromeOptions.legacyImmersive());
    }

    void restore() {
        apply(WebHomeChromeOptions.from(null, previousMode));
    }

    boolean consumeBack() {
        if (!isImmersive()) return false;
        restore();
        return true;
    }

    void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus && isImmersive()) Util.hideSystemUI(activity);
    }

    void onConfigurationChanged() {
        safeBottomMax = 0;
        ViewCompat.requestApplyInsets(binding.getRoot());
        dispatchViewport();
    }

    void destroy() {
        if (isImmersive()) Util.showSystemUI(activity);
    }

    String getMode() {
        return mode;
    }

    WebHomeViewport getViewport() {
        return viewport;
    }

    private void apply(WebHomeChromeOptions next) {
        String nextMode = WebHomeChrome.normalize(next.mode, WebHomeChrome.NORMAL);
        if (WebHomeChrome.IMMERSIVE.equals(nextMode) && !WebHomeChrome.IMMERSIVE.equals(mode)) previousMode = mode;
        if (!WebHomeChrome.IMMERSIVE.equals(nextMode)) previousMode = nextMode;
        mode = nextMode;
        options = next;
        applySystemBars();
        applyCutoutMode();
        applyLayout();
        dispatchViewport();
        host.onWebHomeChromeChanged(mode);
    }

    private void applySystemBars() {
        Window window = activity.getWindow();
        window.setStatusBarColor(options.topScrim);
        window.setNavigationBarColor(options.bottomScrim);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(useDarkIcons(options.statusBarStyle));
        controller.setAppearanceLightNavigationBars(useDarkIcons(options.navigationBarStyle));
        if (isImmersive()) Util.hideSystemUI(activity);
        else Util.showSystemUI(activity);
    }

    private void applyCutoutMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        WindowManager.LayoutParams params = activity.getWindow().getAttributes();
        params.layoutInDisplayCutoutMode = WebHomeChrome.hidesNativeChrome(mode) ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        activity.getWindow().setAttributes(params);
    }

    private void applyLayout() {
        boolean normal = WebHomeChrome.NORMAL.equals(mode);
        binding.navigation.setVisibility(normal ? View.VISIBLE : View.GONE);
        WebHomeViewport current = buildViewport();
        int top = normal ? current.getSafeTop() : 0;
        int bottom = normal ? current.getSafeBottom() : 0;
        binding.container.setPadding(0, top, 0, 0);
        binding.navigation.setPadding(0, 0, 0, bottom);
        ViewGroup.LayoutParams params = binding.navigation.getLayoutParams();
        int height = navigationBaseHeight + bottom;
        if (params.height != height) {
            params.height = height;
            binding.navigation.setLayoutParams(params);
        }
        RelativeLayout.LayoutParams container = (RelativeLayout.LayoutParams) binding.container.getLayoutParams();
        if (binding.navigation.getVisibility() == View.VISIBLE) container.addRule(RelativeLayout.ABOVE, binding.navigation.getId());
        else container.removeRule(RelativeLayout.ABOVE);
        binding.container.setLayoutParams(container);
        applyRestoreButton(current);
    }

    private void applyRestoreButton(WebHomeViewport current) {
        boolean visible = WebHomeChrome.IMMERSIVE.equals(mode) || "native".equals(options.restoreAffordance) && WebHomeChrome.hidesNativeChrome(mode);
        binding.chromeRestore.setVisibility(visible ? View.VISIBLE : View.GONE);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) binding.chromeRestore.getLayoutParams();
        int margin = ResUtil.dp2px(8);
        int top = current.getSafeTop() + margin;
        int right = current.getSafeRight() + margin;
        if (params.topMargin == top && params.rightMargin == right) return;
        params.topMargin = top;
        params.rightMargin = right;
        binding.chromeRestore.setLayoutParams(params);
    }

    private void dispatchViewport() {
        viewport = buildViewport();
        host.onWebHomeViewportChanged(viewport);
    }

    private WebHomeViewport buildViewport() {
        WebHomeViewport current = WebHomeViewport.from(insets, mode, safeBottomMax);
        safeBottomMax = Math.max(safeBottomMax, current.getSafeBottom());
        return WebHomeViewport.from(insets, mode, safeBottomMax);
    }

    private boolean isImmersive() {
        return WebHomeChrome.IMMERSIVE.equals(mode);
    }

    private boolean useDarkIcons(String style) {
        if (WebHomeChromeOptions.STYLE_DARK.equals(style)) return true;
        if (WebHomeChromeOptions.STYLE_LIGHT.equals(style)) return false;
        int mask = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mask != Configuration.UI_MODE_NIGHT_YES;
    }
}
