Preliminary release of the github dependency graph plugin.

- Renamed most input parameters for consistency, and avoiding direct reliance of GitHub-defined variables.
- Reduced spurious logging when using `DEPENDENCY_GRAPH_INCLUDE_CONFIGURATIONS`.

Note that all users of this plugin will need to be updated to accomodate the renamed input parameters.
