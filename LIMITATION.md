# Current Limitations

These are the current limitations of this project and potential solutions.

 - Certain Script Configurations do not work when included builds are present.
   For example, see the test: `MulitProjectDependencyExtractorTest::project leveraging included builds`
   The `result.rootComponent.id` is not a `ProjectComponentIdentifier`, so the `projectPath` can not be determined.
   This could be remedied using a variation of the logic found in the Gradle Enterprise Plugin `ConfigurationResolutionCapturer_5_0`.
