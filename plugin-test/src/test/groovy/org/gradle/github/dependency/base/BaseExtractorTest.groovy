package org.gradle.github.dependency.base

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.*
import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import org.gradle.test.fixtures.SimpleGradleExecuter
import org.gradle.github.dependency.fixture.TestConfig
import org.gradle.internal.hash.Hashing
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion

import java.util.stream.Collectors

abstract class BaseExtractorTest extends BaseIntegrationSpec {

    private static final TestConfig TEST_CONFIG = new TestConfig()
    protected TestEnvironmentVars environmentVars = new TestEnvironmentVars(testDirectory)
    private JsonRepositorySnapshotLoader loader

    @Override
    SimpleGradleExecuter createExecuter() {
        // Create a new JsonManifestLoader for each invocation of the executer
        File manifestFile =
                testDirectory.file("build/reports/github-dependency-report/github-dependency-manifest.json")
        loader = new JsonRepositorySnapshotLoader(manifestFile)
        def gradleVersion = System.getProperty("testGradleVersion", GradleVersion.current().version)
        return createExecuter(gradleVersion)
    }

    @CompileDynamic
    protected void applyExtractorPlugin() {
        File pluginJar = TEST_CONFIG.asFile("extractorPlugin.jar.path")
        String cleanedAbsolutePath = pluginJar.absolutePath.replace('\\',  '/')
        assert (pluginJar.exists())
        file("init.gradle") << """
        import org.gradle.github.dependency.extractor.GithubDependencyExtractorPlugin
        initscript {
            dependencies {
                classpath files('${cleanedAbsolutePath}')
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

    protected String gavFor(org.gradle.test.fixtures.Module module) {
        return "${module.group}:${module.module}:${module.version}"
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
