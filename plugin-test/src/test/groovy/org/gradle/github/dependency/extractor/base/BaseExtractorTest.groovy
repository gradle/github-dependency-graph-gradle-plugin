package org.gradle.github.dependency.extractor.base

import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import org.gradle.github.dependency.extractor.fixture.TestConfig
import org.gradle.github.dependency.extractor.fixtures.SimpleGradleExecuter
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.internal.hash.Hashing
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion

@TargetCoverage({ getTestedGradleVersions() })
abstract class BaseExtractorTest extends BaseMultiVersionIntegrationSpec {
    static List<String> getTestedGradleVersions() {
        return [GradleVersion.current(), GradleVersion.version("5.0")].collect { it.version }
    }

    private static final TestConfig TEST_CONFIG = new TestConfig()
    protected TestEnvironmentVars environmentVars = new TestEnvironmentVars(testDirectory)
    private JsonRepositorySnapshotLoader loader

    @Override
    SimpleGradleExecuter createExecuter() {
        // Create a new JsonManifestLoader for each invocation of the executer
        File manifestFile =
                testDirectory.file("build/reports/github-dependency-report/github-dependency-manifest.json")
        loader = new JsonRepositorySnapshotLoader(manifestFile)
        return createExecuter(BaseMultiVersionIntegrationSpec.version.toString())
    }

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

    protected void establishEnvironmentVariables() {
        executer.withEnvironmentVars(environmentVars.asEnvironmentMap())
    }

    protected String purlFor(org.gradle.test.fixtures.Module module) {
        // NOTE: Don't use this in production, this is purely for test code. The escaping here may be insufficient.
        String repositoryUrlEscaped = URLEncoder.encode(mavenRepo.rootDir.toURI().toASCIIString(), "UTF-8")
        return "pkg:maven/${module.group}/${module.module}@${module.version}?repository_url=$repositoryUrlEscaped"
    }

    @CompileDynamic
    protected Map jsonRepositorySnapshot() {
        return loader.jsonRepositorySnapshot()
    }

    protected Map jsonManifests() {
        def json = jsonRepositorySnapshot()
        assert json.version == 0
        assert json.sha == environmentVars.sha
        assert json.ref == environmentVars.ref
        def job = json.job as Map
        assert job.name == environmentVars.job
        assert job.id == environmentVars.runNumber
        def detector = json.detector as Map
        assert detector.name.contains("Gradle")
        assert detector.version != null
        assert detector.version != ""
        assert detector.url == "https://github.com/gradle/github-dependency-extractor"
        return json["manifests"] as Map
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

    protected Map jsonRepositorySnapshot(Map args) {
        String manifestName = manifestKey(args)
        Map manifests = jsonManifests()
        assert manifests.keySet().contains(manifestName)
        Map manifest = manifests[manifestName] as Map
        assert manifest.name == manifestName
        return manifest
    }

    @CompileStatic
    private static class JsonRepositorySnapshotLoader {
        private final File manifestFile

        JsonRepositorySnapshotLoader(File manifestFile) {
            this.manifestFile = manifestFile
        }

        @Memoized
        protected Object jsonRepositorySnapshot() {
            def jsonSlurper = new JsonSlurper()
            println(manifestFile.text)
            return jsonSlurper.parse(manifestFile)
        }
    }

    static class TestEnvironmentVars {
        final String job = "Build -" + System.currentTimeMillis()
        final String runNumber = UUID.randomUUID().toString()
        final String ref = "refs/head/feature/test" + UUID.randomUUID().toString()
        final String sha = fakeSha()
        final String workspace

        TestEnvironmentVars(TestFile testDirectory) {
            workspace = testDirectory.absolutePath
        }

        Map<String, String> asEnvironmentMap() {
            return [
                    "GITHUB_JOB"       : job,
                    "GITHUB_RUN_NUMBER": runNumber,
                    "GITHUB_REF"       : ref,
                    "GITHUB_SHA"       : sha,
                    "GITHUB_WORKSPACE" : workspace,
            ]
        }

        private static String fakeSha() {
            Hashing.sha1().hashString(UUID.toString().toString()).toString()
        }

    }
}
