package org.gradle.test.fixtures

interface HttpRepository extends Repository {
    enum MetadataType {
        DEFAULT,
        ONLY_ORIGINAL,
        ONLY_GRADLE
    }

    @Override
    HttpModule module(String group, String module)

    @Override
    HttpModule module(String group, String module, String version)

    MetadataType getProvidesMetadata()
}
