# Release process for the GitHub Dependency Graph Gradle Plugin

## Preparation
- Push any outstanding changes to branch main.
- Check that https://github.com/gradle/github-dependency-graph-gradle-plugin/actions is green for all workflows for the main branch.
- Decide on the version number to use for the release. The plugin releases should follow semantic versioning.
    - By default, a patch release is assumed (eg. `1.3.0` → `1.3.1`)
    - If new features have been added, bump the minor version (eg `1.3.1` → `1.4.0`)
    - If a new major release is required, bump the major version (eg `1.3.1` → `2.0.0`)
    - Note: The plugin releases follow the semantic version convention of including a .0 patch number for the first release of a minor version, unlike the Gradle convention which omits the trailing .0.
- Update `release/version.txt` with the to-be-published version
- Ensure that `release/changes.md` contains all changes that should be included in the release notes

## Run the staging release workflow and verify the published plugin
- Publish to staging by executing the [staging release workflow](https://github.com/gradle/dv-solutions/actions/workflows/github-dependency-graph-gradle-plugin-staging-release.yml) job.
  - Run the workflow from the `main` branch and enter "STAGING" when requested.
  - Once the workflow is complete, check that it was published successfully at https://plugins.grdev.net/plugin/org.gradle.github-dependency-graph-gradle-plugin
- If necessary, run a build that resolves and tests the newly published plugin

## Run the production release workflow and verify the published plugin
- Publish to production by executing the [production release workflow](https://github.com/gradle/dv-solutions/actions/workflows/github-dependency-graph-gradle-plugin-production-release.yml) job.
    - Run the workflow from the `main` branch and enter "PRODUCTION" when requested.
- After the workflow completes, check the outputs:
    - Verify that the plugin is available at https://plugins.gradle.org/plugin/org.gradle.github-dependency-graph-gradle-plugin
    - If necessary, run a build that resolves and tests the newly published plugin
    - Check that the GitHub release was generated at https://github.com/gradle/github-dependency-graph-gradle-plugin/releases

## Update `gradle/actions` to use the newly published release
- Update the default value for `dependency-graph-plugin.version` [here](https://github.com/gradle/actions/blob/main/sources/src/resources/init-scripts/gradle-actions.github-dependency-graph-gradle-plugin-apply.groovy#L9), to reference the new release.
  - Submit a PR with this change.
