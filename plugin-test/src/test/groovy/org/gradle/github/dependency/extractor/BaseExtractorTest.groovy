package org.gradle.github.dependency.extractor

import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import org.gradle.github.dependency.extractor.fixture.TestConfig
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.executer.*
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion

@TargetCoverage({ getTestedGradleVersions() })
abstract class BaseExtractorTest extends MultiVersionIntegrationSpec {
    static List<String> getTestedGradleVersions() {
        return [GradleVersion.current(), GradleVersion.version("5.0")].collect { it.version }
    }

    private static final TestConfig TEST_CONFIG = new TestConfig()
    private JsonManifestLoader loader;

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

    protected String purlFor(org.gradle.test.fixtures.Module module) {
        // NOTE: Don't use this in production, this is purely for test code. The escaping here may be insufficient.
        String repositoryUrlEscaped = URLEncoder.encode(mavenRepo.rootDir.toURI().toASCIIString(), "UTF-8")
        return "pkg:maven/${module.group}/${module.module}@${module.version}?repository_url=$repositoryUrlEscaped"
    }

    @CompileDynamic
    protected Object jsonManifest() {
        return loader.jsonManifest()
    }

    protected Map jsonManifests() {
        return loader.jsonManifests()
    }

    protected String manifestKey(Map args) {
        String build = args.getOrDefault("build", ":")
        String project = args.getOrDefault("project", ":")
        boolean isBuildscript = args.getOrDefault("buildscript", false)
        String configuration = args.get("configuration")
        if (!configuration) {
            throw new IllegalArgumentException("Missing 'configuration' parameter")
        }
        return "Build: ${build}, Project: ${project}, ${isBuildscript ? "Buildscript " : ""}Configuration: $configuration"
    }

    protected Map jsonManifest(Map args) {
        String manifestName = manifestKey(args)
        Map manifests = jsonManifests()
        assert manifests.keySet().contains(manifestName)
        Map manifest = manifests[manifestName] as Map
        assert manifest.name == manifestName
        return manifest
    }

    @Override
    GradleExecuter createExecuter() {
        def testKitDir = file("test-kit")

        // Create a new JsonManifestLoader for each invocation of the executer
        File manifestFile = new File("github-manifest.json")
        assert (manifestFile.exists())
        loader = new JsonManifestLoader(manifestFile)

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
            runner.withGradleVersion(version.toString())
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

    @CompileStatic
    private static class JsonManifestLoader {
        private final File manifestFile

        JsonManifestLoader(File manifestFile) {
            this.manifestFile = manifestFile
        }

        @Memoized
        protected Object jsonManifest() {
            def jsonSlurper = new JsonSlurper()
            println(manifestFile.text)
            return jsonSlurper.parse(manifestFile)
        }

        @Memoized
        protected Map jsonManifests() {
            def json = jsonManifest()
            return json["manifests"] as Map
        }
    }
}
