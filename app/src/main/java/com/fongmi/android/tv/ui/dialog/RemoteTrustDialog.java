package com.fongmi.android.tv.ui.dialog;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.remote.RemoteAgent;
import com.fongmi.android.tv.remote.RemoteAgentService;
import com.fongmi.android.tv.remote.RemoteClient;
import com.fongmi.android.tv.remote.RemoteModels.BindCodeResponse;
import com.fongmi.android.tv.remote.RemoteModels.ClaimResponse;
import com.fongmi.android.tv.remote.RemoteModels.CommandDetailResponse;
import com.fongmi.android.tv.remote.RemoteModels.CommandResponse;
import com.fongmi.android.tv.remote.RemoteModels.DevicesResponse;
import com.fongmi.android.tv.remote.RemoteModels.RemoteBindGrant;
import com.fongmi.android.tv.remote.RemoteModels.RemoteCapabilities;
import com.fongmi.android.tv.remote.RemoteModels.RemoteCommand;
import com.fongmi.android.tv.remote.RemoteModels.RemoteCommandResult;
import com.fongmi.android.tv.remote.RemoteModels.RemoteDevice;
import com.fongmi.android.tv.remote.RemoteModels.RemoteGroup;
import com.fongmi.android.tv.remote.RemoteModels.RemoteProfile;
import com.fongmi.android.tv.remote.RemoteModels.ServerCapabilities;
import com.fongmi.android.tv.remote.RemoteStore;
import com.fongmi.android.tv.remote.RemoteTokens;
import com.fongmi.android.tv.bean.SyncOptions;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.QRCode;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.lang.ref.WeakReference;

public final class RemoteTrustDialog {

    private static final int PAGE_DEVICES = 0;
    private static final int PAGE_DETAIL = 1;
    private static final int PAGE_SETTINGS = 2;
    private static final long DETECT_RETRY_MS = 5_000L;
    private static final long DEVICE_REFRESH_RETRY_MS = 3_000L;
    private static final int DEVICE_REFRESH_RETRY_MAX = 8;
    private static WeakReference<FragmentActivity> scanActivity;
    private static WeakReference<Binding> scanBinding;

    private RemoteTrustDialog() {
    }

    public static void show(Fragment fragment, Runnable callback) {
        show(fragment.requireActivity(), callback);
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        Binding binding = build(activity);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setView(binding.root)
                .create();
        binding.dialog = dialog;
        binding.callback = callback;
        binding.detectRetry = () -> {
            if (binding.dialog == null || !binding.dialog.isShowing()) return;
            RemoteProfile profile = currentProfile(binding);
            if (profile == null || !profile.enabled) return;
            if (binding.busy) {
                scheduleDetectRetry(binding);
                return;
            }
            binding.autoDetectStarted = false;
            detectService(activity, binding, true);
        };
        binding.deviceRefreshRetry = () -> retryRefreshDevices(activity, binding);
        render(activity, binding);
        dialog.setOnShowListener(d -> {
            configureWindow(activity, dialog);
            binding.close.setOnClickListener(v -> dialog.dismiss());
            binding.bindCodeButton.setOnClickListener(v -> copyCode(activity, binding));
            binding.enableToggle.setOnClickListener(v -> toggleEnabled(activity, binding));
            binding.statusButton.setOnClickListener(v -> {
                binding.statusExpanded = !binding.statusExpanded;
                render(activity, binding);
            });
            binding.addDeviceButton.setOnClickListener(v -> {
                if (currentProfile(binding) == null) {
                    binding.page = PAGE_SETTINGS;
                    render(activity, binding);
                    return;
                }
                showAddDeviceDialog(activity, binding);
            });
            binding.refreshButton.setOnClickListener(v -> refreshDevices(activity, binding));
            binding.serviceButton.setOnClickListener(v -> {
                binding.serverEditing = currentProfile(binding) == null;
                binding.page = binding.page == PAGE_SETTINGS ? PAGE_DEVICES : PAGE_SETTINGS;
                render(activity, binding);
            });
            binding.settingsBackButton.setOnClickListener(v -> {
                hideKeyboard(activity, binding.server);
                resetServerInput(binding);
                binding.serverEditing = false;
                binding.page = PAGE_DEVICES;
                render(activity, binding);
            });
            bindServerInput(activity, binding);
        });
        dialog.setOnDismissListener(d -> {
            App.removeCallbacks(binding.detectRetry, binding.deviceRefreshRetry);
            clearScanTarget(binding);
        });
        dialog.show();
    }

    public static void onScanResult(String address) {
        FragmentActivity activity = scanActivity == null ? null : scanActivity.get();
        Binding binding = scanBinding == null ? null : scanBinding.get();
        if (activity == null || binding == null || TextUtils.isEmpty(address)) return;
        binding.server.setText(address);
        binding.page = PAGE_SETTINGS;
        binding.serverEditing = true;
        saveServerSettings(activity, binding);
    }

    private static Binding build(Context context) {
        Binding binding = new Binding();
        binding.root = new LinearLayoutCompat(context);
        binding.root.setOrientation(LinearLayoutCompat.VERTICAL);
        binding.root.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        binding.root.setBackground(background(context, "#FFFFFF", Color.TRANSPARENT, 16));

        LinearLayoutCompat header = row(context);
        MaterialTextView title = text(context, context.getString(R.string.setting_remote_trust), 18, "#202124", true);
        header.addView(title);
        binding.bindCodeButton = pillButton(context, context.getString(R.string.remote_trust_bind_code_empty));
        LinearLayoutCompat.LayoutParams codeParams = new LinearLayoutCompat.LayoutParams(0, dp(context, 34), 1);
        codeParams.setMarginStart(dp(context, 8));
        header.addView(binding.bindCodeButton, codeParams);
        binding.enableToggle = compactButton(context, R.string.setting_enable);
        LinearLayoutCompat.LayoutParams enableParams = new LinearLayoutCompat.LayoutParams(dp(context, 56), dp(context, 34));
        enableParams.setMarginStart(dp(context, 8));
        header.addView(binding.enableToggle, enableParams);
        binding.close = closeButton(context);
        LinearLayoutCompat.LayoutParams closeParams = new LinearLayoutCompat.LayoutParams(dp(context, 34), dp(context, 34));
        closeParams.setMarginStart(dp(context, 6));
        header.addView(binding.close, closeParams);
        binding.root.addView(header, matchWrap());

        binding.summary = text(context, "", 13, "#5F6368", false);
        binding.summary.setPadding(0, dp(context, 2), 0, dp(context, 6));
        binding.root.addView(binding.summary, matchWrap());

        binding.toolbar = row(context);
        binding.statusButton = statusButton(context, context.getString(R.string.remote_trust_status_unbound));
        binding.toolbar.addView(binding.statusButton, weight());
        binding.addDeviceButton = outline(context, context.getString(R.string.remote_trust_add_short));
        binding.toolbar.addView(binding.addDeviceButton, fixed(context, 54, 34));
        binding.refreshButton = iconButton(context, R.drawable.ic_setting_refresh, context.getString(R.string.remote_trust_refresh_devices));
        binding.toolbar.addView(binding.refreshButton, fixed(context, 38, 34));
        binding.serviceButton = iconButton(context, R.drawable.ic_remote_settings, context.getString(R.string.remote_trust_service_entry));
        binding.toolbar.addView(binding.serviceButton, fixed(context, 38, 34));
        binding.settingsBackButton = outline(context, context.getString(R.string.remote_trust_back_devices));
        binding.settingsBackButton.setTextSize(12);
        binding.settingsBackButton.setVisibility(View.GONE);
        binding.toolbar.addView(binding.settingsBackButton, fixed(context, 58, 34));
        binding.root.addView(binding.toolbar, topMargin(matchWrap(), 6));

        binding.scroll = new NestedScrollView(context);
        binding.scroll.setFillViewport(false);

        binding.content = new LinearLayoutCompat(context);
        binding.content.setOrientation(LinearLayoutCompat.VERTICAL);
        binding.scroll.addView(binding.content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        binding.root.addView(binding.scroll, topMargin(matchWrap(), 10));

        binding.serverLayout = (TextInputLayout) LayoutInflater.from(context).inflate(R.layout.view_remote_trust_server_input, binding.content, false);
        binding.server = binding.serverLayout.findViewById(R.id.server);
        setupEditableText(binding.server, false);
        binding.enabled = check(context, R.string.remote_trust_enable);
        binding.enabled.setChecked(true);
        binding.keepOnline = check(context, R.string.remote_trust_keep_online);
        binding.keepOnline.setChecked(true);
        return binding;
    }

    private static void configureWindow(Context context, AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (ResUtil.getScreenWidth(context) * (ResUtil.isLand(context) ? 0.72f : 0.92f));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private static void render(Context context, Binding binding) {
        initFields(binding);
        binding.actions.clear();
        binding.content.removeAllViews();
        binding.summary.setText(RemoteStore.summary(context) + (Setting.hasFileAccess() ? "" : "\n" + context.getString(R.string.remote_trust_file_permission_hint)));
        updateHeader(context, binding);
        if (binding.page == PAGE_SETTINGS) renderSettings(context, binding);
        else if (binding.page == PAGE_DETAIL) renderDeviceDetail(context, binding);
        else renderDevices(context, binding);
        setBusy(binding, binding.busy);
        ensureAuto(activityOf(context), binding);
        if (binding.callback != null) binding.callback.run();
    }

    private static void initFields(Binding binding) {
        if (binding.initialized) return;
        binding.initialized = true;
        RemoteProfile profile = RemoteStore.firstProfile();
        if (profile == null) {
            binding.enabled.setChecked(true);
            return;
        }
        binding.server.setText(TextUtils.isEmpty(profile.serverUrl) ? profile.serverOrigin : profile.serverUrl);
        binding.enabled.setChecked(profile.enabled);
        binding.keepOnline.setChecked(true);
        if (!profile.keepOnline) {
            profile.keepOnline = true;
            RemoteStore.upsertProfile(profile);
            RemoteAgent.get().start();
        }
    }

    private static void updateHeader(Context context, Binding binding) {
        RemoteProfile profile = currentProfile(binding);
        boolean enabled = profile == null ? binding.enabled.isChecked() : profile.enabled;
        binding.enableToggle.setText(enabled ? R.string.setting_disable : R.string.setting_enable);
        binding.enabled.setChecked(enabled);
        binding.bindCodeButton.setText(bindCodeText(context, binding, profile));
        binding.toolbar.setVisibility(binding.page == PAGE_DETAIL ? View.GONE : View.VISIBLE);
        boolean settings = binding.page == PAGE_SETTINGS;
        binding.addDeviceButton.setVisibility(settings ? View.GONE : View.VISIBLE);
        binding.refreshButton.setVisibility(settings ? View.GONE : View.VISIBLE);
        binding.serviceButton.setVisibility(settings ? View.GONE : View.VISIBLE);
        binding.settingsBackButton.setVisibility(settings ? View.VISIBLE : View.GONE);
        binding.addDeviceButton.setEnabled(!binding.busy && profile != null);
        binding.refreshButton.setEnabled(!binding.busy && profile != null);
        binding.serviceButton.setEnabled(!binding.busy);
        binding.settingsBackButton.setEnabled(!binding.busy);
        String status = statusText(context, binding, profile);
        binding.statusButton.setText(status);
        applyStatusStyle(context, binding.statusButton, profile, binding.serviceStateText);
    }

    private static String bindCodeText(Context context, Binding binding, RemoteProfile profile) {
        if (profile == null) return context.getString(R.string.remote_trust_bind_code_unavailable);
        if (binding.creatingBindCode) return context.getString(R.string.remote_trust_bind_code_loading);
        if (TextUtils.isEmpty(binding.bindCode)) return context.getString(R.string.remote_trust_bind_code_empty);
        return context.getString(R.string.remote_trust_bind_code_inline, binding.bindCode);
    }

    private static String statusText(Context context, Binding binding, RemoteProfile profile) {
        if (profile == null) return context.getString(R.string.remote_trust_status_unbound);
        if (!profile.enabled) return context.getString(R.string.setting_disable);
        if (binding.busy && TextUtils.isEmpty(binding.serviceStateText)) return context.getString(R.string.remote_trust_detect_service);
        if (!TextUtils.isEmpty(binding.serviceStateText)) return binding.serviceStateText;
        return context.getString(R.string.remote_trust_service_unchecked);
    }

    private static void applyStatusStyle(Context context, MaterialButton button, RemoteProfile profile, String state) {
        int bg = Color.parseColor("#F1F3F4");
        int fg = Color.parseColor("#5F6368");
        int stroke = Color.parseColor("#DADCE0");
        if (profile != null && profile.enabled && TextUtils.equals(state, context.getString(R.string.remote_trust_service_ok))) {
            bg = Color.parseColor("#E6F4EA");
            fg = Color.parseColor("#137333");
            stroke = Color.parseColor("#CEEAD6");
        } else if (profile != null && profile.enabled && TextUtils.equals(state, context.getString(R.string.remote_trust_service_error))) {
            bg = Color.parseColor("#FCE8E6");
            fg = Color.parseColor("#B3261E");
            stroke = Color.parseColor("#F2B8B5");
        } else if (profile != null && profile.enabled) {
            bg = Color.parseColor("#E8F0FE");
            fg = Color.parseColor("#174EA6");
            stroke = Color.parseColor("#D2E3FC");
        }
        button.setBackgroundTintList(ColorStateList.valueOf(bg));
        button.setTextColor(fg);
        button.setStrokeColor(ColorStateList.valueOf(stroke));
    }

    private static void ensureAuto(FragmentActivity activity, Binding binding) {
        if (activity == null || binding.busy) return;
        RemoteProfile profile = currentProfile(binding);
        if (profile == null || !profile.enabled) return;
        if (TextUtils.isEmpty(binding.bindCode) && !binding.autoBindAttempted) {
            binding.creatingBindCode = true;
            createBindCode(activity, binding, false, true);
            return;
        }
        if (!binding.autoDetected && !binding.autoDetectStarted) detectService(activity, binding, true);
    }

    private static FragmentActivity activityOf(Context context) {
        return context instanceof FragmentActivity ? (FragmentActivity) context : null;
    }

    private static void toggleEnabled(FragmentActivity activity, Binding binding) {
        RemoteProfile profile = currentProfile(binding);
        boolean enabled = profile == null ? binding.enabled.isChecked() : profile.enabled;
        enabled = !enabled;
        binding.enabled.setChecked(enabled);
        if (profile != null) {
            profile.enabled = enabled;
            profile.keepOnline = true;
            RemoteStore.upsertProfile(profile);
            if (enabled) RemoteAgent.get().start();
            else RemoteAgent.get().start();
        }
        render(activity, binding);
    }

    private static void bindServerInput(FragmentActivity activity, Binding binding) {
        binding.server.setSingleLine(true);
        binding.server.setImeOptions(EditorInfo.IME_ACTION_DONE);
        binding.server.setOnEditorActionListener((view, actionId, event) -> {
            boolean done = actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (!done) return false;
            hideKeyboard(activity, binding.server);
            binding.server.clearFocus();
            saveServerSettings(activity, binding);
            return true;
        });
        binding.server.setOnClickListener(v -> showKeyboard(activity, binding.server));
    }

    private static void startScan(FragmentActivity activity, Binding binding) {
        try {
            Class<?> clazz = Class.forName("com.fongmi.android.tv.ui.activity.ScanActivity");
            scanActivity = new WeakReference<>(activity);
            scanBinding = new WeakReference<>(binding);
            activity.startActivity(new Intent(activity, clazz));
        } catch (Throwable e) {
            clearScanTarget(binding);
            Notify.show(R.string.remote_trust_scan_unavailable);
        }
    }

    private static void clearScanTarget(Binding binding) {
        Binding current = scanBinding == null ? null : scanBinding.get();
        if (current != null && current != binding) return;
        scanActivity = null;
        scanBinding = null;
    }

    private static void hideKeyboard(Context context, View view) {
        InputMethodManager manager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null && view != null) manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private static void showKeyboard(Context context, TextInputEditText input) {
        if (input == null) return;
        input.requestFocusFromTouch();
        input.postDelayed(() -> {
            InputMethodManager manager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 80);
    }

    private static void setupEditableText(TextInputEditText input, boolean multiline) {
        input.setSelectAllOnFocus(false);
        input.setHorizontallyScrolling(true);
        input.setHorizontalScrollBarEnabled(true);
        input.setVerticalScrollBarEnabled(multiline);
        input.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                view.post(() -> disallowParentIntercept(view, false));
                if (action == MotionEvent.ACTION_UP) showKeyboard(view.getContext(), input);
            } else {
                disallowParentIntercept(view, true);
            }
            return false;
        });
    }

    private static void disallowParentIntercept(View view, boolean disallow) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
            parent = parent.getParent();
        }
    }

    private static void renderDevices(Context context, Binding binding) {
        RemoteProfile profile = currentProfile(binding);
        if (binding.statusExpanded) binding.content.addView(statusDetailPanel(context, profile, binding), matchWrap());
        binding.content.addView(sectionTitle(context, R.string.remote_trust_device_list), topMargin(matchWrap(), binding.statusExpanded ? 12 : 0));

        List<DeviceRow> rows = deviceRows(profile);
        if (rows.isEmpty()) {
            binding.content.addView(emptyPanel(context, context.getString(hasGroups(profile) ? R.string.remote_trust_wait_devices : R.string.remote_trust_no_devices)), topMargin(matchWrap(), 8));
            return;
        }
        for (DeviceRow row : rows) {
            MaterialButton item = deviceButton(context, deviceText(context, profile, row.group, row.device), row.device.online);
            bindAction(binding, item);
            item.setOnClickListener(v -> {
                binding.selectedGroupId = row.group.groupId;
                binding.selectedDeviceId = row.device.deviceId;
                binding.lastResult = "";
                binding.page = PAGE_DETAIL;
                render(context, binding);
            });
            binding.content.addView(item, topMargin(matchWrap(), 8));
        }
    }

    private static void renderDeviceDetail(Context context, Binding binding) {
        RemoteProfile profile = currentProfile(binding);
        DeviceRow row = selectedRow(profile, binding);
        if (row == null) {
            binding.page = PAGE_DEVICES;
            renderDevices(context, binding);
            return;
        }
        LinearLayoutCompat top = row(context);
        MaterialButton deviceStatus = statusButton(context, deviceName(row.device) + " · " + deviceState(context, row.device) + " · " + deviceRole(context, profile, row.device));
        applyDeviceStyle(context, deviceStatus, row.device.online);
        deviceStatus.setOnClickListener(v -> {
            binding.deviceStatusExpanded = !binding.deviceStatusExpanded;
            render(context, binding);
        });
        bindAction(binding, deviceStatus);
        top.addView(deviceStatus, weight());
        MaterialButton back = smallOutlineAction(binding, context, R.string.remote_trust_back_devices);
        back.setOnClickListener(v -> {
            binding.deviceStatusExpanded = false;
            binding.page = PAGE_DEVICES;
            render(context, binding);
        });
        top.addView(back, fixed(context, 58, 34));
        binding.content.addView(top, matchWrap());

        if (binding.deviceStatusExpanded) {
            binding.content.addView(panel(context, deviceDetailText(context, profile, row.group, row.device)), topMargin(matchWrap(), 8));
        }

        LinearLayoutCompat row1 = row(context);
        MaterialButton search = primaryAction(binding, context, R.string.remote_trust_action_search);
        MaterialButton push = tonalAction(binding, context, R.string.remote_trust_action_push);
        search.setOnClickListener(v -> showTextCommandDialog((FragmentActivity) context, binding, R.string.remote_trust_action_search, R.string.remote_trust_search_keyword, "action.search", "word"));
        push.setOnClickListener(v -> showTextCommandDialog((FragmentActivity) context, binding, R.string.remote_trust_action_push, R.string.remote_trust_push_url, "action.push", "url"));
        row1.addView(search, weight());
        row1.addView(push, leftWeight(context));
        binding.content.addView(row1, topMargin(matchWrap(), 12));

        LinearLayoutCompat row2 = row(context);
        MaterialButton config = tonalAction(binding, context, R.string.remote_trust_action_config);
        MaterialButton sync = primaryAction(binding, context, R.string.remote_trust_action_sync);
        config.setOnClickListener(v -> showConfigDialog((FragmentActivity) context, binding));
        sync.setOnClickListener(v -> confirmRemoteSync((FragmentActivity) context, binding));
        row2.addView(config, weight());
        row2.addView(sync, leftWeight(context));
        binding.content.addView(row2, topMargin(matchWrap(), 8));

        if (!TextUtils.isEmpty(binding.lastResult)) {
            binding.content.addView(commandResultPanel(context, binding), topMargin(matchWrap(), 14));
        }
    }

    private static void renderSettings(Context context, Binding binding) {
        String state = TextUtils.isEmpty(binding.serviceStateText) ? context.getString(R.string.remote_trust_service_unchecked) : binding.serviceStateText;
        if (binding.statusExpanded) binding.content.addView(servicePanel(context, binding, state, binding.serviceDetailText), matchWrap());

        RemoteProfile profile = currentProfile(binding);
        if (profile == null) binding.serverEditing = true;
        if (binding.serverEditing) {
            binding.content.addView(serverTitleRow(context, binding), topMargin(matchWrap(), binding.statusExpanded ? 12 : 0));
            detach(binding.serverLayout);
            binding.content.addView(binding.serverLayout, topMargin(matchWrap(), 8));
            MaterialButton save = primaryAction(binding, context, R.string.remote_trust_done);
            save.setOnClickListener(v -> saveServerSettings((FragmentActivity) context, binding));
            binding.content.addView(save, topMargin(fixedHeight(context, 36), 8));
        } else {
            binding.content.addView(serverInfoPanel(context, binding, profile), topMargin(matchWrap(), binding.statusExpanded ? 12 : 0));
        }

        if (!Setting.hasFileAccess()) {
            MaterialButton permission = outlineAction(binding, context, R.string.remote_trust_file_permission);
            permission.setOnClickListener(v -> requestFileAccess((FragmentActivity) context, binding));
            binding.content.addView(permission, topMargin(fixedHeight(context, 34), 8));
        }

        MaterialButton advanced = outlineAction(binding, context, binding.advancedExpanded ? R.string.remote_trust_advanced_hide : R.string.remote_trust_advanced);
        advanced.setOnClickListener(v -> {
            binding.advancedExpanded = !binding.advancedExpanded;
            render(context, binding);
        });
        binding.content.addView(advanced, topMargin(fixedHeight(context, 34), 14));
        if (binding.advancedExpanded) {
            renderDeviceCleanup(context, binding, profile);
            MaterialButton clear = dangerAction(binding, context, R.string.remote_trust_reset_local);
            clear.setOnClickListener(v -> confirmClear((FragmentActivity) context, binding));
            binding.content.addView(clear, topMargin(fixedHeight(context, 36), 12));
        }
    }

    private static void renderDeviceCleanup(Context context, Binding binding, RemoteProfile profile) {
        List<DeviceRow> rows = deviceRows(profile);
        if (rows.isEmpty()) return;
        binding.content.addView(sectionTitle(context, R.string.remote_trust_added_devices), topMargin(matchWrap(), 12));
        for (DeviceRow row : rows) {
            LinearLayoutCompat item = card(context);
            LinearLayoutCompat line = row(context);
            MaterialTextView text = text(context, deviceText(context, profile, row.group, row.device), 12, "#3C4043", false);
            text.setMaxLines(2);
            text.setEllipsize(TextUtils.TruncateAt.END);
            line.addView(text, weight());
            MaterialButton delete = iconButton(context, R.drawable.ic_action_delete, context.getString(R.string.remote_trust_delete_device));
            delete.setOnClickListener(v -> confirmDeleteDevice((FragmentActivity) context, binding, row));
            bindAction(binding, delete);
            line.addView(delete, fixed(context, 36, 32));
            item.addView(line, matchWrap());
            binding.content.addView(item, topMargin(matchWrap(), 8));
        }
    }

    private static void saveServerSettings(FragmentActivity activity, Binding binding) {
        String serverUrl = textOf(binding.server);
        String origin = RemoteTokens.normalizeOrigin(serverUrl);
        if (TextUtils.isEmpty(origin)) {
            Notify.show(R.string.remote_trust_server_required);
            return;
        }
        RemoteProfile current = RemoteStore.getProfileByOrigin(origin);
        if (current != null && TextUtils.equals(serverUrl.trim(), current.serverUrl) && current.keepOnline && current.enabled == binding.enabled.isChecked()) {
            binding.serverEditing = false;
            if (TextUtils.isEmpty(binding.bindCode)) binding.autoBindAttempted = false;
            if (!binding.autoDetected) detectService(activity, binding, true);
            render(activity, binding);
            return;
        }
        applyServer(activity, binding);
    }

    private static void applyServerIfNeeded(FragmentActivity activity, Binding binding) {
        if (binding.busy) return;
        String serverUrl = textOf(binding.server);
        if (TextUtils.isEmpty(serverUrl)) return;
        String origin = RemoteTokens.normalizeOrigin(serverUrl);
        if (TextUtils.isEmpty(origin)) {
            Notify.show(R.string.remote_trust_server_required);
            return;
        }
        RemoteProfile current = TextUtils.isEmpty(origin) ? null : RemoteStore.getProfileByOrigin(origin);
        if (current != null && TextUtils.equals(serverUrl.trim(), current.serverUrl) && current.keepOnline && current.enabled == binding.enabled.isChecked()) {
            if (!binding.autoDetected) detectService(activity, binding, true);
            return;
        }
        applyServer(activity, binding);
    }

    private static void applyServer(FragmentActivity activity, Binding binding) {
        RemoteProfile profile;
        try {
            profile = prepare(binding, true);
        } catch (Throwable e) {
            Notify.show(e.getMessage());
            return;
        }
        setBusy(binding, true);
        Task.execute(() -> {
            try {
                new RemoteClient(profile).register();
                RemoteStore.upsertProfile(profile);
                RemoteAgent.get().start();
                App.post(() -> {
                    setBusy(binding, false);
                    resetDetect(binding);
                    binding.serverEditing = false;
                    binding.autoBindAttempted = false;
                    binding.bindCode = "";
                    Notify.show(R.string.remote_trust_register_done);
                    render(activity, binding);
                });
            } catch (Throwable e) {
                App.post(() -> {
                    setBusy(binding, false);
                    Notify.show(e.getMessage());
                });
            }
        });
    }

    private static void detectService(FragmentActivity activity, Binding binding, boolean quiet) {
        String serverUrl = textOf(binding.server);
        String origin = RemoteTokens.normalizeOrigin(serverUrl);
        if (TextUtils.isEmpty(origin)) {
            if (!quiet) Notify.show(R.string.remote_trust_server_required);
            return;
        }
        binding.autoDetectStarted = true;
        RemoteProfile probe = new RemoteProfile();
        probe.serverUrl = serverUrl.trim();
        probe.serverOrigin = origin;
        setBusy(binding, true);
        Task.execute(() -> {
            try {
                ServerCapabilities capabilities = new RemoteClient(probe).capabilities();
                String detail = formatCapabilities(activity, capabilities);
                String diagnostics = origin + "/api/server/capabilities\n" + App.gson().toJson(capabilities);
                App.post(() -> {
                    setBusy(binding, false);
                    binding.serviceStateText = activity.getString(R.string.remote_trust_service_ok);
                    binding.serviceDetailText = detail;
                    binding.diagnostics = diagnostics;
                    binding.autoDetected = true;
                    binding.autoDetectStarted = false;
                    if (TextUtils.isEmpty(binding.bindCode)) binding.autoBindAttempted = false;
                    App.removeCallbacks(binding.detectRetry);
                    render(activity, binding);
                });
            } catch (Throwable e) {
                App.post(() -> {
                    setBusy(binding, false);
                    binding.serviceStateText = activity.getString(R.string.remote_trust_service_error);
                    binding.serviceDetailText = activity.getString(R.string.remote_trust_service_failed_with_reason, conciseError(activity, e));
                    binding.diagnostics = origin + "/api/server/capabilities\n" + e.getMessage();
                    binding.autoDetected = false;
                    binding.autoDetectStarted = true;
                    scheduleDetectRetry(binding);
                    render(activity, binding);
                });
            }
        });
    }

    private static void showBindCodeDialog(FragmentActivity activity, Binding binding) {
        if (currentProfile(binding) == null) {
            binding.page = PAGE_SETTINGS;
            render(activity, binding);
            Notify.show(R.string.remote_trust_no_profile);
            return;
        }
        if (TextUtils.isEmpty(binding.bindCode)) {
            createBindCode(activity, binding, true);
            return;
        }
        LinearLayoutCompat root = dialogRoot(activity);
        MaterialTextView code = text(activity, binding.bindCode, 28, "#202124", true);
        code.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        code.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        root.addView(code, matchWrap());
        root.addView(caption(activity, R.string.remote_trust_bind_code_hint), topMargin(matchWrap(), 8));
        new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(R.string.remote_trust_bind_local_title)
                .setView(root)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setNeutralButton(R.string.remote_trust_refresh_bind_code, (dialog, which) -> createBindCode(activity, binding, true))
                .setPositiveButton(R.string.remote_trust_copy, (dialog, which) -> copyCode(activity, binding))
                .show();
    }

    private static void createBindCode(FragmentActivity activity, Binding binding, boolean reopen) {
        createBindCode(activity, binding, reopen, false);
    }

    private static void createBindCode(FragmentActivity activity, Binding binding, boolean reopen, boolean quiet) {
        RemoteProfile profile = currentProfile(binding);
        if (profile == null) {
            binding.page = PAGE_SETTINGS;
            render(activity, binding);
            if (!quiet) Notify.show(R.string.remote_trust_no_profile);
            return;
        }
        binding.autoBindAttempted = true;
        binding.creatingBindCode = true;
        RemoteBindGrant grant = new RemoteBindGrant();
        grant.bindGrantToken = RemoteTokens.randomCapability("bgt");
        grant.grantId = RemoteTokens.bindGrantId(profile.serverOrigin, grant.bindGrantToken);
        grant.createdAt = System.currentTimeMillis();
        RemoteStore.addBindGrant(profile.serverOrigin, grant);
        setBusy(binding, true);
        Task.execute(() -> {
            try {
                RemoteClient client = new RemoteClient(profile);
                client.register();
                BindCodeResponse response = client.createBindCode(grant);
                RemoteStore.upsertProfile(profile);
                RemoteAgent.get().start();
                App.post(() -> {
                    setBusy(binding, false);
                    binding.creatingBindCode = false;
                    binding.bindCode = response == null ? "" : response.code;
                    if (!quiet) Notify.show(R.string.remote_trust_bind_code_done);
                    render(activity, binding);
                    if (reopen) showBindCodeDialog(activity, binding);
                });
            } catch (Throwable e) {
                App.post(() -> {
                    setBusy(binding, false);
                    binding.creatingBindCode = false;
                    if (!quiet) Notify.show(e.getMessage());
                    else render(activity, binding);
                });
            }
        });
    }

    private static void showAddDeviceDialog(FragmentActivity activity, Binding binding) {
        RemoteProfile profile = currentProfile(binding);
        if (profile == null) {
            binding.page = PAGE_SETTINGS;
            render(activity, binding);
            Notify.show(R.string.remote_trust_no_profile);
            return;
        }
        LinearLayoutCompat root = dialogRoot(activity);
        TextInputEditText code = input(activity, InputType.TYPE_CLASS_NUMBER, true);
        TextInputEditText alias = input(activity, InputType.TYPE_CLASS_TEXT, true);
        root.addView(inputLayout(activity, R.string.remote_trust_bind_code, code), matchWrap());
        root.addView(inputLayout(activity, R.string.remote_trust_device_alias, alias), topMargin(matchWrap(), 8));
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(R.string.remote_trust_add_device_title)
                .setView(root)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.remote_trust_add_device, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = textOf(code);
            if (TextUtils.isEmpty(value)) {
                Notify.show(R.string.remote_trust_code_required);
                return;
            }
            dialog.dismiss();
            addDevice(activity, binding, value, textOf(alias));
        }));
        dialog.show();
    }

    private static void addDevice(FragmentActivity activity, Binding binding, String code, String alias) {
        RemoteProfile profile = currentProfile(binding);
        if (profile == null) {
            Notify.show(R.string.remote_trust_no_profile);
            return;
        }
        String groupToken = firstGroupToken(profile);
        setBusy(binding, true);
        Task.execute(() -> {
            try {
                RemoteClient client = new RemoteClient(profile);
                client.register();
                ClaimResponse response = client.claim(code, groupToken, alias);
                RemoteGroup group = RemoteStore.upsertClaimGroup(profile.serverOrigin, response, alias);
                RemoteProfile updated = RemoteStore.getProfileByOrigin(profile.serverOrigin);
                if (updated != null) {
                    RemoteClient updatedClient = new RemoteClient(updated);
                    updatedClient.register();
                    if (group != null) refreshGroup(updatedClient, updated.serverOrigin, group);
                }
                RemoteAgent.get().start();
                App.post(() -> {
                    setBusy(binding, false);
                    binding.selectedGroupId = "";
                    binding.selectedDeviceId = "";
                    binding.page = PAGE_DEVICES;
                    binding.pendingDeviceRefreshes = DEVICE_REFRESH_RETRY_MAX;
                    Notify.show(R.string.remote_trust_add_done);
                    render(activity, binding);
                    scheduleDeviceRefresh(binding);
                });
            } catch (Throwable e) {
                App.post(() -> {
                    setBusy(binding, false);
                    Notify.show(e.getMessage());
                });
            }
        });
    }

    private static void refreshDevices(FragmentActivity activity, Binding binding) {
        RemoteProfile profile = currentProfile(binding);
        if (profile == null) {
            binding.page = PAGE_SETTINGS;
            render(activity, binding);
            Notify.show(R.string.remote_trust_no_profile);
            return;
        }
        if (profile.groups == null || profile.groups.isEmpty()) {
            Notify.show(R.string.remote_trust_no_group);
            return;
        }
        setBusy(binding, true);
        Task.execute(() -> {
            try {
                RemoteClient client = new RemoteClient(profile);
                client.register();
                int count = 0;
                for (RemoteGroup group : new ArrayList<>(profile.groups)) count += refreshGroup(client, profile.serverOrigin, group);
                RemoteAgent.get().start();
                int refreshed = count;
                App.post(() -> {
                    setBusy(binding, false);
                    Notify.show(activity.getString(R.string.remote_trust_devices_refreshed, refreshed));
                    render(activity, binding);
                });
            } catch (Throwable e) {
                App.post(() -> {
                    setBusy(binding, false);
                    Notify.show(e.getMessage());
                });
            }
        });
    }

    private static int refreshGroup(RemoteClient client, String serverOrigin, RemoteGroup group) throws Exception {
        DevicesResponse response = client.listDevices(group);
        List<RemoteDevice> devices = response == null ? new ArrayList<>() : response.devices;
        RemoteStore.upsertDevices(serverOrigin, group.groupId, devices);
        return devices == null ? 0 : devices.size();
    }

    private static void scheduleDeviceRefresh(Binding binding) {
        if (binding.deviceRefreshRetry == null || binding.dialog == null || !binding.dialog.isShowing()) return;
        App.post(binding.deviceRefreshRetry, DEVICE_REFRESH_RETRY_MS);
    }

    private static void retryRefreshDevices(FragmentActivity activity, Binding binding) {
        if (activity == null || binding.dialog == null || !binding.dialog.isShowing()) return;
        RemoteProfile profile = currentProfile(binding);
        if (profile == null || profile.groups == null || profile.groups.isEmpty()) return;
        if (!deviceRows(profile).isEmpty()) return;
        if (binding.pendingDeviceRefreshes-- <= 0) return;
        Task.execute(() -> {
            try {
                RemoteProfile latest = currentProfile(binding);
                if (latest == null || latest.groups == null || latest.groups.isEmpty()) return;
                RemoteClient client = new RemoteClient(latest);
                client.register();
                for (RemoteGroup group : new ArrayList<>(latest.groups)) refreshGroup(client, latest.serverOrigin, group);
                App.post(() -> {
                    render(activity, binding);
                    if (deviceRows(currentProfile(binding)).isEmpty()) scheduleDeviceRefresh(binding);
                });
            } catch (Throwable ignored) {
                App.post(() -> scheduleDeviceRefresh(binding));
            }
        });
    }

    private static void showTextCommandDialog(FragmentActivity activity, Binding binding, int title, int hint, String type, String payloadKey) {
        TextInputEditText input = input(activity, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, true);
        LinearLayoutCompat root = dialogRoot(activity);
        root.addView(inputLayout(activity, hint, input), matchWrap());
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(title)
                .setView(root)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton("action.push".equals(type) ? R.string.remote_trust_send_push : R.string.remote_trust_send_search, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = textOf(input);
            if (TextUtils.isEmpty(value)) {
                Notify.show(hint);
                return;
            }
            JsonObject payload = new JsonObject();
            payload.addProperty(payloadKey, value);
            dialog.dismiss();
            sendCommand(activity, binding, type, payload);
        }));
        dialog.show();
    }

    private static void showConfigDialog(FragmentActivity activity, Binding binding) {
        LinearLayoutCompat root = dialogRoot(activity);
        int[] selectedType = {0};
        boolean[] localMode = {true};
        LinearLayoutCompat modeRow = row(activity);
        MaterialButton local = tab(activity, R.string.remote_trust_config_mode_local);
        MaterialButton manual = tab(activity, R.string.remote_trust_config_mode_manual);
        local.setChecked(true);
        modeRow.addView(local, weight());
        modeRow.addView(manual, leftWeight(activity));
        root.addView(modeRow, matchWrap());

        LinearLayoutCompat typeRow = row(activity);
        MaterialButton vod = tab(activity, R.string.remote_trust_config_type_vod);
        MaterialButton live = tab(activity, R.string.remote_trust_config_type_live);
        MaterialButton wall = tab(activity, R.string.remote_trust_config_type_wall);
        vod.setChecked(true);
        typeRow.addView(vod, weight());
        typeRow.addView(live, leftWeight(activity, 6));
        typeRow.addView(wall, leftWeight(activity, 6));
        root.addView(typeRow, topMargin(matchWrap(), 8));

        LinearLayoutCompat content = new LinearLayoutCompat(activity);
        content.setOrientation(LinearLayoutCompat.VERTICAL);
        root.addView(content, topMargin(matchWrap(), 10));

        AlertDialog[] dialogRef = new AlertDialog[1];
        Runnable[] render = new Runnable[1];
        render[0] = () -> renderConfigContent(activity, binding, dialogRef, content, selectedType[0], localMode[0]);
        local.setOnClickListener(v -> {
            localMode[0] = true;
            local.setChecked(true);
            manual.setChecked(false);
            render[0].run();
        });
        manual.setOnClickListener(v -> {
            localMode[0] = false;
            local.setChecked(false);
            manual.setChecked(true);
            render[0].run();
        });
        vod.setOnClickListener(v -> selectConfigType(selectedType, 0, vod, live, wall, render[0]));
        live.setOnClickListener(v -> selectConfigType(selectedType, 1, vod, live, wall, render[0]));
        wall.setOnClickListener(v -> selectConfigType(selectedType, 2, vod, live, wall, render[0]));
        render[0].run();
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(R.string.remote_trust_action_config)
                .setView(root)
                .setNegativeButton(R.string.dialog_cancel, null)
                .create();
        dialogRef[0] = dialog;
        dialog.show();
    }

    private static void selectConfigType(int[] selectedType, int type, MaterialButton vod, MaterialButton live, MaterialButton wall, Runnable render) {
        selectedType[0] = type;
        vod.setChecked(type == 0);
        live.setChecked(type == 1);
        wall.setChecked(type == 2);
        render.run();
    }

    private static void renderConfigContent(FragmentActivity activity, Binding binding, AlertDialog[] dialogRef, LinearLayoutCompat content, int type, boolean localMode) {
        content.removeAllViews();
        if (localMode) {
            List<Config> configs = Config.getAll(type);
            if (configs.isEmpty()) {
                content.addView(emptyPanel(activity, activity.getString(R.string.remote_trust_config_local_empty)), matchWrap());
                return;
            }
            for (Config config : configs) {
                LinearLayoutCompat item = configItem(activity, config);
                item.setOnClickListener(v -> {
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                    showConfigActionDialog(activity, binding, configPayload(type, config.getUrl(), config.getName()));
                });
                content.addView(item, topMargin(matchWrap(), 6));
            }
            return;
        }
        TextInputEditText url = input(activity, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, true);
        TextInputEditText name = input(activity, InputType.TYPE_CLASS_TEXT, true);
        content.addView(inputLayout(activity, R.string.remote_trust_config_url, url), matchWrap());
        content.addView(inputLayout(activity, R.string.remote_trust_config_name, name), topMargin(matchWrap(), 8));
        MaterialButton next = primaryAction(binding, activity, R.string.remote_trust_config_save);
        next.setOnClickListener(v -> {
            JsonObject payload = configPayload(activity, type, url, name);
            if (payload != null) {
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                showConfigActionDialog(activity, binding, payload);
            }
        });
        content.addView(next, topMargin(fixedHeight(activity, 36), 8));
    }

    private static LinearLayoutCompat configItem(Context context, Config config) {
        LinearLayoutCompat item = card(context);
        item.setClickable(true);
        item.setFocusable(true);
        LinearLayoutCompat top = row(context);
        MaterialTextView title = text(context, configTitle(config), 14, "#202124", true);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(title, weight());
        MaterialTextView action = text(context, context.getString(R.string.remote_trust_config_select), 12, "#0B57D0", true);
        action.setPadding(dp(context, 8), 0, 0, 0);
        top.addView(action, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        item.addView(top, matchWrap());
        String subtitle = configSubtitle(config);
        if (!TextUtils.isEmpty(subtitle)) {
            MaterialTextView url = text(context, subtitle, 12, "#5F6368", false);
            url.setMaxLines(2);
            url.setEllipsize(TextUtils.TruncateAt.END);
            url.setPadding(0, dp(context, 4), 0, 0);
            item.addView(url, matchWrap());
        }
        return item;
    }

    private static String configTitle(Config config) {
        if (config == null) return "";
        if (!TextUtils.isEmpty(config.getName())) return config.getName();
        return shortenMiddle(config.getUrl(), 48);
    }

    private static String configSubtitle(Config config) {
        if (config == null || TextUtils.isEmpty(config.getUrl())) return "";
        return TextUtils.isEmpty(config.getName()) ? "" : config.getUrl();
    }

    private static void showConfigActionDialog(FragmentActivity activity, Binding binding, JsonObject payload) {
        String[] actions = new String[]{
                activity.getString(R.string.remote_trust_config_upsert),
                activity.getString(R.string.remote_trust_config_use),
                activity.getString(R.string.remote_trust_config_delete)
        };
        new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(R.string.remote_trust_action_config)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) sendCommand(activity, binding, "config.upsert", payload);
                    else if (which == 1) sendCommand(activity, binding, "config.use", payload);
                    else confirmConfigDelete(activity, binding, payload);
                })
                .show();
    }

    private static JsonObject configPayload(FragmentActivity activity, int type, TextInputEditText url, TextInputEditText name) {
        String value = textOf(url);
        if (TextUtils.isEmpty(value)) {
            Notify.show(R.string.remote_trust_config_url_required);
            return null;
        }
        return configPayload(type, value, textOf(name));
    }

    private static JsonObject configPayload(int type, String url, String name) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", Math.max(0, Math.min(2, type)));
        payload.addProperty("url", url);
        payload.addProperty("name", name);
        return payload;
    }

    private static void confirmConfigDelete(FragmentActivity activity, Binding binding, JsonObject payload) {
        new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(R.string.remote_trust_config_delete)
                .setMessage(activity.getString(R.string.remote_trust_config_delete_message, payload.has("url") ? payload.get("url").getAsString() : ""))
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_confirm, (dialog, which) -> sendCommand(activity, binding, "config.delete", payload))
                .show();
    }

    private static void confirmRemoteSync(FragmentActivity activity, Binding binding) {
        RemoteProfile profile = currentProfile(binding);
        DeviceRow selected = selectedRow(profile, binding);
        if (profile == null || selected == null) {
            Notify.show(R.string.remote_trust_no_device_selected);
            return;
        }
        if (TextUtils.equals(profile.deviceId, selected.device.deviceId)) {
            Notify.show(R.string.remote_trust_sync_self_forbidden);
            return;
        }
        new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(R.string.remote_trust_action_sync)
                .setMessage(activity.getString(R.string.remote_trust_sync_confirm, deviceName(selected.device)))
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_confirm, (dialog, which) -> startRemoteSync(activity, binding, selected))
                .show();
    }

    private static void startRemoteSync(FragmentActivity activity, Binding binding, DeviceRow selected) {
        RemoteProfile profile = currentProfile(binding);
        if (profile == null || selected == null) {
            Notify.show(R.string.remote_trust_no_device_selected);
            return;
        }
        setBusy(binding, true);
        Task.execute(() -> {
            try {
                RemoteClient client = new RemoteClient(profile);
                client.register();
                JsonObject response = client.createSync(selected.group, profile.deviceId, selected.device.deviceId, App.gson().toJsonTree(SyncOptions.defaults()).getAsJsonObject());
                App.post(() -> {
                    setBusy(binding, false);
                    binding.lastResult = activity.getString(R.string.remote_trust_sync_started) + "\n\n" + formatData(response);
                    render(activity, binding);
                });
            } catch (Throwable e) {
                App.post(() -> {
                    setBusy(binding, false);
                    binding.lastResult = e.getMessage();
                    Notify.show(e.getMessage());
                    render(activity, binding);
                });
            }
        });
    }

    private static void sendCommand(FragmentActivity activity, Binding binding, String type, JsonObject payload) {
        RemoteProfile profile = currentProfile(binding);
        DeviceRow selected = selectedRow(profile, binding);
        if (profile == null || selected == null) {
            Notify.show(R.string.remote_trust_no_device_selected);
            return;
        }
        setBusy(binding, true);
        binding.lastResult = "";
        Task.execute(() -> {
            try {
                RemoteClient client = new RemoteClient(profile);
                CommandResponse response = client.createCommand(selected.group, selected.device.deviceId, type, payload);
                String commandId = response == null ? "" : response.commandId;
                if (TextUtils.isEmpty(commandId) && response != null && response.command != null) commandId = response.command.id;
                RemoteCommand command = waitCommand(client, selected.group, commandId, response == null ? null : response.command);
                String result = formatCommand(activity, type, command);
                App.post(() -> {
                    setBusy(binding, false);
                    binding.lastResult = result;
                    binding.page = PAGE_DETAIL;
                    render(activity, binding);
                });
            } catch (Throwable e) {
                App.post(() -> {
                    setBusy(binding, false);
                    binding.lastResult = e.getMessage();
                    Notify.show(e.getMessage());
                    render(activity, binding);
                });
            }
        });
    }

    private static RemoteCommand waitCommand(RemoteClient client, RemoteGroup group, String commandId, RemoteCommand fallback) throws Exception {
        if (TextUtils.isEmpty(commandId)) return fallback;
        RemoteCommand command = fallback;
        for (int i = 0; i < 8; i++) {
            Thread.sleep(i == 0 ? 700 : 1000);
            CommandDetailResponse detail = client.getCommand(group, commandId);
            if (detail != null && detail.command != null) command = detail.command;
            if (command != null && ("done".equals(command.status) || "failed".equals(command.status))) break;
        }
        return command;
    }

    private static String formatCommand(Context context, String type, RemoteCommand command) {
        if (command == null) return context.getString(R.string.remote_trust_empty_result);
        StringBuilder builder = new StringBuilder();
        builder.append(context.getString(R.string.remote_trust_result_action)).append(": ").append(commandName(context, type));
        builder.append('\n').append(context.getString(R.string.remote_trust_result_status)).append(": ").append(commandStatus(command.status));
        RemoteCommandResult result = command.result;
        if (result == null) {
            builder.append('\n').append(context.getString(R.string.remote_trust_command_waiting));
            return builder.toString();
        }
        builder.append('\n').append(context.getString(R.string.remote_trust_result_state)).append(": ").append(result.ok ? context.getString(R.string.remote_trust_command_success) : context.getString(R.string.remote_trust_command_failed));
        if (!TextUtils.isEmpty(result.message)) builder.append(": ").append(result.message);
        String data = formatData(result.data);
        if (!TextUtils.isEmpty(data)) builder.append("\n\n").append(data);
        return builder.toString();
    }

    private static String commandName(Context context, String type) {
        if ("device.status".equals(type)) return context.getString(R.string.remote_trust_action_status);
        if ("config.list".equals(type)) return context.getString(R.string.remote_trust_config_list);
        if ("config.upsert".equals(type)) return context.getString(R.string.remote_trust_config_upsert);
        if ("config.use".equals(type)) return context.getString(R.string.remote_trust_config_use);
        if ("config.delete".equals(type)) return context.getString(R.string.remote_trust_config_delete);
        if ("remoteSync.export".equals(type) || "remoteSync.restore".equals(type)) return context.getString(R.string.remote_trust_action_sync);
        if ("action.search".equals(type)) return context.getString(R.string.remote_trust_action_search);
        if ("action.push".equals(type)) return context.getString(R.string.remote_trust_action_push);
        if ("log.recent".equals(type) || "device.log.recent".equals(type)) return context.getString(R.string.remote_trust_action_log);
        return type;
    }

    private static String commandStatus(String status) {
        return TextUtils.isEmpty(status) ? "queued" : status;
    }

    private static String formatData(JsonElement data) {
        if (data == null || data.isJsonNull()) return "";
        if (data.isJsonObject()) {
            JsonObject object = data.getAsJsonObject();
            if (object.has("lines") && object.get("lines").isJsonArray()) return lines(object.getAsJsonArray("lines"));
            if (object.has("items") && object.get("items").isJsonArray()) return configItems(object.getAsJsonArray("items"));
            if (object.has("syncFiles") || object.has("loginStateFiles")) return syncSummary(object);
            return keyValues(object);
        }
        if (data.isJsonArray()) return arrayValues(data.getAsJsonArray());
        return data.getAsString();
    }

    private static String configItems(JsonArray array) {
        StringBuilder builder = new StringBuilder();
        builder.append(App.get().getString(R.string.remote_trust_result_config_count, array.size()));
        for (int i = 0; i < array.size(); i++) {
            if (!array.get(i).isJsonObject()) continue;
            JsonObject item = array.get(i).getAsJsonObject();
            builder.append('\n').append(bool(item, "active") ? "* " : "- ");
            builder.append(safe(item, "typeName")).append(" · ").append(safe(item, "desc"));
            String url = safe(item, "url");
            if (!TextUtils.isEmpty(url)) builder.append('\n').append("  ").append(url);
        }
        return builder.toString();
    }

    private static String syncSummary(JsonObject object) {
        StringBuilder builder = new StringBuilder();
        if (object.has("syncId")) builder.append("Sync ID: ").append(safe(object, "syncId")).append('\n');
        builder.append("Files: ").append(safe(object, "syncFiles"));
        builder.append('\n').append("Login files: ").append(safe(object, "loginStateFiles"));
        return builder.toString();
    }

    private static String keyValues(JsonObject object) {
        StringBuilder builder = new StringBuilder();
        for (String key : object.keySet()) {
            JsonElement value = object.get(key);
            if (value == null || value.isJsonNull()) continue;
            if (builder.length() > 0) builder.append('\n');
            builder.append(label(key)).append(": ");
            if (value.isJsonObject()) builder.append(keyValues(value.getAsJsonObject()).replace("\n", "\n  "));
            else if (value.isJsonArray()) builder.append(arrayValues(value.getAsJsonArray()));
            else builder.append(value.getAsString());
        }
        return builder.toString();
    }

    private static String arrayValues(JsonArray array) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < array.size(); i++) {
            if (i > 0) builder.append('\n');
            JsonElement value = array.get(i);
            builder.append("- ");
            if (value.isJsonObject()) builder.append(keyValues(value.getAsJsonObject()).replace("\n", "\n  "));
            else builder.append(value.isJsonNull() ? "" : value.getAsString());
        }
        return builder.toString();
    }

    private static String label(String key) {
        if (TextUtils.isEmpty(key)) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) builder.append(' ');
            builder.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return builder.toString();
    }

    private static String safe(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        try {
            return object.get(key).getAsString();
        } catch (Throwable e) {
            return "";
        }
    }

    private static boolean bool(JsonObject object, String key) {
        try {
            return object != null && object.has(key) && object.get(key).getAsBoolean();
        } catch (Throwable e) {
            return false;
        }
    }

    private static String lines(JsonArray array) {
        StringBuilder builder = new StringBuilder();
        builder.append(App.get().getString(R.string.remote_trust_result_log_lines, array.size())).append('\n');
        int start = Math.max(0, array.size() - 120);
        for (int i = start; i < array.size(); i++) {
            if (i > start) builder.append('\n');
            builder.append(array.get(i).getAsString());
        }
        return builder.toString();
    }

    private static RemoteProfile prepare(Binding binding, boolean keepOnline) {
        String serverUrl = textOf(binding.server);
        return RemoteStore.prepareProfile(serverUrl, binding.enabled.isChecked(), keepOnline);
    }

    private static RemoteProfile currentProfile(Binding binding) {
        String serverUrl = textOf(binding.server);
        if (TextUtils.isEmpty(serverUrl)) return RemoteStore.firstProfile();
        String origin = RemoteTokens.normalizeOrigin(serverUrl);
        return TextUtils.isEmpty(origin) ? null : RemoteStore.getProfileByOrigin(origin);
    }

    private static void requestFileAccess(FragmentActivity activity, Binding binding) {
        PermissionUtil.requestFile(activity, granted -> {
            if (granted) RemoteStore.save(RemoteStore.get());
            render(activity, binding);
        });
    }

    private static void resetServerInput(Binding binding) {
        RemoteProfile profile = currentProfile(binding);
        if (profile == null) profile = RemoteStore.firstProfile();
        if (profile == null) return;
        binding.server.setText(TextUtils.isEmpty(profile.serverUrl) ? profile.serverOrigin : profile.serverUrl);
        binding.enabled.setChecked(profile.enabled);
    }

    private static void confirmClear(FragmentActivity activity, Binding binding) {
        new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(R.string.remote_trust_reset_local)
                .setMessage(R.string.remote_trust_clear_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_confirm, (dialog, which) -> {
                    RemoteStore.clear();
                    RemoteAgent.get().stop();
                    RemoteAgentService.stop(activity);
                    binding.initialized = false;
                    binding.bindCode = "";
                    binding.selectedGroupId = "";
                    binding.selectedDeviceId = "";
                    binding.serviceStateText = "";
                    binding.serviceDetailText = "";
                    binding.diagnostics = "";
                    binding.statusExpanded = false;
                    binding.advancedExpanded = false;
                    binding.autoBindAttempted = false;
                    resetDetect(binding);
                    binding.creatingBindCode = false;
                    binding.page = PAGE_DEVICES;
                    render(activity, binding);
                })
                .show();
    }

    private static void confirmDeleteDevice(FragmentActivity activity, Binding binding, DeviceRow row) {
        if (row == null || row.group == null || row.device == null) return;
        new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(R.string.remote_trust_delete_device)
                .setMessage(activity.getString(R.string.remote_trust_delete_device_message, deviceName(row.device)))
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_confirm, (dialog, which) -> {
                    RemoteProfile profile = currentProfile(binding);
                    if (profile == null) return;
                    if (RemoteStore.removeDevice(profile.serverOrigin, row.group.groupId, row.device.deviceId)) {
                        binding.selectedGroupId = "";
                        binding.selectedDeviceId = "";
                        Notify.show(R.string.remote_trust_delete_device_done);
                        render(activity, binding);
                    }
                })
                .show();
    }

    private static void copyCode(Context context, Binding binding) {
        if (TextUtils.isEmpty(binding.bindCode)) return;
        copyText(context, context.getString(R.string.setting_remote_trust), binding.bindCode, R.string.remote_trust_bind_code_copied);
    }

    private static void copyText(Context context, String label, String text, int message) {
        ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) return;
        manager.setPrimaryClip(ClipData.newPlainText(label, text));
        Notify.show(message);
    }

    private static String localStatus(Context context, RemoteProfile profile) {
        if (profile == null) return context.getString(R.string.remote_trust_no_profile);
        StringBuilder builder = new StringBuilder();
        builder.append(context.getString(R.string.remote_trust_server_url)).append(": ").append(displayOrigin(profile.serverOrigin));
        builder.append('\n').append(context.getString(R.string.remote_trust_device_id)).append(": ").append(shortId(profile.deviceId));
        builder.append('\n').append(context.getString(R.string.remote_trust_status_summary, profile.keepOnline ? context.getString(R.string.remote_trust_status_online) : context.getString(R.string.remote_trust_status_enabled), 1, profile.groups == null ? 0 : profile.groups.size()));
        return builder.toString();
    }

    private static List<DeviceRow> deviceRows(RemoteProfile profile) {
        List<DeviceRow> rows = new ArrayList<>();
        if (profile == null || profile.groups == null) return rows;
        for (RemoteGroup group : profile.groups) {
            if (group == null || group.devices == null) continue;
            for (RemoteDevice device : group.devices) {
                if (device != null && !TextUtils.isEmpty(device.deviceId)) rows.add(new DeviceRow(group, device));
            }
        }
        return rows;
    }

    private static boolean hasGroups(RemoteProfile profile) {
        return profile != null && profile.groups != null && !profile.groups.isEmpty();
    }

    private static DeviceRow selectedRow(RemoteProfile profile, Binding binding) {
        if (profile == null) return null;
        for (DeviceRow row : deviceRows(profile)) {
            if (TextUtils.equals(row.group.groupId, binding.selectedGroupId) && TextUtils.equals(row.device.deviceId, binding.selectedDeviceId)) return row;
        }
        return null;
    }

    private static String firstGroupToken(RemoteProfile profile) {
        if (profile == null || profile.groups == null) return "";
        for (RemoteGroup group : profile.groups) if (group != null && !TextUtils.isEmpty(group.groupToken)) return group.groupToken;
        return "";
    }

    private static String deviceText(Context context, RemoteProfile profile, RemoteGroup group, RemoteDevice device) {
        return deviceName(device) + " · " + deviceRole(context, profile, device) + " · " + deviceState(context, device) + deviceTime(device) + "\n" + groupName(context, group) + " · " + shortId(device.deviceId);
    }

    private static String deviceDetailText(Context context, RemoteProfile profile, RemoteGroup group, RemoteDevice device) {
        StringBuilder builder = new StringBuilder();
        builder.append(deviceRole(context, profile, device)).append(" · ").append(deviceState(context, device)).append(deviceTime(device));
        builder.append('\n').append(groupName(context, group));
        builder.append('\n').append(context.getString(R.string.remote_trust_device_id)).append(": ").append(shortId(device.deviceId));
        if (!TextUtils.isEmpty(device.appVersion)) builder.append('\n').append(context.getString(R.string.remote_trust_app_version)).append(": ").append(device.appVersion);
        return builder.toString();
    }

    private static String deviceName(RemoteDevice device) {
        return TextUtils.isEmpty(device.name) ? shortId(device.deviceId) : device.name;
    }

    private static String deviceState(Context context, RemoteDevice device) {
        return device.online ? context.getString(R.string.remote_trust_device_online) : context.getString(R.string.remote_trust_device_offline);
    }

    private static String deviceTime(RemoteDevice device) {
        return device.lastSeen <= 0 ? "" : " · " + new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(new Date(device.lastSeen));
    }

    private static String selfSuffix(Context context, RemoteProfile profile, RemoteDevice device) {
        return profile != null && TextUtils.equals(profile.deviceId, device.deviceId) ? " · " + context.getString(R.string.remote_trust_self_device) : "";
    }

    private static String deviceRole(Context context, RemoteProfile profile, RemoteDevice device) {
        return profile != null && TextUtils.equals(profile.deviceId, device.deviceId) ? context.getString(R.string.remote_trust_self_device) : context.getString(R.string.remote_trust_controlled_device);
    }

    private static String groupName(Context context, RemoteGroup group) {
        return TextUtils.isEmpty(group.name) ? context.getString(R.string.remote_trust_group_title, shortId(group.groupId)) : group.name;
    }

    private static String formatCapabilities(Context context, ServerCapabilities server) {
        if (server == null) return "";
        RemoteCapabilities capabilities = server.capabilities == null ? new RemoteCapabilities() : server.capabilities;
        List<String> support = new ArrayList<>();
        support.add(context.getString(R.string.remote_trust_cap_device));
        if (capabilities.configManage) support.add(context.getString(R.string.remote_trust_cap_config));
        if (capabilities.remoteSync) support.add(context.getString(R.string.remote_trust_cap_sync));
        if (capabilities.pushAction) support.add(context.getString(R.string.remote_trust_cap_push));
        if (capabilities.recentLog) support.add(context.getString(R.string.remote_trust_cap_log));
        String supportText = support.isEmpty() ? context.getString(R.string.remote_trust_support_none) : TextUtils.join(", ", support);
        return context.getString(R.string.remote_trust_service_info,
                empty(server.serverMode),
                empty(server.relayMode),
                supportText,
                formatBytes(server.maxSyncPartBytes));
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "-";
        long mb = bytes / 1024 / 1024;
        return mb > 0 ? mb + " MB" : bytes + " B";
    }

    private static String empty(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }

    private static String conciseError(Context context, Throwable e) {
        String message = e == null ? "" : e.getMessage();
        if (TextUtils.isEmpty(message)) return context.getString(R.string.remote_trust_service_request_failed);
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("failed to connect") || lower.contains("timeout") || lower.contains("timed out") || lower.contains("unable to resolve") || lower.contains("connection refused")) {
            return context.getString(R.string.remote_trust_service_connect_failed);
        }
        return shorten(message.replace('\n', ' '), 72);
    }

    private static String displayOrigin(String value) {
        if (TextUtils.isEmpty(value)) return "-";
        String text = value.replace("https://", "").replace("http://", "");
        if (text.endsWith("/")) text = text.substring(0, text.length() - 1);
        return shortenMiddle(text, 34);
    }

    private static String shorten(String value, int max) {
        if (TextUtils.isEmpty(value) || value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String shortenMiddle(String value, int max) {
        if (TextUtils.isEmpty(value) || value.length() <= max) return value;
        int keep = Math.max(8, (max - 1) / 2);
        return value.substring(0, keep) + "…" + value.substring(value.length() - keep);
    }

    private static String shortId(String value) {
        if (TextUtils.isEmpty(value)) return "";
        return value.length() <= 8 ? value : value.substring(value.length() - 8);
    }

    private static String textOf(TextInputEditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    private static void setBusy(Binding binding, boolean busy) {
        binding.busy = busy;
        binding.bindCodeButton.setEnabled(!busy && !TextUtils.isEmpty(binding.bindCode));
        binding.enableToggle.setEnabled(!busy);
        binding.statusButton.setEnabled(!busy);
        binding.serviceButton.setEnabled(!busy);
        if (binding.addDeviceButton != null) binding.addDeviceButton.setEnabled(!busy && currentProfile(binding) != null);
        if (binding.refreshButton != null) binding.refreshButton.setEnabled(!busy && currentProfile(binding) != null);
        if (binding.settingsBackButton != null) binding.settingsBackButton.setEnabled(!busy);
        binding.server.setEnabled(!busy);
        binding.enabled.setEnabled(!busy);
        binding.keepOnline.setEnabled(!busy);
        for (MaterialButton button : binding.actions) button.setEnabled(!busy);
    }

    private static void resetDetect(Binding binding) {
        binding.autoDetected = false;
        binding.autoDetectStarted = false;
        App.removeCallbacks(binding.detectRetry);
    }

    private static void scheduleDetectRetry(Binding binding) {
        if (binding.detectRetry == null || binding.dialog == null || !binding.dialog.isShowing()) return;
        App.post(binding.detectRetry, DETECT_RETRY_MS);
    }

    private static void detach(View view) {
        if (view == null || !(view.getParent() instanceof ViewGroup)) return;
        ((ViewGroup) view.getParent()).removeView(view);
    }

    private static LinearLayoutCompat dialogRoot(Context context) {
        LinearLayoutCompat root = new LinearLayoutCompat(context);
        root.setOrientation(LinearLayoutCompat.VERTICAL);
        root.setPadding(dp(context, 2), dp(context, 4), dp(context, 2), 0);
        return root;
    }

    private static LinearLayoutCompat row(Context context) {
        LinearLayoutCompat row = new LinearLayoutCompat(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayoutCompat.HORIZONTAL);
        return row;
    }

    private static MaterialTextView sectionTitle(Context context, int resId) {
        return sectionTitle(context, context.getString(resId));
    }

    private static MaterialTextView sectionTitle(Context context, String value) {
        MaterialTextView view = text(context, value, 15, "#202124", true);
        view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    private static MaterialTextView caption(Context context, int resId) {
        return text(context, context.getString(resId), 12, "#5F6368", false);
    }

    private static MaterialTextView panel(Context context, String value) {
        MaterialTextView view = text(context, value, 13, "#3C4043", false);
        view.setTextIsSelectable(true);
        view.setLineSpacing(0, 1.08f);
        view.setPadding(dp(context, 10), dp(context, 9), dp(context, 10), dp(context, 9));
        view.setBackground(background(context, "#F8F9FA", Color.parseColor("#DADCE0"), 8));
        return view;
    }

    private static LinearLayoutCompat statusDetailPanel(Context context, RemoteProfile profile, Binding binding) {
        LinearLayoutCompat card = card(context);
        MaterialTextView title = text(context, context.getString(R.string.remote_trust_local_status), 14, "#202124", true);
        card.addView(title, matchWrap());
        MaterialTextView detail = text(context, localStatus(context, profile) + serviceSuffix(binding), 12, "#5F6368", false);
        detail.setPadding(0, dp(context, 4), 0, 0);
        card.addView(detail, matchWrap());
        return card;
    }

    private static String serviceSuffix(Binding binding) {
        if (TextUtils.isEmpty(binding.serviceDetailText)) return "";
        return "\n" + binding.serviceDetailText;
    }

    private static LinearLayoutCompat serverTitleRow(Context context, Binding binding) {
        LinearLayoutCompat top = row(context);
        MaterialTextView title = text(context, context.getString(R.string.remote_trust_settings_title), 15, "#202124", true);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        top.addView(title, weight());
        MaterialButton qr = iconButton(context, R.drawable.ic_remote_qr, context.getString(R.string.remote_trust_server_qr));
        qr.setOnClickListener(v -> showServerQr((FragmentActivity) context, binding));
        bindAction(binding, qr);
        top.addView(qr, fixed(context, 36, 32));
        MaterialButton scan = iconButton(context, R.drawable.ic_remote_scan, context.getString(R.string.remote_trust_scan_server));
        scan.setOnClickListener(v -> startScan((FragmentActivity) context, binding));
        bindAction(binding, scan);
        top.addView(scan, fixed(context, 36, 32));
        return top;
    }

    private static void showServerQr(FragmentActivity activity, Binding binding) {
        String server = currentServerText(binding);
        if (TextUtils.isEmpty(server)) {
            Notify.show(R.string.remote_trust_server_required);
            return;
        }
        LinearLayoutCompat root = dialogRoot(activity);
        ImageView image = new ImageView(activity);
        image.setImageBitmap(QRCode.getLightBitmap(server, 220, 1));
        image.setAdjustViewBounds(true);
        image.setBackgroundColor(Color.WHITE);
        image.setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 10));
        root.addView(image, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 240)));
        MaterialTextView value = text(activity, server, 12, "#5F6368", false);
        value.setGravity(Gravity.CENTER);
        value.setTextIsSelectable(true);
        value.setPadding(0, dp(activity, 8), 0, 0);
        root.addView(value, matchWrap());
        new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(R.string.remote_trust_server_qr)
                .setView(root)
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private static String currentServerText(Binding binding) {
        String server = textOf(binding.server);
        if (!TextUtils.isEmpty(server)) return server;
        RemoteProfile profile = currentProfile(binding);
        if (profile == null) profile = RemoteStore.firstProfile();
        if (profile == null) return "";
        return TextUtils.isEmpty(profile.serverUrl) ? profile.serverOrigin : profile.serverUrl;
    }

    private static LinearLayoutCompat serverInfoPanel(Context context, Binding binding, RemoteProfile profile) {
        LinearLayoutCompat card = card(context);
        LinearLayoutCompat top = serverTitleRow(context, binding);
        MaterialButton edit = iconButton(context, R.drawable.ic_git_cloud_edit, context.getString(R.string.remote_trust_edit_server));
        edit.setOnClickListener(v -> {
            binding.serverEditing = true;
            render(context, binding);
        });
        bindAction(binding, edit);
        top.addView(edit, fixed(context, 36, 32));
        card.addView(top, matchWrap());
        String server = profile == null ? "-" : (TextUtils.isEmpty(profile.serverUrl) ? profile.serverOrigin : profile.serverUrl);
        MaterialTextView text = text(context, server, 13, "#3C4043", false);
        text.setTextIsSelectable(true);
        text.setLineSpacing(0, 1.08f);
        text.setPadding(0, dp(context, 6), 0, 0);
        card.addView(text, matchWrap());
        return card;
    }

    private static LinearLayoutCompat commandResultPanel(Context context, Binding binding) {
        LinearLayoutCompat card = card(context);
        LinearLayoutCompat top = row(context);
        MaterialTextView title = text(context, context.getString(R.string.remote_trust_command_log_title), 14, "#202124", true);
        top.addView(title, weight());
        MaterialButton copy = iconButton(context, R.drawable.ic_remote_copy, context.getString(R.string.remote_trust_copy));
        copy.setOnClickListener(v -> copyText(context, context.getString(R.string.remote_trust_command_log_title), binding.lastResult, R.string.remote_trust_result_copied));
        bindAction(binding, copy);
        top.addView(copy, fixed(context, 36, 32));
        card.addView(top, matchWrap());
        NestedScrollView scroll = new NestedScrollView(context);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        MaterialTextView detail = text(context, binding.lastResult, 12, "#3C4043", false);
        detail.setTextIsSelectable(true);
        detail.setLineSpacing(0, 1.08f);
        detail.setPadding(0, dp(context, 6), dp(context, 4), dp(context, 6));
        scroll.addView(detail, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(scroll, topMargin(fixedHeight(context, 180), 4));
        return card;
    }

    private static LinearLayoutCompat servicePanel(Context context, Binding binding, String state, String detail) {
        LinearLayoutCompat card = card(context);
        LinearLayoutCompat row = row(context);
        MaterialTextView title = text(context, state, 14, "#202124", true);
        row.addView(title, weight());
        if (!TextUtils.isEmpty(binding.diagnostics)) {
            MaterialButton copy = iconButton(context, R.drawable.ic_remote_copy, context.getString(R.string.remote_trust_copy_diagnostics));
            copy.setOnClickListener(v -> copyText(context, context.getString(R.string.setting_remote_trust), binding.diagnostics, R.string.remote_trust_diagnostics_copied));
            bindAction(binding, copy);
            row.addView(copy, fixed(context, 36, 32));
        }
        card.addView(row, matchWrap());
        if (!TextUtils.isEmpty(detail)) {
            MaterialTextView text = text(context, detail, 12, "#5F6368", false);
            text.setPadding(0, dp(context, 4), 0, 0);
            text.setMaxLines(5);
            text.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(text, matchWrap());
        }
        return card;
    }

    private static LinearLayoutCompat emptyPanel(Context context, String value) {
        LinearLayoutCompat card = card(context);
        MaterialTextView text = text(context, value, 13, "#5F6368", false);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, dp(context, 8), 0, dp(context, 8));
        card.addView(text, matchWrap());
        return card;
    }

    private static LinearLayoutCompat card(Context context) {
        LinearLayoutCompat view = new LinearLayoutCompat(context);
        view.setOrientation(LinearLayoutCompat.VERTICAL);
        view.setPadding(dp(context, 10), dp(context, 9), dp(context, 10), dp(context, 9));
        view.setBackground(background(context, "#F8F9FA", Color.parseColor("#DADCE0"), 8));
        return view;
    }

    private static MaterialTextView text(Context context, String value, int sp, String color, boolean bold) {
        MaterialTextView view = new MaterialTextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.parseColor(color));
        if (bold) view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    private static TextInputEditText input(Context context, int inputType, boolean singleLine) {
        TextInputEditText input = new TextInputEditText(context);
        input.setInputType(inputType);
        input.setSingleLine(singleLine);
        input.setTextSize(14);
        input.setMinHeight(dp(context, 46));
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        input.setSelectAllOnFocus(false);
        input.setTextColor(Color.BLACK);
        input.setHintTextColor(Color.parseColor("#666666"));
        return input;
    }

    private static TextInputLayout inputLayout(Context context, int hint, TextInputEditText input) {
        TextInputLayout layout = new TextInputLayout(context);
        layout.setHint(context.getString(hint));
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxBackgroundColor(Color.WHITE);
        layout.setBoxStrokeColor(ContextCompat.getColor(context, R.color.dialog_outlined_button_stroke));
        layout.setHintTextColor(ColorStateList.valueOf(Color.parseColor("#5F6368")));
        layout.setBoxCornerRadii(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
        layout.addView(input, matchWrap());
        return layout;
    }

    private static com.google.android.material.checkbox.MaterialCheckBox check(Context context, int resId) {
        com.google.android.material.checkbox.MaterialCheckBox box = new com.google.android.material.checkbox.MaterialCheckBox(context);
        box.setText(resId);
        box.setTextSize(14);
        box.setButtonTintList(ContextCompat.getColorStateList(context, R.color.dialog_checkbox_tint));
        box.setPadding(0, 0, 0, 0);
        return box;
    }

    private static MaterialButton tab(Context context, int resId) {
        MaterialButton button = segment(context, context.getString(resId));
        button.setCheckable(true);
        return button;
    }

    private static MaterialButton primaryAction(Binding binding, Context context, int resId) {
        MaterialButton button = primary(context, context.getString(resId));
        bindAction(binding, button);
        return button;
    }

    private static MaterialButton tonalAction(Binding binding, Context context, int resId) {
        MaterialButton button = tonal(context, context.getString(resId));
        bindAction(binding, button);
        return button;
    }

    private static MaterialButton outlineAction(Binding binding, Context context, int resId) {
        MaterialButton button = outline(context, context.getString(resId));
        bindAction(binding, button);
        return button;
    }

    private static MaterialButton dangerAction(Binding binding, Context context, int resId) {
        MaterialButton button = outlineAction(binding, context, resId);
        button.setTextColor(Color.parseColor("#B3261E"));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#F2B8B5")));
        return button;
    }

    private static MaterialButton smallOutlineAction(Binding binding, Context context, int resId) {
        MaterialButton button = outlineAction(binding, context, resId);
        button.setTextSize(12);
        return button;
    }

    private static MaterialButton button(Context context, int resId) {
        return button(context, context.getString(resId));
    }

    private static MaterialButton button(Context context, String text) {
        MaterialButton button = new MaterialButton(context);
        button.setText(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(dp(context, 32));
        button.setMinimumHeight(dp(context, 32));
        button.setPadding(dp(context, 6), 0, dp(context, 6), 0);
        button.setMaxLines(2);
        button.setEllipsize(TextUtils.TruncateAt.END);
        return button;
    }

    private static MaterialButton primary(Context context, String text) {
        MaterialButton button = button(context, text);
        button.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.dialog_primary_button_bg));
        button.setTextColor(ContextCompat.getColorStateList(context, R.color.dialog_primary_button_text));
        return button;
    }

    private static MaterialButton tonal(Context context, String text) {
        MaterialButton button = button(context, text);
        button.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.dialog_tonal_button_bg));
        button.setTextColor(ContextCompat.getColorStateList(context, R.color.dialog_tonal_button_text));
        return button;
    }

    private static MaterialButton outline(Context context, String text) {
        MaterialButton button = button(context, text);
        button.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.dialog_outlined_button_bg));
        button.setTextColor(ContextCompat.getColorStateList(context, R.color.dialog_outlined_button_text));
        button.setStrokeColor(ContextCompat.getColorStateList(context, R.color.dialog_outlined_button_stroke));
        button.setStrokeWidth(dp(context, 1));
        return button;
    }

    private static MaterialButton segment(Context context, String text) {
        MaterialButton button = outline(context, text);
        button.setBackgroundTintList(segmentBackground());
        button.setTextColor(segmentText());
        button.setStrokeColor(segmentStroke());
        return button;
    }

    private static MaterialButton closeButton(Context context) {
        MaterialButton button = button(context, "×");
        button.setTextSize(20);
        button.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.dialog_outlined_button_bg));
        button.setTextColor(Color.parseColor("#5F6368"));
        return button;
    }

    private static MaterialButton compactButton(Context context, int resId) {
        MaterialButton button = tonal(context, context.getString(resId));
        button.setTextSize(12);
        return button;
    }

    private static MaterialButton pillButton(Context context, String text) {
        MaterialButton button = outline(context, text);
        button.setTextSize(12);
        button.setMaxLines(1);
        button.setEllipsize(TextUtils.TruncateAt.END);
        return button;
    }

    private static MaterialButton statusButton(Context context, String text) {
        MaterialButton button = pillButton(context, text);
        button.setStrokeWidth(dp(context, 1));
        return button;
    }

    private static void applyDeviceStyle(Context context, MaterialButton button, boolean online) {
        button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(online ? "#E6F4EA" : "#FCE8E6")));
        button.setTextColor(Color.parseColor(online ? "#137333" : "#B3261E"));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor(online ? "#CEEAD6" : "#F2B8B5")));
    }

    private static MaterialButton iconButton(Context context, int icon, String label) {
        MaterialButton button = outline(context, "");
        button.setContentDescription(label);
        button.setIconResource(icon);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconPadding(0);
        button.setIconSize(dp(context, 18));
        button.setIconTint(ContextCompat.getColorStateList(context, R.color.dialog_outlined_button_text));
        button.setMinWidth(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private static MaterialButton listButton(Context context, String text) {
        MaterialButton button = outline(context, text);
        button.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        button.setTextColor(Color.parseColor("#202124"));
        button.setMinHeight(dp(context, 56));
        button.setMaxLines(3);
        return button;
    }

    private static MaterialButton deviceButton(Context context, String text, boolean online) {
        MaterialButton button = listButton(context, text);
        applyDeviceStyle(context, button, online);
        return button;
    }

    private static void bindAction(Binding binding, MaterialButton button) {
        binding.actions.add(button);
    }

    private static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(Color.parseColor("#E8EAED"));
        view.setMinimumHeight(dp(context, 1));
        return view;
    }

    private static GradientDrawable background(Context context, String color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(color));
        drawable.setCornerRadius(dp(context, radius));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    private static ColorStateList segmentBackground() {
        return new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_enabled},
                new int[]{android.R.attr.state_focused},
                new int[]{android.R.attr.state_pressed},
                new int[]{}
        }, new int[]{
                Color.parseColor("#0B57D0"),
                Color.parseColor("#F1F3F4"),
                Color.parseColor("#E8F0FE"),
                Color.parseColor("#E8F0FE"),
                Color.WHITE
        });
    }

    private static ColorStateList segmentText() {
        return new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_enabled},
                new int[]{}
        }, new int[]{
                Color.WHITE,
                Color.parseColor("#9AA0A6"),
                Color.parseColor("#202124")
        });
    }

    private static ColorStateList segmentStroke() {
        return new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{android.R.attr.state_focused},
                new int[]{android.R.attr.state_pressed},
                new int[]{}
        }, new int[]{
                Color.parseColor("#0B57D0"),
                Color.parseColor("#0B57D0"),
                Color.parseColor("#0B57D0"),
                Color.parseColor("#C8CDD2")
        });
    }

    private static LinearLayoutCompat.LayoutParams matchWrap() {
        return new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayoutCompat.LayoutParams weight() {
        return new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private static LinearLayoutCompat.LayoutParams leftWeight(Context context) {
        LinearLayoutCompat.LayoutParams params = weight();
        params.setMarginStart(dp(context, 8));
        return params;
    }

    private static LinearLayoutCompat.LayoutParams leftWeight(Context context, int marginDp) {
        LinearLayoutCompat.LayoutParams params = weight();
        params.setMarginStart(dp(context, marginDp));
        return params;
    }

    private static LinearLayoutCompat.LayoutParams fixed(Context context, int widthDp, int heightDp) {
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(dp(context, widthDp), dp(context, heightDp));
        params.setMarginStart(dp(context, 8));
        return params;
    }

    private static LinearLayoutCompat.LayoutParams fixedHeight(Context context, int heightDp) {
        return new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, heightDp));
    }

    private static LinearLayoutCompat.LayoutParams compactWrap() {
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = 0;
        params.bottomMargin = 0;
        return params;
    }

    private static LinearLayoutCompat.LayoutParams topMargin(LinearLayoutCompat.LayoutParams params, int topDp) {
        params.topMargin = dp(App.get(), topDp);
        return params;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class DeviceRow {
        private final RemoteGroup group;
        private final RemoteDevice device;

        private DeviceRow(RemoteGroup group, RemoteDevice device) {
            this.group = group;
            this.device = device;
        }
    }

    private static final class Binding {
        private LinearLayoutCompat root;
        private NestedScrollView scroll;
        private LinearLayoutCompat toolbar;
        private AlertDialog dialog;
        private Runnable callback;
        private Runnable detectRetry;
        private Runnable deviceRefreshRetry;
        private LinearLayoutCompat content;
        private MaterialTextView summary;
        private MaterialButton close;
        private MaterialButton bindCodeButton;
        private MaterialButton enableToggle;
        private MaterialButton statusButton;
        private MaterialButton serviceButton;
        private MaterialButton settingsBackButton;
        private MaterialButton addDeviceButton;
        private MaterialButton refreshButton;
        private TextInputEditText server;
        private TextInputLayout serverLayout;
        private com.google.android.material.checkbox.MaterialCheckBox enabled;
        private com.google.android.material.checkbox.MaterialCheckBox keepOnline;
        private final List<MaterialButton> actions = new ArrayList<>();
        private boolean initialized;
        private boolean busy;
        private boolean statusExpanded;
        private boolean deviceStatusExpanded;
        private boolean advancedExpanded;
        private boolean serverEditing;
        private boolean autoBindAttempted;
        private boolean autoDetectStarted;
        private boolean autoDetected;
        private boolean creatingBindCode;
        private int page = PAGE_DEVICES;
        private int pendingDeviceRefreshes;
        private String bindCode = "";
        private String selectedGroupId = "";
        private String selectedDeviceId = "";
        private String lastResult = "";
        private String serviceStateText = "";
        private String serviceDetailText = "";
        private String diagnostics = "";
    }
}
