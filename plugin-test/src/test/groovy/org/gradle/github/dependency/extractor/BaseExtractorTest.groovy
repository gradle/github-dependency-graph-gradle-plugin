package org.gradle.github.dependency.extractor

import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import org.gradle.github.dependency.extractor.fixture.TestConfig
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.*
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.GradleRunner

abstract class BaseExtractorTest extends AbstractIntegrationSpec {
    private static final TestConfig TEST_CONFIG = new TestConfig()

    @CompileDynamic
    protected void applyExtractorPlugin() {
        File pluginJar = TEST_CONFIG.asFile("extractorPlugin.jar.path")
        assert (pluginJar.exists())
        file("init.gradle") << """
        import org.gradle.github.dependency.extractor.GithubDependencyExtractorPlugin
        initscript {
            dependencies {
                classpath files("${pluginJar.absolutePath}")
            }
        }
        apply plugin: GithubDependencyExtractorPlugin
        """.stripMargin()
        args("--init-script", "init.gradle")
    }

    @CompileDynamic
    protected Object jsonManifest() {
        def jsonSlurper = new JsonSlurper()
        File manifestFile = new File("github-manifest.json")
        assert (manifestFile.exists())
        println(manifestFile.text)
        return jsonSlurper.parse(manifestFile)
    }

    protected Map jsonManifests() {
        def json = jsonManifest()
        return json.manifests as Map
    }

    protected String purlFor(org.gradle.test.fixtures.Module module) {
        // NOTE: Don't use this in production, this is purely for test code. The escaping here may be insufficient.
        String repositoryUrlEscaped = URLEncoder.encode(mavenRepo.rootDir.toURI().toASCIIString(), "UTF-8")
        return "pkg:maven/${module.group}/${module.module}@${module.version}?repository_url=$repositoryUrlEscaped"
    }

    @Override
    GradleExecuter createExecuter() {
        def testKitDir = file("test-kit")
        return new TestKitBackedGradleExecuter(temporaryFolder, testKitDir)
    }

    static class TestKitBackedGradleExecuter extends AbstractGradleExecuter {
        List<File> pluginClasspath = []
        private final TestFile testKitDir

        TestKitBackedGradleExecuter(TestDirectoryProvider testDirectoryProvider, TestFile testKitDir) {
            super(null, testDirectoryProvider)
            this.testKitDir = testKitDir
        }

        @Override
        void assertCanExecute() throws AssertionError {
        }

        @Override
        protected ExecutionResult doRun() {
            def runnerResult = createRunner().build()
            return OutputScrapingExecutionResult.from(runnerResult.output, "")
        }

        @Override
        protected ExecutionFailure doRunWithFailure() {
            def runnerResult = createRunner().buildAndFail()
            return OutputScrapingExecutionFailure.from(runnerResult.output, "")
        }

        private GradleRunner createRunner() {
            def runner = GradleRunner.create()
            runner.withTestKitDir(testKitDir)
            runner.withProjectDir(workingDir)
            def args = allArgs
            args.remove("--no-daemon")
            runner.withArguments(args)
            runner.withPluginClasspath(pluginClasspath)
            if (!environmentVars.isEmpty()) {
                runner.withEnvironment(environmentVars)
            }
            runner.withDebug(debug)
            runner.forwardOutput()
            runner
        }
    }
}
