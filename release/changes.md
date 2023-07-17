Preliminary release of the github dependency graph plugin.

- Reduce verbosity of log messages
- Use a custom task for resolving all project dependencies, instead of calling the "dependencies" task
- Which Gradle Configurations contribute to the dependency graph can be filtered by project or configuration name
- Renamed environment variables used to configure plugin for consistency and conciseness
- Allow system properties to override all configuration environment variables
