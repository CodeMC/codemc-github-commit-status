package io.codemc.jenkins.plugins.github.status;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.kohsuke.github.GHCommitState;

@SuppressWarnings("unused")
public class CodeMCCommitPendingPublisher extends Builder implements SimpleBuildStep {

    private final String credentialId;
    private final String repository;
    private final String context;

    private String statusMessage;
    private String statusUrl;

    @DataBoundConstructor
    public CodeMCCommitPendingPublisher(String credentialId, String repository, String context) {
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
    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    @DataBoundSetter
    public void setStatusUrl(String statusUrl) {
        this.statusUrl = statusUrl;
    }

    public String getStatusUrl() {
        return statusUrl;
    }

    @Override
    public void perform(@NonNull Run<?, ?> run, @NonNull FilePath workspace, @NonNull EnvVars env, @NonNull Launcher launcher, @NonNull TaskListener listener) {
        try {
            // Force status to PENDING
            String msg = (statusMessage != null && !statusMessage.isEmpty()) ? statusMessage : "Build started...";
            CommitStatusCommon.updateCommitStatus(run, listener, env, credentialId, repository, context, statusUrl, msg, GHCommitState.PENDING);
        } catch (Exception e) {
            listener.error("[CodeMC] Error setting pending status: " + e.getMessage());
            e.printStackTrace(listener.getLogger());
            // We usually don't fail the build if setting pending fails, but we could make it configurable. 
            // For now, let's just log error to not block the build startup.
        }
    }

    @Symbol("gitHubCommitPendingPublisher")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "CodeMC Set GitHub Commit Status to PENDING";
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
