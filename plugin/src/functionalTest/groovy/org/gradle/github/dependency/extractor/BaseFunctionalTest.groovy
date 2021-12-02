package org.gradle.github.dependency.extractor

import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

@CompileStatic
abstract class BaseFunctionalTest extends Specification {
    @TempDir
    File projectDir

    protected void writeString(File file, String string) throws IOException {
        try (Writer writer = new FileWriter(file, true)) {
            writer.write(string);
        }
    }

    @CompileDynamic
    protected void applyExtractorPlugin() {
        File pluginJar = new File("build/libs/plugin-all.jar")
        assert(pluginJar.exists())
        writeString(new File(projectDir, "init.gradle"),
            """
            import org.gradle.github.dependency.extractor.GithubDependencyExtractorPlugin
            initscript {
                dependencies {
                    classpath files("${pluginJar.absolutePath}")
                }
            }
            apply plugin: GithubDependencyExtractorPlugin
            """.stripMargin()
        )
    }

    GradleRunner createGradleRunner() {
        GradleRunner runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withProjectDir(projectDir)
        runner.withDebug(true)
        return runner
    }

    protected BuildResult succeed(String... args) {
        GradleRunner runner = createGradleRunner()
        List<String> arguments = args.toList()
        arguments.add("--stacktrace")
        arguments.addAll("--init-script", "init.gradle")
        return runner.withArguments(arguments).build()
    }

    @CompileDynamic
    protected Object jsonManifest() {
        def jsonSlurper = new JsonSlurper()
        File manifestFile = new File("github-manifest.json")
        assert(manifestFile.exists())
        println(manifestFile.text)
        return jsonSlurper.parse(manifestFile)
    }
}
