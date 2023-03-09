package org.gradle.github.dependency.base


import org.gradle.github.dependency.extractor.fixtures.SimpleGradleExecuter
import org.gradle.integtests.fixtures.CompiledLanguage
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.build.BuildTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion
import org.intellij.lang.annotations.Language
import org.junit.Rule
import spock.lang.Specification

class BaseIntegrationSpec extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    public final MavenFileRepository mavenRepo = new MavenFileRepository(temporaryFolder.testDirectory.file("maven-repo"))
    BuildTestFixture buildTestFixture = new BuildTestFixture(temporaryFolder)

    private SimpleGradleExecuter executer
    private BuildResult result

    def setup() {
        if (System.getProperty("os.name").containsIgnoreCase("windows")) {
            // Suppress file cleanup check on windows
            temporaryFolder.suppressCleanupErrors()
        }
    }

    SimpleGradleExecuter getExecuter() {
        if (executer == null) {
            executer = createExecuter()
        }
        return executer
    }

    SimpleGradleExecuter createExecuter() {
        return createExecuter(GradleVersion.current().version)
    }

    SimpleGradleExecuter createExecuter(String gradleVersion) {
        println("Executing test with Gradle $gradleVersion")
        def testKitDir = file("test-kit")
        return new SimpleGradleExecuter(temporaryFolder, testKitDir, gradleVersion)
    }


    TestFile getTestDirectory() {
//        if (testDirOverride != null) {
//            return testDirOverride
//        }
        temporaryFolder.testDirectory
    }

    TestFile file(Object... path) {
        if (path.length == 1 && path[0] instanceof TestFile) {
            return path[0] as TestFile
        }
        getTestDirectory().file(path)
    }

    TestFile groovyTestSourceFile(@Language("groovy") String source) {
        file("src/test/groovy/Test.groovy") << source
    }

    TestFile javaTestSourceFile(@Language("java") String source) {
        file("src/test/java/Test.java") << source
    }

    def singleProjectBuild(String projectName, @DelegatesTo(value = BuildTestFile, strategy = Closure.DELEGATE_FIRST) Closure cl = {}) {
        buildTestFixture.singleProjectBuild(projectName, cl)
    }

    def multiProjectBuild(String projectName, List<String> subprojects, @DelegatesTo(value = BuildTestFile, strategy = Closure.DELEGATE_FIRST) Closure cl = {}) {
        multiProjectBuild(projectName, subprojects, CompiledLanguage.JAVA, cl)
    }

    def multiProjectBuild(String projectName, List<String> subprojects, CompiledLanguage language, @DelegatesTo(value = BuildTestFile, strategy = Closure.DELEGATE_FIRST) Closure cl = {}) {
        buildTestFixture.multiProjectBuild(projectName, subprojects, language, cl)
    }

    /**
     * Synonym for succeeds()
     */
    protected BuildResult run(String... tasks) {
        succeeds(*tasks)
    }

    protected BuildResult run(List<String> tasks) {
        succeeds(tasks.toArray(new String[tasks.size()]))
    }

    protected SimpleGradleExecuter args(String... args) {
        getExecuter().withArguments(args)
    }

    protected SimpleGradleExecuter withDebugLogging() {
        getExecuter().withArgument("-d")
    }

    protected BuildResult succeeds(String... tasks) {
        result = getExecuter().withTasks(*tasks).run()
        return result
    }

    BuildResult getResult() {
        if (result == null) {
            throw new IllegalStateException("No build result is available yet.")
        }
        return result
    }

    boolean isFailed() {
        return result.output.contains('BUILD FAILED')
    }

    protected BuildResult runAndFail(String... tasks) {
        fails(*tasks)
    }

    protected BuildResult fails(String... tasks) {
        result = getExecuter().withTasks(*tasks).runWithFailure()
        return result
    }

    void reset() {
        executer = null
        result = null
    }
}
