# CodeMC GitHub Commit Status Plugin

This Jenkins plugin allows you to update the GitHub commit status with project-specific credentials (Personal Access Source or GitHub App) and custom messages/URLs.

It provides two main functionalities:
1.  **CodeMC Set GitHub Commit Status to PENDING**: A build step to set the status to PENDING at the start of your build (or wherever placed).
2.  **CodeMC GitHub Commit Status**: A post-build publisher that automatically sets the status (SUCCESS, FAILURE, ERROR) based on the build result.

## Features

-   **Project-specific Credentials**: Use different GitHub credentials for different jobs/folders.
-   **GitHub App Support**: Authenticate using GitHub Apps.
-   **Custom Messages & URLs**: Configure the status context, description, and target URL.
-   **Inferred Repository**: Automatically detects the repository name from the SCM configuration if not specified.

## Usage

### As a Build Step (Pending Status)

Add the "CodeMC Set GitHub Commit Status to PENDING" build step.
-   **Credentials**: Select your GitHub credentials (Secret Text or GitHub App).
-   **Context**: The status context (e.g., `jenkins`).
-   **Repository**: (Optional) `owner/repo`. Defaults to detecting from Git SCM.
-   **Status Message**: (Optional) Custom message (defaults to "Build started...").
-   **Target URL**: (Optional) Custom URL (defaults to build URL).

### As a Notifier (Final Status)

Add the "CodeMC GitHub Commit Status" post-build action.
-   **Credentials**: Select your GitHub credentials.
-   **Context**: The status context (must match the one used in the pending step if you want to update the same status).
-   **Repository**: (Optional) `owner/repo`.
-   **Custom Status Message**: (Optional).
-   **Custom Target URL**: (Optional).

### Pipeline Configuration

You can use the following steps in your Jenkins Pipeline:

**Set Pending Status:**
```groovy
gitHubCommitPendingBuilder(
    credentialId: 'my-github-creds',
    context: 'jenkins',
    statusMessage: 'Build started...',
    repository: 'owner/repo' // Optional, auto-detected if empty
)
```

**Set Final Status (Post Build):**
```groovy
gitHubCommitStatusNotifier(
    credentialId: 'my-github-creds',
    context: 'jenkins',
    statusMessage: 'Build finished correctly', // Optional
    statusUrl: 'https://custom-url.com', // Optional
    repository: 'owner/repo' // Optional
)
```

## Inspiration

This plugin is heavily inspired by the official [Jenkins GitHub Plugin](https://plugins.jenkins.io/github/).
We created this custom version to handle specific requirements around credential isolation and custom status updates that were difficult to achieve with the standard plugin.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
