package org.gradle.github.dependency.extractor

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.*
import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import org.gradle.github.dependency.fixture.TestConfig
import org.gradle.internal.hash.Hashing
import org.gradle.test.fixtures.SimpleGradleExecuter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion
import spock.lang.Specification
import spock.lang.TempDir

import java.util.stream.Collectors

abstract class BaseExtractorTest extends Specification {

    @TempDir
    File testDir

    private static final TestConfig TEST_CONFIG = new TestConfig()
    public TestEnvironmentVars environmentVars
    public MavenFileRepository mavenRepo

    private JsonRepositorySnapshotLoader loader

    private SimpleGradleExecuter executer
    private BuildResult result

    MavenFileRepository getMavenRepo() {
        if (mavenRepo == null) {
            mavenRepo = new MavenFileRepository(testDirectory.file("maven-repo"))
        }
        return mavenRepo
    }

    SimpleGradleExecuter getExecuter() {
        if (executer == null) {
            executer = createExecuter()
        }
        return executer
    }

    SimpleGradleExecuter createExecuter() {
        // Create a new JsonManifestLoader for each invocation of the executer
        File manifestFile =
                testDirectory.file("build/reports/github-dependency-report/github-dependency-manifest.json")
        loader = new JsonRepositorySnapshotLoader(manifestFile)
        def gradleVersion = System.getProperty("testGradleVersion", GradleVersion.current().version)
        return createExecuter(gradleVersion)
    }


    SimpleGradleExecuter createExecuter(String gradleVersion) {
        println("Executing test with Gradle $gradleVersion")
        def testKitDir = file("test-kit")
        return new SimpleGradleExecuter(testDirectory, testKitDir, gradleVersion)
    }


    TestFile getTestDirectory() {
        new TestFile(testDir)
    }

    TestFile file(Object... path) {
        if (path.length == 1 && path[0] instanceof TestFile) {
            return path[0] as TestFile
        }
        getTestDirectory().file(path)
    }

    protected SimpleGradleExecuter args(String... args) {
        getExecuter().withArguments(args)
    }

    protected SimpleGradleExecuter withDebugLogging() {
        getExecuter().withArgument("-d")
    }

    protected BuildResult run() {
        result = getExecuter().run()
        return result
    }

    @CompileDynamic
    protected void applyExtractorPlugin() {
        File pluginJar = TEST_CONFIG.asFile("extractorPlugin.jar.path")
        String cleanedAbsolutePath = pluginJar.absolutePath.replace('\\',  '/')
        assert (pluginJar.exists())
        file("init.gradle") << """
        import org.gradle.github.dependency.GitHubDependencySubmissionPlugin
        initscript {
            dependencies {
                classpath files('${cleanedAbsolutePath}')
            }
        }
        apply plugin: GitHubDependencySubmissionPlugin
        """.stripMargin()
        args("--init-script", "init.gradle")
    }

    protected void establishEnvironmentVariables() {
        environmentVars = new TestEnvironmentVars(testDirectory)
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
        assert job.correlator == environmentVars.job
        assert job.id == environmentVars.runNumber
        def detector = json.detector as Map
        assert detector.name.contains("Gradle")
        assert detector.version != null
        assert detector.version != ""
        assert detector.url == "https://github.com/gradle/github-dependency-extractor"
        return json["manifests"] as Map
    }

    protected Map jsonManifest(String manifestName) {
        Map manifests = jsonManifests()
        assert manifests.keySet().contains(manifestName)
        Map manifest = manifests[manifestName] as Map
        assert manifest.name == manifestName
        return manifest
    }

    protected List<String> getManifestNames() {
        return jsonManifests().keySet() as List
    }

    protected GitHubManifest gitHubManifest(String manifestName) {
        def jsonManifest = jsonManifest(manifestName)
        return new GitHubManifest(jsonManifest)
    }

    protected static class GitHubManifest {
        Map manifestData

        GitHubManifest(Map manifestData) {
            this.manifestData = manifestData
        }

        def getName() {
            return manifestData.name
        }

        def getSourceFile() {
            return (manifestData.file as Map).source_location
        }

        def assertResolved(Map<String, Map> expectedResolved = [:]) {
            def resolved = manifestData.resolved as Map<String, Map>

            assert resolved.keySet() == expectedResolved.keySet()

            for (String key : expectedResolved.keySet()) {
                def actual = resolved[key]
                def expected = expectedResolved[key]

                assert actual.package_url == expected.package_url
                assert actual.relationship == (expected.relationship ?: "direct")
                assert actual.dependencies == (expected.dependencies ?: [])
            }

            return true
        }
    }

    @CompileStatic
    private static class JsonRepositorySnapshotLoader {
        private static final String SCHEMA = "schema/github-repository-snapshot-schema.json"
        private final File manifestFile

        JsonRepositorySnapshotLoader(File manifestFile) {
            this.manifestFile = manifestFile
        }

        @Memoized
        protected Object jsonRepositorySnapshot() {
            def jsonSlurper = new JsonSlurper()
            println(manifestFile.text)
            JsonSchema schema = createSchemaValidator()
            ObjectMapper mapper = new ObjectMapper()
            JsonNode node = mapper.readTree(manifestFile)
            validateAgainstJsonSchema(schema, node)
            return jsonSlurper.parse(manifestFile)
        }

        private static void validateAgainstJsonSchema(JsonSchema schema, JsonNode json) {
            final Set<ValidationMessage> validationMessages = schema.validate(json)
            if (!validationMessages.isEmpty()) {
                final String newline = System.lineSeparator()
                final String violationMessage = createViolationMessage(newline, validationMessages)
                throw new AssertionError(
                        ("Dependency constraints contains schema violations:" + newline + violationMessage) as Object
                )
            }
        }

        @CompileDynamic
        private static String createViolationMessage(String newline, Set<ValidationMessage> validationMessages) {
            return validationMessages
                    .stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.joining(newline + "  - ", "  - ", ""))
        }

        private static JsonSchema createSchemaValidator() {
            final JsonSchemaFactory factory =
                    JsonSchemaFactory
                            .builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4))
                            .build()
            try {
                return factory
                        .getSchema(
                                JsonRepositorySnapshotLoader.class.classLoader.getResourceAsStream(SCHEMA)
                        )
            } catch (JsonSchemaException ex) {
                throw new RuntimeException(
                        "Unable to load dependency constraints schema (resource: " + SCHEMA + ")",
                        ex
                )
            }
        }
    }

    static class TestEnvironmentVars {
        final String job = "Build -" + System.currentTimeMillis()
        final String runNumber = UUID.randomUUID().toString()
        final String ref = "refs/head/feature/test" + UUID.randomUUID().toString()
        final String sha = fakeSha()
        final String workspace
        final String gitHubRepository = "gradle/github-dependency-extractor"
        final String gitHubToken = UUID.randomUUID().toString()

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
                    "GITHUB_REPOSITORY": gitHubRepository,
                    "GITHUB_TOKEN"     : gitHubToken
            ]
        }

        private static String fakeSha() {
            Hashing.sha1().hashString(UUID.toString().toString()).toString()
        }

    }
}
