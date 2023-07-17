Preliminary release of the github dependency graph plugin.

- Verbosity of log messages reduced
- A custom task is now used for resolving all project dependencies, instead of calling the "dependencies" task
- The Gradle Configurations that contribute to the dependency graph can be filtered by project or configuration name
- Environment variables used to configure plugin have been renamed for consistency and conciseness
- System properties can be used to override all configuration environment variables
