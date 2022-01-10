# GitHub Dependency Extractor

A Gradle plugin for extracting dependencies from a Gradle build to feed the GitHub Dependency API.

This project is currently just a proof of concept, but ideally, this plugin would be automatically applied to any Gradle
project using the GitHub Action [gradle-build-action](https://github.com/marketplace/actions/gradle-build-action).

This plugin leverages the same internal API's used by the Gradle Build Scan Plugin.

This plugin is intended to implement the proposed API for the GitHub Build-Time Dependency Graph API found 
[here](https://docs.google.com/document/d/1TjxJJwgPavw-TFzK3110iH-CWstgdcVdb2JYiRy2GVs/edit?usp=sharing)
and this
[JSON Schema](https://gist.github.com/reiddraper/7b47d553382fd3867cc1d0b5474bd6c7).

## Building/Testing

To test this plugin, run the following task:
```shell
./gradlew test
```
