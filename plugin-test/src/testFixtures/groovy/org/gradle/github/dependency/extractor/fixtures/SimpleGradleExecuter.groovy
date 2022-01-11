package org.gradle.github.dependency.extractor.fixtures

import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

class SimpleGradleExecuter {

    private final List<String> args = new ArrayList<>()
    private final List<String> tasks = new ArrayList<>()
    private final Map<String, String> environmentVars = new HashMap<>()

    private final TestDirectoryProvider testDirectoryProvider
    private final TestFile testKitDir
    private final String gradleVersion
    private boolean showStacktrace = true
    private boolean debug = false

    SimpleGradleExecuter(
            TestDirectoryProvider testDirectoryProvider,
            TestFile testKitDir,
            String gradleVersion
    ) {
        this.testDirectoryProvider = testDirectoryProvider
        this.testKitDir = testKitDir
        this.gradleVersion = gradleVersion
    }


    SimpleGradleExecuter withArguments(String... args) {
        return withArguments(Arrays.asList(args))
    }

    SimpleGradleExecuter withArguments(List<String> args) {
        this.args.clear()
        this.args.addAll(args)
        return this
    }

    SimpleGradleExecuter withArgument(String arg) {
        this.args.add(arg)
        return this
    }

    SimpleGradleExecuter withEnvironmentVars(Map<String, ?> environment) {
        environmentVars.clear()
        for (Map.Entry<String, ?> entry : environment.entrySet()) {
            environmentVars.put(entry.getKey(), entry.getValue().toString())
        }
        return this
    }

    SimpleGradleExecuter enableDebug() {
        this.debug = true
        return this
    }

    protected Map<String, String> getEnvironmentVars() {
        return new HashMap<>(environmentVars)
    }

    SimpleGradleExecuter withTasks(String... names) {
        return withTasks(Arrays.asList(names))
    }

    SimpleGradleExecuter withTasks(List<String> names) {
        tasks.clear()
        tasks.addAll(names)
        return this
    }

    protected List<String> getAllArgs() {
        List<String> allArgs = new ArrayList<>();
//        if (buildScript != null) {
//            allArgs.add("--build-file");
//            allArgs.add(buildScript.getAbsolutePath());
//        }
//        if (projectDir != null) {
//            allArgs.add("--project-dir");
//            allArgs.add(projectDir.getAbsolutePath());
//        }
//        for (File initScript : initScripts) {
//            allArgs.add("--init-script");
//            allArgs.add(initScript.getAbsolutePath());
//        }
//        if (settingsFile != null) {
//            allArgs.add("--settings-file");
//            allArgs.add(settingsFile.getAbsolutePath());
//        }
//        if (quiet) {
//            allArgs.add("--quiet");
//        }
//        if (noDaemonArgumentGiven()) {
//            if (isUseDaemon()) {
//                allArgs.add("--daemon");
//            } else {
//                allArgs.add("--no-daemon");
//            }
//        }
        if (showStacktrace) {
            allArgs.add("--stacktrace");
        }
//        if (taskList) {
//            allArgs.add("tasks");
//        }
//        if (dependencyList) {
//            allArgs.add("dependencies");
//        }
//
//        if (settingsFile == null && !ignoreMissingSettingsFile) {
//            ensureSettingsFileAvailable();
//        }
//
//        if (getGradleUserHomeDir() != null) {
//            allArgs.add("--gradle-user-home");
//            allArgs.add(getGradleUserHomeDir().getAbsolutePath());
//        }
//
//        if (consoleType != null) {
//            allArgs.add("--console=" + TextUtil.toLowerCaseLocaleSafe(consoleType.toString()));
//        }
//
//        if (warningMode != null) {
//            allArgs.add("--warning-mode=" + TextUtil.toLowerCaseLocaleSafe(warningMode.toString()));
//        }
//
//        if (disableToolchainDownload) {
//            allArgs.add("-Porg.gradle.java.installations.auto-download=false");
//        }
//        if (disableToolchainDetection) {
//            allArgs.add("-Porg.gradle.java.installations.auto-detect=false");
//        }

        allArgs.addAll(args);
        allArgs.addAll(tasks);
        return allArgs;
    }

    private GradleRunner createRunner() {
        def runner = GradleRunner.create()
        runner.withGradleVersion(gradleVersion.toString())
        runner.withTestKitDir(testKitDir)
        runner.withProjectDir(testDirectoryProvider.testDirectory)
        def args = getAllArgs()
        if (!environmentVars.isEmpty()) {
            if (debug) {
                environmentVars.forEach { key, value ->
                    args.add("-Porg.gradle.github.internal.debug.env.$key=$value".toString())
                }
            } else {
                runner.withEnvironment(environmentVars)
            }
        }
        args.remove("--no-daemon")
        runner.withArguments(args)
        runner.withDebug(debug)
        runner.forwardOutput()
        runner
    }

    BuildResult run() {
        createRunner().build()
    }

    BuildResult runWithFailure() {
        return createRunner().buildAndFail()
    }
}
