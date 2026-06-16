package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.gitcloud.AccountInfo;
import com.fongmi.android.tv.gitcloud.CreateRepoRequest;
import com.fongmi.android.tv.gitcloud.GitAccount;
import com.fongmi.android.tv.gitcloud.GitCloudAccountStore;
import com.fongmi.android.tv.gitcloud.GitCloudPaths;
import com.fongmi.android.tv.gitcloud.GitFile;
import com.fongmi.android.tv.gitcloud.GitProviderType;
import com.fongmi.android.tv.gitcloud.GitRepo;
import com.fongmi.android.tv.gitcloud.ProviderCapabilities;
import com.fongmi.android.tv.gitcloud.SaveOptions;
import com.fongmi.android.tv.gitcloud.drive.CommitResult;
import com.fongmi.android.tv.gitcloud.drive.FileChange;
import com.fongmi.android.tv.gitcloud.drive.GitDriveConfig;
import com.fongmi.android.tv.gitcloud.drive.JGitDriveEngine;
import com.fongmi.android.tv.gitcloud.provider.GitCloudProvider;
import com.fongmi.android.tv.gitcloud.provider.GitCloudProviders;
import com.fongmi.android.tv.gitcloud.secure.GitCloudTokenStore;
import com.fongmi.android.tv.utils.Formatters;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SyncFiles;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.utils.Path;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GitCloudDialog extends BaseAlertDialog {

    private final JGitDriveEngine driveEngine = new JGitDriveEngine();
    private final ActivityResultLauncher<Intent> filePicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
        handleFileUri(result.getData().getData());
    });
    private final ActivityResultLauncher<Intent> folderPicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
        handleTreeUri(result.getData().getData());
    });
    private DialogBinding binding;
    private Runnable callback;
    private GitProviderType providerType = GitProviderType.GITHUB;
    private GitAccount account;
    private GitRepo repo;
    private String currentPath = "";
    private boolean busy;
    private boolean editingAccount;
    private String reposAccountId;
    private final List<GitRepo> repos = new ArrayList<>();
    private final Map<String, List<GitFile>> fileTree = new HashMap<>();
    private final Set<String> expandedPaths = new HashSet<>();

    public static void show(Fragment fragment, Runnable callback) {
        show(fragment.requireActivity(), callback);
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        GitCloudDialog dialog = new GitCloudDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        buildView();
        return binding;
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(requireContext());
        params.width = (int) (ResUtil.getScreenWidth(requireContext()) * (land ? 0.72f : 0.94f));
        params.height = land ? (int) (ResUtil.getScreenHeight(requireContext()) * 0.86f) : WindowManager.LayoutParams.WRAP_CONTENT;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
    }

    @Override
    protected void initView() {
        account = GitCloudAccountStore.first(providerType);
        if (account != null) providerType = account.providerType;
        editingAccount = account == null;
        populateAccountForm(account);
        render();
        if (account != null) refreshRepos();
    }

    @Override
    protected void initEvent() {
        binding.close.setOnClickListener(view -> dismiss());
        binding.github.setOnClickListener(view -> switchProvider(GitProviderType.GITHUB));
        binding.cnb.setOnClickListener(view -> switchProvider(GitProviderType.CNB));
        binding.tokenLink.setOnClickListener(view -> open(tokenUrl()));
        binding.helpLink.setOnClickListener(view -> open(helpUrl()));
        binding.save.setOnClickListener(view -> saveAccount(true));
        binding.refresh.setOnClickListener(view -> refreshRepos());
        binding.editAccount.setOnClickListener(view -> editAccount());
        binding.removeAccount.setOnClickListener(view -> removeAccount());
        binding.changeRepo.setOnClickListener(view -> changeRepo());
        binding.refreshTree.setOnClickListener(view -> reloadTree());
        binding.createPrivate.setOnClickListener(view -> createRepo(true));
        binding.createPublic.setOnClickListener(view -> createRepo(false));
        binding.uploadText.setOnClickListener(view -> showUploadText());
        binding.uploadFile.setOnClickListener(view -> chooseUploadFile());
        binding.uploadFolder.setOnClickListener(view -> chooseUploadFolder());
        binding.backup.setOnClickListener(view -> backup());
        binding.clearCache.setOnClickListener(view -> clearCache());
    }

    @Override
    public void onDestroyView() {
        if (callback != null) callback.run();
        super.onDestroyView();
    }

    private View buildView() {
        LinearLayoutCompat root = new LinearLayoutCompat(requireContext());
        root.setOrientation(LinearLayoutCompat.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.setBackground(round(Color.WHITE, 12, Color.TRANSPARENT));

        LinearLayoutCompat header = row();
        MaterialTextView title = text("Git 云盘", 20, Color.BLACK, true);
        header.addView(title, new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        binding = new DialogBinding(root);
        binding.close = iconButton("×");
        header.addView(binding.close, new LinearLayoutCompat.LayoutParams(dp(42), dp(42)));
        root.addView(header);

        binding.status = text("", 13, Color.parseColor("#5F6368"), false);
        binding.status.setPadding(0, dp(6), 0, dp(10));
        root.addView(binding.status);

        binding.scroll = new NestedScrollView(requireContext());
        binding.scroll.setFillViewport(false);
        LinearLayoutCompat content = new LinearLayoutCompat(requireContext());
        content.setOrientation(LinearLayoutCompat.VERTICAL);
        binding.scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(binding.scroll, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(section("账号管理"));
        LinearLayoutCompat provider = row();
        binding.github = segment("GitHub");
        binding.cnb = segment("CNB");
        provider.addView(binding.github, new LinearLayoutCompat.LayoutParams(0, dp(40), 1));
        LinearLayoutCompat.LayoutParams cnbParams = new LinearLayoutCompat.LayoutParams(0, dp(40), 1);
        cnbParams.leftMargin = dp(8);
        provider.addView(binding.cnb, cnbParams);
        content.addView(provider);

        binding.accountCard = card();
        LinearLayoutCompat accountTop = row();
        binding.accountName = text("", 15, Color.BLACK, true);
        accountTop.addView(binding.accountName, new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        binding.accountBadge = pill("");
        accountTop.addView(binding.accountBadge);
        binding.accountCard.addView(accountTop);
        binding.accountMeta = detail("");
        binding.accountCard.addView(binding.accountMeta);
        LinearLayoutCompat accountCardActions = row();
        binding.editAccount = compact("更换账号");
        binding.removeAccount = outline("退出登录");
        accountCardActions.addView(binding.editAccount, new LinearLayoutCompat.LayoutParams(0, dp(36), 1));
        LinearLayoutCompat.LayoutParams removeParams = new LinearLayoutCompat.LayoutParams(0, dp(36), 1);
        removeParams.leftMargin = dp(8);
        accountCardActions.addView(binding.removeAccount, removeParams);
        binding.accountCard.addView(accountCardActions);
        content.addView(binding.accountCard);

        binding.loginForm = list();
        binding.alias = input("账号备注", false);
        binding.baseUrl = input("服务地址", false);
        binding.token = input("Token", true);
        binding.loginForm.addView(binding.alias.layout);
        binding.loginForm.addView(binding.baseUrl.layout);
        binding.loginForm.addView(binding.token.layout);

        LinearLayoutCompat links = row();
        binding.tokenLink = outline("获取 Token");
        binding.helpLink = outline("权限说明");
        links.addView(binding.tokenLink, new LinearLayoutCompat.LayoutParams(0, dp(38), 1));
        LinearLayoutCompat.LayoutParams helpParams = new LinearLayoutCompat.LayoutParams(0, dp(38), 1);
        helpParams.leftMargin = dp(8);
        links.addView(binding.helpLink, helpParams);
        binding.loginForm.addView(links);

        LinearLayoutCompat accountActions = row();
        binding.save = primary("保存并校验");
        accountActions.addView(binding.save, new LinearLayoutCompat.LayoutParams(0, dp(42), 1));
        binding.loginForm.addView(accountActions);
        content.addView(binding.loginForm);

        binding.repoPanel = list();
        binding.repoPanel.addView(section("选择仓库"));
        binding.refresh = tonal("同步仓库列表");
        binding.repoPanel.addView(binding.refresh, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        binding.repoList = list();
        binding.repoPanel.addView(binding.repoList);
        content.addView(binding.repoPanel);

        binding.createPanel = list();
        binding.createPanel.addView(section("创建仓库"));
        LinearLayoutCompat repoActions = row();
        binding.createPrivate = tonal("私有备份库");
        binding.createPublic = outline("公开资源库");
        repoActions.addView(binding.createPrivate, new LinearLayoutCompat.LayoutParams(0, dp(40), 1));
        LinearLayoutCompat.LayoutParams publicParams = new LinearLayoutCompat.LayoutParams(0, dp(40), 1);
        publicParams.leftMargin = dp(8);
        repoActions.addView(binding.createPublic, publicParams);
        binding.createPanel.addView(repoActions);
        content.addView(binding.createPanel);

        binding.filePanel = list();
        binding.filePanel.addView(section("目录树"));
        LinearLayoutCompat selectedRepo = card();
        LinearLayoutCompat selectedTop = row();
        binding.repoTitle = text("", 15, Color.BLACK, true);
        selectedTop.addView(binding.repoTitle, new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        binding.repoBadge = pill("");
        selectedTop.addView(binding.repoBadge);
        selectedRepo.addView(selectedTop);
        binding.pathText = detail("");
        selectedRepo.addView(binding.pathText);
        LinearLayoutCompat selectedActions = row();
        binding.changeRepo = compact("更换仓库");
        binding.refreshTree = outline("刷新目录");
        selectedActions.addView(binding.changeRepo, new LinearLayoutCompat.LayoutParams(0, dp(36), 1));
        LinearLayoutCompat.LayoutParams refreshTreeParams = new LinearLayoutCompat.LayoutParams(0, dp(36), 1);
        refreshTreeParams.leftMargin = dp(8);
        selectedActions.addView(binding.refreshTree, refreshTreeParams);
        selectedRepo.addView(selectedActions);
        binding.filePanel.addView(selectedRepo);

        LinearLayoutCompat fileActions = row();
        binding.uploadText = tonal("新建文件");
        binding.uploadFile = tonal("上传文件");
        binding.uploadFolder = tonal("上传目录");
        fileActions.addView(binding.uploadText, new LinearLayoutCompat.LayoutParams(0, dp(40), 1));
        LinearLayoutCompat.LayoutParams uploadFileParams = new LinearLayoutCompat.LayoutParams(0, dp(40), 1);
        uploadFileParams.leftMargin = dp(8);
        fileActions.addView(binding.uploadFile, uploadFileParams);
        LinearLayoutCompat.LayoutParams uploadFolderParams = new LinearLayoutCompat.LayoutParams(0, dp(40), 1);
        uploadFolderParams.leftMargin = dp(8);
        fileActions.addView(binding.uploadFolder, uploadFolderParams);
        binding.filePanel.addView(fileActions);

        LinearLayoutCompat moreFileActions = row();
        binding.backup = tonal("一键备份");
        binding.clearCache = outline("清理缓存");
        moreFileActions.addView(binding.backup, new LinearLayoutCompat.LayoutParams(0, dp(40), 1));
        LinearLayoutCompat.LayoutParams cacheParams = new LinearLayoutCompat.LayoutParams(0, dp(40), 1);
        cacheParams.leftMargin = dp(8);
        moreFileActions.addView(binding.clearCache, cacheParams);
        binding.filePanel.addView(moreFileActions);
        binding.fileList = list();
        binding.filePanel.addView(binding.fileList);
        content.addView(binding.filePanel);
        return root;
    }

    private void render() {
        if (binding == null) return;
        boolean loggedIn = account != null && account.providerType == providerType && !editingAccount;
        binding.github.setChecked(providerType == GitProviderType.GITHUB);
        binding.cnb.setChecked(providerType == GitProviderType.CNB);
        binding.baseUrl.layout.setVisibility(View.GONE);
        binding.accountCard.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        binding.loginForm.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        binding.repoPanel.setVisibility(loggedIn && repo == null ? View.VISIBLE : View.GONE);
        binding.createPanel.setVisibility(loggedIn && repo == null ? View.VISIBLE : View.GONE);
        binding.filePanel.setVisibility(loggedIn && repo != null ? View.VISIBLE : View.GONE);
        binding.refresh.setEnabled(loggedIn && !busy);
        binding.createPrivate.setEnabled(loggedIn && !busy);
        binding.createPublic.setEnabled(loggedIn && !busy);
        binding.uploadText.setEnabled(repo != null && !busy);
        binding.uploadFile.setEnabled(repo != null && !busy);
        binding.uploadFolder.setEnabled(repo != null && !busy);
        binding.backup.setEnabled(repo != null && !busy);
        binding.clearCache.setEnabled(repo != null && !busy);
        binding.changeRepo.setEnabled(!busy);
        binding.refreshTree.setEnabled(repo != null && !busy);
        if (loggedIn) {
            binding.accountName.setText(account.displayName());
            binding.accountBadge.setText(label(providerType));
            binding.accountMeta.setText(meta(account));
        }
        if (repo != null) {
            binding.repoTitle.setText(repo.displayName());
            binding.repoBadge.setText(repo.privateRepo ? "私有" : "公开");
            binding.pathText.setText(pathLabel());
        }
        setStatus(statusText());
    }

    private void switchProvider(GitProviderType type) {
        providerType = type;
        account = GitCloudAccountStore.first(type);
        repo = null;
        currentPath = "";
        repos.clear();
        fileTree.clear();
        expandedPaths.clear();
        reposAccountId = null;
        editingAccount = account == null;
        populateAccountForm(account);
        render();
        if (account != null) refreshRepos();
    }

    private void saveAccount(boolean loadRepos) {
        String token = value(binding.token.edit);
        if (TextUtils.isEmpty(token) && account != null) {
            try {
                token = GitCloudTokenStore.get(account.tokenKey);
            } catch (Exception e) {
                token = "";
            }
        }
        if (TextUtils.isEmpty(token)) {
            Notify.show("Token 为空");
            return;
        }
        String authToken = token;
        GitAccount target = account != null && account.providerType == providerType ? account : GitAccount.create(providerType, value(binding.baseUrl.edit), value(binding.alias.edit));
        target.baseUrl = providerType == GitProviderType.CNB ? "https://cnb.cool" : value(binding.baseUrl.edit);
        target.remark = value(binding.alias.edit);
        run("校验账号中", () -> {
            GitCloudProvider provider = provider();
            AccountInfo info = provider.validateToken(target, authToken);
            target.username = info.username;
            target.lastValidatedAt = System.currentTimeMillis();
            GitCloudTokenStore.put(target.tokenKey, authToken);
            GitCloudAccountStore.save(target);
            account = target;
            App.post(() -> {
                editingAccount = false;
                repo = null;
                currentPath = "";
                repos.clear();
                fileTree.clear();
                expandedPaths.clear();
                reposAccountId = null;
                render();
                if (loadRepos) refreshRepos();
            });
        });
    }

    private void refreshRepos() {
        if (account == null) {
            saveAccount(true);
            return;
        }
        run("读取仓库中", () -> {
            List<GitRepo> items = provider().listRepos(account, token());
            App.post(() -> showRepos(items));
        });
    }

    private void showRepos(List<GitRepo> items) {
        repos.clear();
        repos.addAll(items);
        reposAccountId = account == null ? null : account.id;
        binding.repoList.removeAllViews();
        if (items.isEmpty()) {
            binding.repoList.addView(empty("暂无仓库"));
            render();
            return;
        }
        for (GitRepo item : items) binding.repoList.addView(repoRow(item));
        render();
    }

    private View repoRow(GitRepo item) {
        LinearLayoutCompat root = card();
        LinearLayoutCompat top = row();
        top.addView(text(item.displayName(), 15, Color.BLACK, true), new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(pill(item.privateRepo ? "私有" : "公开"));
        root.addView(top);
        root.addView(detail((TextUtils.isEmpty(item.defaultBranch) ? "main" : item.defaultBranch) + " · " + size(item.sizeKb * 1024)));
        LinearLayoutCompat actions = row();
        MaterialButton open = compact("打开仓库");
        open.setOnClickListener(view -> openRepo(item));
        actions.addView(open, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        root.addView(actions);
        root.setOnClickListener(view -> openRepo(item));
        return root;
    }

    private void openRepo(GitRepo item) {
        repo = item;
        currentPath = "";
        fileTree.clear();
        expandedPaths.clear();
        expandedPaths.add("");
        render();
        browse(item, "");
    }

    private void browse(GitRepo target, String path) {
        repo = target;
        currentPath = path == null ? "" : path;
        expandedPaths.add(currentPath);
        render();
        run("读取文件中", () -> {
            List<GitFile> files = provider().listFiles(account, token(), target, target.defaultBranch, currentPath);
            App.post(() -> showFiles(currentPath, files));
        });
    }

    private void showFiles(String path, List<GitFile> files) {
        fileTree.put(path == null ? "" : path, files);
        renderFileTree();
    }

    private void renderFileTree() {
        if (binding == null || binding.fileList == null) return;
        binding.fileList.removeAllViews();
        if (repo == null) return;
        binding.fileList.addView(treeRootRow());
        List<GitFile> files = fileTree.get("");
        if (files == null) {
            binding.fileList.addView(empty("目录加载中"));
            return;
        }
        if (files.isEmpty()) {
            binding.fileList.addView(empty("目录为空"));
            return;
        }
        for (GitFile file : files) binding.fileList.addView(fileTreeRow(file, 1));
    }

    private View treeRootRow() {
        LinearLayoutCompat root = card();
        root.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayoutCompat line = row();
        MaterialButton toggle = treeToggle(expandedPaths.contains("") ? "−" : "+");
        toggle.setOnClickListener(view -> toggleTree(""));
        line.addView(toggle, new LinearLayoutCompat.LayoutParams(dp(34), dp(34)));
        MaterialTextView name = text(repo == null ? "全部文件" : repo.name, 15, Color.BLACK, true);
        name.setPadding(dp(8), 0, 0, 0);
        line.addView(name, new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(line);
        return root;
    }

    private View fileTreeRow(GitFile file, int depth) {
        LinearLayoutCompat root = card();
        root.setPadding(dp(10 + depth * 14), dp(8), dp(10), dp(8));
        LinearLayoutCompat line = row();
        MaterialButton toggle = treeToggle(file.directory ? expandedPaths.contains(file.path) ? "−" : "+" : "");
        toggle.setEnabled(file.directory);
        toggle.setOnClickListener(view -> toggleTree(file.path));
        line.addView(toggle, new LinearLayoutCompat.LayoutParams(dp(34), dp(34)));
        LinearLayoutCompat info = new LinearLayoutCompat(requireContext());
        info.setOrientation(LinearLayoutCompat.VERTICAL);
        MaterialTextView name = text((file.directory ? "📁 " : "📄 ") + file.name, 14, Color.BLACK, true);
        MaterialTextView meta = text(file.directory ? file.path : file.path + " · " + size(file.size), 11, Color.parseColor("#5F6368"), false);
        info.addView(name);
        info.addView(meta);
        line.addView(info, new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton action = compact(file.directory ? "打开" : "复制 raw");
        action.setOnClickListener(view -> {
            if (file.directory) toggleTree(file.path);
            else copy(file.rawUrl);
        });
        line.addView(action, new LinearLayoutCompat.LayoutParams(dp(file.directory ? 62 : 82), dp(34)));
        root.addView(line);
        if (file.directory && expandedPaths.contains(file.path)) {
            List<GitFile> children = fileTree.get(file.path);
            if (children == null) root.addView(empty("加载中"));
            else if (children.isEmpty()) root.addView(empty("目录为空"));
            else for (GitFile child : children) root.addView(fileTreeRow(child, depth + 1));
        }
        return root;
    }

    private void toggleTree(String path) {
        String key = path == null ? "" : path;
        if (expandedPaths.contains(key) && !TextUtils.isEmpty(key)) {
            expandedPaths.remove(key);
            renderFileTree();
            return;
        }
        expandedPaths.add(key);
        currentPath = key;
        if (fileTree.containsKey(key)) {
            render();
            renderFileTree();
        } else {
            browse(repo, key);
        }
    }

    private void createRepo(boolean privateRepo) {
        TextInputEditText input = new TextInputEditText(requireContext());
        input.setSingleLine(true);
        input.setText(privateRepo ? "webhtv-backup" : "webhtv-public");
        new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(privateRepo ? "创建私有备份库" : "创建公开资源库")
                .setView(input)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    String name = value(input);
                    if (TextUtils.isEmpty(name)) return;
                    run("创建仓库中", () -> {
                        GitRepo created = provider().createRepo(account, token(), new CreateRepoRequest(name, "WebHTV Git 云盘", privateRepo));
                        App.post(() -> {
                            repo = created;
                            repos.add(0, created);
                            fileTree.clear();
                            expandedPaths.clear();
                            expandedPaths.add("");
                            render();
                            browse(created, "");
                        });
                    });
                })
                .show();
    }

    private void showUploadText() {
        LinearLayoutCompat root = new LinearLayoutCompat(requireContext());
        root.setOrientation(LinearLayoutCompat.VERTICAL);
        TextInput editPath = input("远端路径", false);
        editPath.edit.setText(joinRemote(currentPath, "new-file.txt"));
        TextInput content = input("内容", false);
        content.edit.setMinLines(6);
        content.edit.setGravity(Gravity.TOP | Gravity.START);
        root.addView(editPath.layout);
        root.addView(content.layout);
        new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle("新建文件")
                .setView(root)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> uploadText(value(editPath.edit), value(content.edit)))
                .show();
    }

    private void uploadText(String path, String content) {
        if (TextUtils.isEmpty(path)) return;
        uploadChanges(List.of(new FileChange(path, content.getBytes(StandardCharsets.UTF_8))), "新建文件中");
    }

    private void chooseUploadFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        filePicker.launch(Intent.createChooser(intent, "选择文件"));
    }

    private void chooseUploadFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        folderPicker.launch(intent);
    }

    private void handleFileUri(Uri uri) {
        if (uri == null || repo == null) return;
        run("读取文件中", () -> {
            String name = displayName(uri, "upload.bin");
            byte[] data = readBytes(uri);
            uploadChangesSync(List.of(new FileChange(joinRemote(currentPath, name), data)));
            App.post(() -> reloadAfterWrite(currentPath));
        });
    }

    private void handleTreeUri(Uri uri) {
        if (uri == null || repo == null) return;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {
        }
        run("读取目录中", () -> {
            List<FileChange> changes = new ArrayList<>();
            String rootName = documentName(uri, "folder");
            collectTree(uri, DocumentsContract.getTreeDocumentId(uri), rootName, changes);
            if (changes.isEmpty()) throw new IllegalStateException("目录中没有可上传文件");
            uploadChangesSync(changes);
            App.post(() -> reloadAfterWrite(currentPath));
        });
    }

    private void uploadChanges(List<FileChange> changes, String status) {
        if (changes == null || changes.isEmpty()) return;
        run(status, () -> {
            uploadChangesSync(changes);
            App.post(() -> reloadAfterWrite(currentPath));
        });
    }

    private void uploadChangesSync(List<FileChange> changes) throws Exception {
        ProviderCapabilities capabilities = provider().capabilities();
        if (capabilities.contentsWrite) {
            for (FileChange change : changes) {
                SaveOptions options = new SaveOptions();
                options.message = "upload: " + change.path;
                provider().saveSmallFile(account, token(), repo, repo.defaultBranch, change.path, change.data, options);
            }
        } else {
            driveEngine.commitAndPush(driveConfig(), changes);
        }
    }

    private void reloadAfterWrite(String path) {
        String target = path == null ? "" : path;
        invalidateTree(target);
        browse(repo, target);
    }

    private void invalidateTree(String path) {
        String target = path == null ? "" : path;
        fileTree.remove(target);
        String prefix = TextUtils.isEmpty(target) ? "" : target + "/";
        List<String> remove = new ArrayList<>();
        for (String key : fileTree.keySet()) {
            if (TextUtils.isEmpty(prefix) || key.startsWith(prefix)) remove.add(key);
        }
        for (String key : remove) fileTree.remove(key);
    }

    private void backup() {
        if (repo == null) return;
        run("生成备份中", () -> {
            SyncFiles.Archive archive = SyncFiles.createArchive(SyncFiles.getPaths(SyncFiles.DEFAULT_PATHS));
            if (archive == null) throw new IllegalStateException("没有可备份文件");
            String stamp = Formatters.LOCAL_DATETIME.format(Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault())).replace(":", "").replace(" ", "-");
            String zip = GitCloudPaths.backupDir() + "/webhtv-backup-" + stamp + ".zip";
            String manifest = GitCloudPaths.backupDir() + "/webhtv-backup-" + stamp + ".json";
            CommitResult result = driveEngine.commitAndPush(driveConfig(), List.of(
                    new FileChange(zip, Path.readToByte(archive.getFile())),
                    new FileChange(manifest, manifest(zip, archive).getBytes(StandardCharsets.UTF_8))));
            archive.delete();
            App.post(() -> {
                Notify.show(result.pushed ? "备份已上传" : result.message);
                invalidateTree(GitCloudPaths.backupDir());
                browse(repo, GitCloudPaths.backupDir());
            });
        });
    }

    private String manifest(String archive, SyncFiles.Archive data) {
        JsonObject object = new JsonObject();
        object.addProperty("app", "WebHTV");
        object.addProperty("version", 1);
        object.addProperty("createdAt", Instant.now().toString());
        object.addProperty("archive", archive);
        object.addProperty("fileCount", data.getCount());
        object.addProperty("rawSize", data.getRawSize());
        object.addProperty("zipSize", data.getZipSize());
        JsonArray items = new JsonArray();
        for (String path : data.getPaths()) items.add(path);
        object.add("items", items);
        return App.gson().toJson(object);
    }

    private void clearCache() {
        if (account == null || repo == null) return;
        run("清理缓存中", () -> {
            Path.clear(GitCloudPaths.worktree(account, repo));
            App.post(() -> Notify.show("本地缓存已清理"));
        });
    }

    private GitDriveConfig driveConfig() throws Exception {
        GitDriveConfig config = new GitDriveConfig();
        config.account = account;
        config.repo = repo;
        config.token = token();
        config.branch = repo.defaultBranch;
        config.worktreeDir = GitCloudPaths.worktree(account, repo);
        config.defaultRemotePath = GitCloudPaths.backupDir();
        return config;
    }

    private void editAccount() {
        editingAccount = true;
        populateAccountForm(account);
        render();
    }

    private void removeAccount() {
        if (account == null) return;
        GitCloudTokenStore.remove(account.tokenKey);
        GitCloudAccountStore.remove(account);
        account = null;
        repo = null;
        currentPath = "";
        repos.clear();
        fileTree.clear();
        expandedPaths.clear();
        reposAccountId = null;
        editingAccount = true;
        populateAccountForm(null);
        render();
    }

    private void changeRepo() {
        repo = null;
        currentPath = "";
        fileTree.clear();
        expandedPaths.clear();
        render();
        if (account != null && !TextUtils.equals(reposAccountId, account.id)) refreshRepos();
        else showRepos(new ArrayList<>(repos));
    }

    private void reloadTree() {
        if (repo == null) return;
        fileTree.remove(currentPath == null ? "" : currentPath);
        browse(repo, currentPath);
    }

    private void populateAccountForm(GitAccount source) {
        if (binding == null || binding.alias == null) return;
        binding.alias.edit.setText(source == null ? "" : source.remark);
        binding.baseUrl.edit.setText(source == null ? defaultBaseUrl(providerType) : source.baseUrl);
        binding.token.edit.setText("");
    }

    private String label(GitProviderType type) {
        return type == GitProviderType.CNB ? "CNB" : "GitHub";
    }

    private String defaultBaseUrl(GitProviderType type) {
        return type == GitProviderType.CNB ? "https://cnb.cool" : "";
    }

    private String meta(GitAccount value) {
        if (value == null) return "";
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(value.username)) builder.append(value.username);
        if (!TextUtils.isEmpty(value.normalizedBaseUrl())) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append(value.normalizedBaseUrl());
        }
        if (value.lastValidatedAt > 0) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append("已校验");
        }
        return builder.toString();
    }

    private String statusText() {
        if (busy) return binding.status == null ? "" : binding.status.getText().toString();
        if (account == null || editingAccount) return label(providerType) + " · 未连接";
        if (repo == null) return account.displayName() + " · 请选择仓库";
        return account.displayName() + " · " + repo.displayName();
    }

    private String pathLabel() {
        String branch = TextUtils.isEmpty(repo.defaultBranch) ? "main" : repo.defaultBranch;
        return branch + " · /" + (TextUtils.isEmpty(currentPath) ? "" : currentPath);
    }

    private void run(String status, CheckedRunnable runnable) {
        if (busy) return;
        busy = true;
        setStatus(status);
        render();
        Task.execute(() -> {
            try {
                runnable.run();
                App.post(() -> setStatus("完成"));
            } catch (Throwable e) {
                App.post(() -> {
                    setStatus(e.getMessage());
                    Notify.show(e.getMessage());
                });
            } finally {
                App.post(() -> {
                    busy = false;
                    render();
                });
            }
        });
    }

    private GitCloudProvider provider() {
        return GitCloudProviders.get(providerType);
    }

    private String token() throws Exception {
        return account == null ? "" : GitCloudTokenStore.get(account.tokenKey);
    }

    private void setStatus(String value) {
        if (binding != null && binding.status != null) binding.status.setText(value == null ? "" : value);
    }

    private TextInput input(String hint, boolean password) {
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setHint(hint);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxCornerRadii(dp(8), dp(8), dp(8), dp(8));
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        layout.setLayoutParams(params);
        TextInputEditText edit = new TextInputEditText(layout.getContext());
        edit.setSingleLine(!"内容".contentEquals(hint));
        edit.setTextSize(14);
        if (password) edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(edit, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new TextInput(layout, edit);
    }

    private View section(String title) {
        MaterialTextView view = text(title, 12, Color.parseColor("#5F6368"), true);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(14);
        params.bottomMargin = dp(6);
        view.setLayoutParams(params);
        return view;
    }

    private LinearLayoutCompat list() {
        LinearLayoutCompat view = new LinearLayoutCompat(requireContext());
        view.setOrientation(LinearLayoutCompat.VERTICAL);
        return view;
    }

    private LinearLayoutCompat row() {
        LinearLayoutCompat view = new LinearLayoutCompat(requireContext());
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setOrientation(LinearLayoutCompat.HORIZONTAL);
        return view;
    }

    private LinearLayoutCompat card() {
        LinearLayoutCompat view = new LinearLayoutCompat(requireContext());
        view.setOrientation(LinearLayoutCompat.VERTICAL);
        view.setPadding(dp(10), dp(9), dp(10), dp(9));
        view.setBackground(round(Color.parseColor("#F8F9FA"), 8, Color.parseColor("#DADCE0")));
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        view.setLayoutParams(params);
        return view;
    }

    private MaterialTextView text(String value, int sp, int color, boolean bold) {
        MaterialTextView view = new MaterialTextView(requireContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setSingleLine(false);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private MaterialTextView detail(String value) {
        MaterialTextView view = text(value, 12, Color.parseColor("#5F6368"), false);
        view.setPadding(0, dp(4), 0, dp(8));
        return view;
    }

    private MaterialTextView empty(String value) {
        MaterialTextView view = text(value, 13, Color.parseColor("#5F6368"), false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(14), 0, dp(14));
        return view;
    }

    private MaterialTextView pill(String value) {
        MaterialTextView view = text(value, 11, Color.parseColor("#1967D2"), true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), dp(3), dp(8), dp(3));
        view.setBackground(round(Color.parseColor("#E8F0FE"), 16, Color.TRANSPARENT));
        return view;
    }

    private MaterialButton primary(String text) {
        MaterialButton button = baseButton(text);
        button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.dialog_primary_button_bg));
        button.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.dialog_primary_button_text));
        return button;
    }

    private MaterialButton tonal(String text) {
        MaterialButton button = baseButton(text);
        button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.dialog_tonal_button_bg));
        button.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.dialog_tonal_button_text));
        return button;
    }

    private MaterialButton outline(String text) {
        MaterialButton button = baseButton(text);
        button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_bg));
        button.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_text));
        button.setStrokeColor(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_stroke));
        button.setStrokeWidth(dp(1));
        return button;
    }

    private MaterialButton compact(String text) {
        MaterialButton button = tonal(text);
        button.setTextSize(12);
        return button;
    }

    private MaterialButton treeToggle(String text) {
        MaterialButton button = outline(text);
        button.setTextSize(16);
        button.setMinWidth(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private MaterialButton segment(String text) {
        MaterialButton button = outline(text);
        button.setCheckable(true);
        button.setBackgroundTintList(segmentBackground());
        button.setTextColor(segmentText());
        button.setStrokeColor(segmentStroke());
        return button;
    }

    private ColorStateList segmentBackground() {
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

    private ColorStateList segmentText() {
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

    private ColorStateList segmentStroke() {
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

    private MaterialButton iconButton(String text) {
        MaterialButton button = baseButton(text);
        button.setTextSize(20);
        button.setMinWidth(0);
        button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_bg));
        button.setTextColor(Color.parseColor("#5F6368"));
        return button;
    }

    private MaterialButton baseButton(String text) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(dp(36));
        button.setMinimumHeight(dp(36));
        button.setPadding(dp(6), 0, dp(6), 0);
        return button;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable e) {
            Notify.show(R.string.manage_page_no_browser);
        }
    }

    private void copy(String value) {
        ClipboardManager manager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null || TextUtils.isEmpty(value)) return;
        manager.setPrimaryClip(ClipData.newPlainText("Git raw", value));
        Notify.show("已复制");
    }

    private String tokenUrl() {
        return providerType == GitProviderType.CNB ? "https://cnb.cool" : "https://github.com/settings/personal-access-tokens";
    }

    private String helpUrl() {
        return providerType == GitProviderType.CNB ? "https://docs.cnb.cool/zh/guide/git-access.html" : "https://docs.github.com/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens";
    }

    private String value(TextInputEditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    private byte[] readBytes(Uri uri) throws Exception {
        try (InputStream input = requireContext().getContentResolver().openInputStream(uri); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) return new byte[0];
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toByteArray();
        }
    }

    private String displayName(Uri uri, String fallback) {
        String[] projection = {OpenableColumns.DISPLAY_NAME};
        try (Cursor cursor = requireContext().getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String value = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
                if (!TextUtils.isEmpty(value)) return cleanName(value);
            }
        } catch (Throwable ignored) {
        }
        String last = uri == null ? "" : uri.getLastPathSegment();
        return cleanName(TextUtils.isEmpty(last) ? fallback : last);
    }

    private String documentName(Uri treeUri, String fallback) {
        try {
            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
            return queryDocumentName(docUri, fallback);
        } catch (Throwable e) {
            return fallback;
        }
    }

    private String queryDocumentName(Uri docUri, String fallback) {
        String[] projection = {DocumentsContract.Document.COLUMN_DISPLAY_NAME};
        try (Cursor cursor = requireContext().getContentResolver().query(docUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String value = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                if (!TextUtils.isEmpty(value)) return cleanName(value);
            }
        } catch (Throwable ignored) {
        }
        return cleanName(fallback);
    }

    private void collectTree(Uri treeUri, String documentId, String relative, List<FileChange> changes) throws Exception {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = requireContext().getContentResolver().query(children, projection, null, null, null)) {
            if (cursor == null) return;
            int idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext()) {
                String id = cursor.getString(idColumn);
                String name = cleanName(cursor.getString(nameColumn));
                String mime = cursor.getString(mimeColumn);
                String childRelative = joinRemote(relative, name);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    collectTree(treeUri, id, childRelative, changes);
                } else {
                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                    changes.add(new FileChange(joinRemote(currentPath, childRelative), readBytes(docUri)));
                }
            }
        }
    }

    private String joinRemote(String parent, String child) {
        String left = parent == null ? "" : parent.replaceAll("^/+", "").replaceAll("/+$", "");
        String right = child == null ? "" : child.replaceAll("^/+", "");
        if (TextUtils.isEmpty(left)) return right;
        if (TextUtils.isEmpty(right)) return left;
        return left + "/" + right;
    }

    private String cleanName(String value) {
        String name = value == null ? "" : value.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        return TextUtils.isEmpty(name) ? "file" : name;
    }

    private String parent(String path) {
        if (TextUtils.isEmpty(path) || !path.contains("/")) return "";
        return path.substring(0, path.lastIndexOf('/'));
    }

    private String size(long bytes) {
        if (bytes <= 0) return "0 B";
        return com.fongmi.android.tv.utils.FileUtil.byteCountToDisplaySize(bytes);
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }

    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private static class TextInput {
        final TextInputLayout layout;
        final TextInputEditText edit;

        TextInput(TextInputLayout layout, TextInputEditText edit) {
            this.layout = layout;
            this.edit = edit;
        }
    }

    private static class DialogBinding implements ViewBinding {
        final View root;
        NestedScrollView scroll;
        MaterialTextView status;
        MaterialButton close;
        MaterialButton github;
        MaterialButton cnb;
        MaterialButton tokenLink;
        MaterialButton helpLink;
        MaterialButton save;
        MaterialButton refresh;
        MaterialButton editAccount;
        MaterialButton removeAccount;
        MaterialButton changeRepo;
        MaterialButton refreshTree;
        MaterialButton createPrivate;
        MaterialButton createPublic;
        MaterialButton uploadText;
        MaterialButton uploadFile;
        MaterialButton uploadFolder;
        MaterialButton backup;
        MaterialButton clearCache;
        MaterialTextView accountName;
        MaterialTextView accountBadge;
        MaterialTextView accountMeta;
        MaterialTextView repoTitle;
        MaterialTextView repoBadge;
        MaterialTextView pathText;
        TextInput alias;
        TextInput baseUrl;
        TextInput token;
        LinearLayoutCompat accountCard;
        LinearLayoutCompat loginForm;
        LinearLayoutCompat repoPanel;
        LinearLayoutCompat createPanel;
        LinearLayoutCompat filePanel;
        LinearLayoutCompat repoList;
        LinearLayoutCompat fileList;

        DialogBinding(View root) {
            this.root = root;
        }

        @NonNull
        @Override
        public View getRoot() {
            return root;
        }
    }
}
