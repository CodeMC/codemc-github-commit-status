package io.codemc.jenkins.plugins.github.status;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;

import hudson.EnvVars;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import org.jenkinsci.plugins.github_branch_source.GitHubAppCredentials;
import org.jenkinsci.plugins.github_branch_source.Connector;
import hudson.util.FormValidation;

import java.util.Collections;
import java.util.List;
import java.io.IOException;

import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

public class CommitStatusCommon {

    public static GitHub createGitHubClient(Run<?, ?> run, String credentialId, String repository) throws IOException {
        if (credentialId == null) {
            throw new IOException("No credential ID provided");
        }

        List<StandardCredentials> foundCredentials = CredentialsProvider.lookupCredentialsInItem(StandardCredentials.class, run.getParent(), ACL.SYSTEM2, Collections.emptyList());
        StandardCredentials credentials = CredentialsMatchers.firstOrNull(foundCredentials, CredentialsMatchers.withId(credentialId));

        if (credentials == null) {
            throw new IOException("Credential not found: " + credentialId);
        }

        if (credentials instanceof StringCredentials) {
            return new GitHubBuilder().withOAuthToken(((StringCredentials) credentials).getSecret().getPlainText()).build();
        } else if (credentials instanceof GitHubAppCredentials appCredentials) {
            GitHub appClient = Connector.connect("https://api.github.com", appCredentials);
            try {
                GHApp app = appClient.getApp();
                String owner = repository.split("/")[0];
                GHAppInstallation installation = app.getInstallationByRepository(owner, repository.split("/")[1]);
                GHAppInstallationToken token = installation.createToken().create();
                return new GitHubBuilder().withAppInstallationToken(token.getToken()).build();
            } catch (Exception e) {
                throw new IOException("Failed to authenticate as GitHub App for repository " + repository, e);
            }
        }

        throw new IOException("Unsupported credential type: " + credentials.getClass().getName());
    }

    public static String resolveHeadCommit(Run<?, ?> run, EnvVars env) {
        String sha = env.get("GIT_COMMIT");
        if (sha != null && !sha.isEmpty()) {
            return sha;
        }

        try {
            hudson.plugins.git.util.BuildData action = run.getAction(hudson.plugins.git.util.BuildData.class);
            if (action != null && action.getLastBuiltRevision() != null) {
                return action.getLastBuiltRevision().getSha1String();
            }
        } catch (NoClassDefFoundError | Exception e) {
            // Ignore
        }

        return null;
    }

    public static String parseRepoFromUrl(String url) {
        if (url == null) {
            return null;
        }

        url = url.trim();
        if (url.endsWith(".git")) {
            url = url.substring(0, url.length() - 4);
        }
        if (url.startsWith("https://github.com/")) {
            return url.substring("https://github.com/".length());
        }
        if (url.startsWith("git@github.com:")) {
            return url.substring("git@github.com:".length());
        }
        return url;
    }

    public static void updateCommitStatus(Run<?, ?> run, TaskListener listener, EnvVars env, String credentialId, String repository, String context, String statusUrl, String statusMessage, GHCommitState state) throws IOException {
        String targetRepo = repository;
        if (targetRepo == null || targetRepo.trim().isEmpty()) {
            String gitUrl = env.get("GIT_URL");
            if (gitUrl != null) {
                targetRepo = parseRepoFromUrl(gitUrl);
            }
        }

        if (targetRepo == null || targetRepo.trim().isEmpty()) {
            throw new IOException("Could not determine repository name. Please specify it in the configuration.");
        }

        String sha = resolveHeadCommit(run, env);
        if (sha == null || sha.isEmpty()) {
            throw new IOException("Could not determine GIT_COMMIT from environment or SCM configuration.");
        }

        GitHub github = createGitHubClient(run, credentialId, targetRepo);
        GHRepository ghRepo = github.getRepository(targetRepo);

        String description = (statusMessage != null && !statusMessage.isEmpty()) ? env.expand(statusMessage) : "Build " + run.getDisplayName() + ": " + state.toString();

        String targetUrl = (statusUrl != null && !statusUrl.isEmpty()) ? env.expand(statusUrl) : env.get("BUILD_URL");

        String statusContext = (context != null && !context.isEmpty()) ? env.expand(context) : "jenkins/codemc";

        ghRepo.createCommitStatus(sha, state, targetUrl, description, statusContext);
        listener.getLogger().println("[CodeMC GitHub Commit Status] Updated GitHub commit status for " + targetRepo + "@" + sha + " to " + state);
    }

    // Form Validations
    public static ListBoxModel doFillCredentialIdItems(Item item, String credentialId) {
        StandardListBoxModel result = new StandardListBoxModel();
        if (item == null) {
            if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                return result.includeCurrentValue(credentialId);
            }
        } else {
            if (!item.hasPermission(Item.CONFIGURE) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                return result.includeCurrentValue(credentialId);
            }
        }
        return result.includeEmptyValue().includeMatchingAs(ACL.SYSTEM2, item, StandardCredentials.class, Collections.emptyList(), CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StringCredentials.class), CredentialsMatchers.instanceOf(GitHubAppCredentials.class))).includeCurrentValue(credentialId);
    }

    public static FormValidation doCheckRepository(String value) {
        if (value == null || value.trim().isEmpty()) {
            return FormValidation.warning("If empty, the plugin will try to infer the repository from the SCM configuration.");
        }
        if (!value.contains("/")) {
            return FormValidation.error("Repository name should be in the format 'owner/repo'.");
        }
        return FormValidation.ok();
    }

    public static FormValidation doCheckStatusUrl(String value) {
        if (value != null && !value.isEmpty() && !value.startsWith("http") && !value.contains("$")) {
            return FormValidation.warning("The URL should likely start with http:// or https:// unless you are using variables.");
        }
        return FormValidation.ok();
    }

    public static FormValidation doCheckContext(String value) {
        if (value == null || value.trim().isEmpty()) {
            return FormValidation.error("Status context cannot be empty.");
        }
        return FormValidation.ok();
    }
}
