package org.gradle.github.dependency.extractor.internal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.github.packageurl.PackageURL;

import java.io.IOException;

/**
 * @implNote Must be written in Java. Shadow Jar relocation doesn't support Kotlin Reflection.
 */
class PackageUrlSerializer extends StdSerializer<PackageURL> {

    protected PackageUrlSerializer() {
        super(PackageURL.class);
    }

    @Override
    public void serialize(PackageURL value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(value.toString());
    }
}
