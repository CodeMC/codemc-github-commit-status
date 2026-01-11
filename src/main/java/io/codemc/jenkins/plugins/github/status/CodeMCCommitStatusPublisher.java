package io.codemc.jenkins.plugins.github.status;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import edu.umd.cs.findbugs.annotations.NonNull;

@SuppressWarnings("unused")
public class CodeMCCommitStatusPublisher extends Recorder implements SimpleBuildStep {

    private final String credentialId;
    private final String repository;
    private final String context;

    private String statusUrl;
    private String statusMessage;
    private boolean failOnError;

    @DataBoundConstructor
    public CodeMCCommitStatusPublisher(String credentialId, String repository, String context) {
        this.credentialId = credentialId;
        this.repository = repository;
        this.context = context;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public String getRepository() {
        return repository;
    }

    public String getContext() {
        return context;
    }

    @DataBoundSetter
    public void setStatusUrl(String statusUrl) {
        this.statusUrl = statusUrl;
    }

    public String getStatusUrl() {
        return statusUrl;
    }

    @DataBoundSetter
    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    @DataBoundSetter
    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    @Override
    public void perform(@NonNull Run<?, ?> run, @NonNull FilePath workspace, @NonNull EnvVars env, @NonNull Launcher launcher, @NonNull TaskListener listener) {
        try {
            GHCommitState state = resolveCommitState(run.getResult());

            // If default message is needed (null), we pass null, helper handles it. 
            // BUT helper's default message is generic. Publisher usually wants "Build #1: SUCCESS". 
            // Helper says: "Build " + run.getDisplayName() + ": " + state.toString();
            // That matches our previous logic roughly.

            CommitStatusCommon.updateCommitStatus(run, listener, env, credentialId, repository, context, statusUrl, statusMessage, state);
        } catch (Exception e) {
            listener.error("[CodeMC] Error updating commit status: " + e.getMessage());
            e.printStackTrace(listener.getLogger());
            if (failOnError) {
                run.setResult(Result.FAILURE);
            }
        }
    }

    private GHCommitState resolveCommitState(Result result) {
        if (result == null) {
            return GHCommitState.PENDING;
        }
        if (result == Result.SUCCESS) {
            return GHCommitState.SUCCESS;
        }
        if (result == Result.FAILURE) {
            return GHCommitState.FAILURE;
        }
        if (result == Result.UNSTABLE) {
            return GHCommitState.FAILURE;
        }
        if (result == Result.ABORTED) {
            return GHCommitState.ERROR;
        }
        return GHCommitState.FAILURE;
    }

    @Symbol("codeMCGitHubCommitStatus")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "CodeMC GitHub Commit Status";
        }

        public ListBoxModel doFillCredentialIdItems(@AncestorInPath Item item, @QueryParameter String credentialId) {
            return CommitStatusCommon.doFillCredentialIdItems(item, credentialId);
        }

        public FormValidation doCheckRepository(@QueryParameter String value) {
            return CommitStatusCommon.doCheckRepository(value);
        }

        public FormValidation doCheckStatusUrl(@QueryParameter String value) {
            return CommitStatusCommon.doCheckStatusUrl(value);
        }

        public FormValidation doCheckContext(@QueryParameter String value) {
            return CommitStatusCommon.doCheckContext(value);
        }
    }
}
