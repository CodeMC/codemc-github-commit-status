package io.codemc.jenkins.plugins.github.status;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;

import hudson.EnvVars;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.util.BuildData;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import org.jenkinsci.plugins.github_branch_source.GitHubAppCredentials;
import org.jenkinsci.plugins.github_branch_source.Connector;
import hudson.util.FormValidation;
import org.jenkinsci.plugins.github_branch_source.app_credentials.AccessSpecifiedRepositories;

import java.util.Collections;
import java.util.List;
import java.io.IOException;

import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.util.logging.Logger;
import java.util.logging.Level;

public class CommitStatusCommon {

    private static final Logger LOGGER = Logger.getLogger(CommitStatusCommon.class.getName());

    public static GitHub createGitHubClient(Run<?, ?> run, String credentialId, String repository) throws IOException {
        List<StandardCredentials> foundCredentials = CredentialsProvider.lookupCredentialsInItem(StandardCredentials.class, run.getParent(), ACL.SYSTEM2, Collections.emptyList());
        StandardCredentials credentials;

        if (credentialId != null && !credentialId.trim().isEmpty()) {
            credentials = CredentialsMatchers.firstOrNull(foundCredentials, CredentialsMatchers.withId(credentialId));
            if (credentials == null) {
                LOGGER.warning("Credential not found for ID: " + credentialId);
                throw new IOException("Credential not found: " + credentialId);
            }
        } else {
            throw new IOException("No credential ID provided!");
        }

        if (credentials instanceof StringCredentials) {
            return new GitHubBuilder().withOAuthToken(((StringCredentials) credentials).getSecret().getPlainText()).build();
        } else if (credentials instanceof GitHubAppCredentials appCredentials) {
            try {
                String owner = repository.split("/")[0];
                // Clone the credential to set the owner
                appCredentials = restrictGitHubCredentialsToOwner(appCredentials, owner);
                return Connector.connect("https://api.github.com", appCredentials);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to authenticate as GitHub App for repository " + repository, e);
                throw new IOException("Failed to authenticate as GitHub App for repository " + repository, e);
            }
        }

        throw new IOException("Unsupported credential type: " + credentials.getClass().getName());
    }

    private static GitHubAppCredentials restrictGitHubCredentialsToOwner(GitHubAppCredentials appCredentials, String owner) {
        GitHubAppCredentials scoped = new GitHubAppCredentials(
                appCredentials.getScope(),
                appCredentials.getId() + "-" + owner,
                appCredentials.getDescription(),
                appCredentials.getAppID(),
                appCredentials.getPrivateKey()
        );
        scoped.setRepositoryAccessStrategy(new AccessSpecifiedRepositories(owner, Collections.emptyList()));
        scoped.setApiUri(appCredentials.getApiUri());
        return scoped;
    }

    public static String resolveHeadCommit(Run<?, ?> run, EnvVars env) {
        String sha = env.get("GIT_COMMIT");
        if (sha != null && !sha.isEmpty()) {
            return sha;
        }

        try {
            BuildData action = run.getAction(BuildData.class);
            if (action != null && action.getLastBuiltRevision() != null) {
                return action.getLastBuiltRevision().getSha1String();
            }
        } catch (NoClassDefFoundError | Exception e) {
            LOGGER.log(Level.FINE, "Failed to resolve head commit from BuildData: " + e.getMessage(), e);
        }

        LOGGER.warning("Could not resolve HEAD commit SHA.");
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

        LOGGER.log(Level.INFO, "Updating commit status for repo: {0}", targetRepo);

        String sha = resolveHeadCommit(run, env);
        if (sha == null || sha.isEmpty()) {
            throw new IOException("Could not determine GIT_COMMIT from environment or SCM configuration.");
        }

        GitHub github = createGitHubClient(run, credentialId, targetRepo);
        GHRepository ghRepo = github.getRepository(targetRepo);

        String description = (statusMessage != null && !statusMessage.isEmpty()) ? env.expand(statusMessage) : "Build " + run.getDisplayName() + ": " + state.toString();

        String targetUrl = (statusUrl != null && !statusUrl.isEmpty()) ? env.expand(statusUrl) : env.get("BUILD_URL");

        String statusContext = (context != null && !context.isEmpty()) ? env.expand(context) : "jenkins";

        ghRepo.createCommitStatus(sha, state, targetUrl, description, statusContext);
        listener.getLogger().println("[CodeMC GitHub Commit Status] Updated GitHub commit status for " + targetRepo + "@" + sha + " to " + state);
    }

    // Form Validations
    public static ListBoxModel doFillCredentialIdItems(Item item, String credentialId) {
        StandardListBoxModel result = new StandardListBoxModel();
        if (item == null) {
            return result;
        }

        // Filter for specific types
        CredentialsProvider.lookupCredentialsInItem(
                GitHubAppCredentials.class,
                item,
                ACL.SYSTEM2,
                Collections.emptyList()
        ).forEach(result::with);
        CredentialsProvider.lookupCredentialsInItem(
                StringCredentials.class,
                item,
                ACL.SYSTEM2,
                Collections.emptyList()
        ).forEach(result::with);

        // Ensure current value is present
        if (credentialId != null && !credentialId.trim().isEmpty()) {
            result.includeCurrentValue(credentialId);
        }

        return result;
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
