package com.fongmi.android.tv.gitcloud.provider;

import com.fongmi.android.tv.gitcloud.AccountInfo;
import com.fongmi.android.tv.gitcloud.CreateRepoRequest;
import com.fongmi.android.tv.gitcloud.DownloadRef;
import com.fongmi.android.tv.gitcloud.GitAccount;
import com.fongmi.android.tv.gitcloud.GitBranch;
import com.fongmi.android.tv.gitcloud.GitCloudException;
import com.fongmi.android.tv.gitcloud.GitFile;
import com.fongmi.android.tv.gitcloud.GitFileContent;
import com.fongmi.android.tv.gitcloud.GitProviderType;
import com.fongmi.android.tv.gitcloud.GitRepo;
import com.fongmi.android.tv.gitcloud.ProviderCapabilities;
import com.fongmi.android.tv.gitcloud.SaveOptions;
import com.fongmi.android.tv.gitcloud.SaveResult;

import java.util.List;

public interface GitCloudProvider {

    GitProviderType type();

    ProviderCapabilities capabilities();

    AccountInfo validateToken(GitAccount account, String token) throws GitCloudException;

    List<GitRepo> listRepos(GitAccount account, String token) throws GitCloudException;

    GitRepo createRepo(GitAccount account, String token, CreateRepoRequest request) throws GitCloudException;

    List<GitBranch> listBranches(GitAccount account, String token, GitRepo repo) throws GitCloudException;

    List<GitFile> listFiles(GitAccount account, String token, GitRepo repo, String ref, String path) throws GitCloudException;

    GitFileContent readFile(GitAccount account, String token, GitRepo repo, String ref, String path) throws GitCloudException;

    SaveResult saveSmallFile(GitAccount account, String token, GitRepo repo, String branch, String path, byte[] data, SaveOptions options) throws GitCloudException;

    String rawUrl(GitAccount account, GitRepo repo, String ref, String path);

    DownloadRef archiveUrl(GitAccount account, String token, GitRepo repo, String ref, String path);
}
