package com.fongmi.android.tv.ui.base;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class BaseActivitySystemBarTest {

    @Test
    public void mobileBaseActivityStartsWithLightColoredSystemBarIcons() throws Exception {
        String source = readMobileSource("ui", "base", "BaseActivity.java");
        int method = source.indexOf("private void enableEdgeToEdge()");
        int end = source.indexOf("private void enableDynamicColor()", method);
        String body = source.substring(method, end);

        assertTrue("mobile activities must start with light-colored system bar icons over the app backdrop",
                body.contains("SystemBarStyle.dark(Color.TRANSPARENT)"));
        assertFalse("base edge-to-edge setup must not choose dark icons from the light system theme",
                body.contains("SystemBarStyle.auto("));
    }

    @Test
    public void nativeHomeScreensKeepLightColoredIconsWhenWebHomeIsInactive() throws Exception {
        String source = readMobileSource("ui", "activity", "WebHomeChromeController.java");
        int method = source.indexOf("private void applySystemBars()");
        int end = source.indexOf("private void applyCutoutMode()", method);
        String body = source.substring(method, end);

        assertTrue("system-bar updates must distinguish the active WebHome from native screens",
                body.contains("boolean webHomeActive = isActive();"));
        assertTrue("native screens must not request dark status-bar icons",
                body.contains("webHomeActive && useDarkIcons(effectiveOptions.statusBarStyle)"));
        assertTrue("native screens must not request dark navigation-bar icons",
                body.contains("webHomeActive && useDarkIcons(effectiveOptions.navigationBarStyle)"));
        int resolver = source.indexOf("private boolean useDarkIcons(String style)");
        String resolverBody = source.substring(resolver);
        assertTrue("AUTO WebHome chrome must default to light-colored icons; only an explicit DARK style may opt in",
                resolverBody.contains("return WebHomeChromeOptions.STYLE_DARK.equals(style);"));
    }

    @Test
    public void nativeSystemBarOverridesKeepLightColoredIcons() throws Exception {
        String live = readMobileSource("ui", "activity", "LiveActivity.java");
        String detail = readMainSource("ui", "activity", "TmdbDetailActivity.java");

        assertTrue("embedded Live must use light-colored status and navigation icons",
                live.contains("setAppearanceLightStatusBars(false)")
                        && live.contains("setAppearanceLightNavigationBars(false)"));
        assertTrue("TMDB detail must use the same light-colored system-bar icons as other native screens",
                detail.contains("setAppearanceLightStatusBars(false)")
                        && detail.contains("setAppearanceLightNavigationBars(false)"));
    }

    @Test
    public void vodUsesAnOptionalWebViewAndStillOpensWhenTheProviderIsUnavailable() throws Exception {
        String home = readMobileSource("ui", "activity", "HomeActivity.java");
        String vod = readMobileSource("ui", "fragment", "VodFragment.java");
        String layout = readMobileLayout("fragment_vod.xml");

        assertFalse("Home navigation must not silently reject the Vod tab",
                home.contains("if (position == 0 && !canCreateWebView()) return false;"));
        assertFalse("Vod layout must not inflate android.webkit.WebView before errors can be caught",
                layout.contains("<WebView"));
        assertTrue("Vod layout must provide a safe container for the optional WebView",
                layout.contains("<FrameLayout") && layout.contains("android:id=\"@+id/homeWeb\""));
        assertTrue("Vod must create the WebView programmatically inside a guarded path",
                vod.contains("private WebView mHomeWeb;")
                        && vod.contains("mHomeWeb = new WebView(requireContext());")
                        && vod.contains("mBinding.homeWeb.addView(mHomeWeb")
                        && vod.contains("catch (RuntimeException | LinkageError error)"));
    }
    @Test
    public void webHomeControllerIsPublishedOnlyAfterSuccessfulInitialization() throws Exception {
        String source = readMainSource("web", "HomeWebController.java");
        int constructor = source.indexOf("public HomeWebController(Activity activity, WebView webView, Listener listener, boolean debugTools)");
        int end = source.indexOf("public static void requestExtensionReload()", constructor);
        String body = source.substring(constructor, end);

        assertTrue("a failed WebView initialization must not leave a half-initialized global controller",
                body.indexOf("init();") < body.indexOf("active = this;"));
    }
    private static String readMobileLayout(String name) throws Exception {
        Path moduleRelative = Path.of("src", "mobile", "res", "layout", name);
        Path sourcePath = Files.exists(moduleRelative) ? moduleRelative : Path.of("app", "src", "mobile", "res", "layout", name);
        return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
    }
    private static String readMainSource(String... path) throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv"));
        for (String part : path) sourcePath = sourcePath.resolve(part);
        return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
    }

    private static String readMobileSource(String... path) throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv"));
        for (String part : path) sourcePath = sourcePath.resolve(part);
        return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findMobileJavaPath() {
        Path moduleRelative = Path.of("src", "mobile", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "java");
    }
}
