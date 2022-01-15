package org.gradle.github.dependency.base

import org.gradle.integtests.fixtures.compatibility.MultiVersionTest
import org.gradle.integtests.fixtures.compatibility.MultiVersionTestCategory
import org.gradle.util.internal.VersionNumber

/**
 * See {@link org.gradle.integtests.fixtures.compatibility.AbstractContextualMultiVersionTestInterceptor} for information on running these tests.
 */
@MultiVersionTest
@MultiVersionTestCategory
class BaseMultiVersionIntegrationSpec extends BaseIntegrationSpec {

    static def version

    static VersionNumber getVersionNumber() {
        VersionNumber.parse(version.toString())
    }
}
