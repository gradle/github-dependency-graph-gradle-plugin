package org.gradle.github.dependency.uploader


import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.gradle.github.dependency.base.BaseExtractorTest
import org.junit.Rule

abstract class BaseUploaderTest extends BaseExtractorTest {
    @Rule
    WireMockRule wireMock = new WireMockRule(9000 - new Random().nextInt(1000))

    def setup() {
        wireMock.start()
    }

    @Override
    protected void establishEnvironmentVariables() {
        Map<String, String> env = environmentVars.asEnvironmentMap()
        env.put("GITHUB_API_URL", wireMock.baseUrl())
        executer.withEnvironmentVars(env)
    }
}
