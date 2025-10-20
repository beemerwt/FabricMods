package com.github.beemerwt.resourcelib;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.ServiceLoader;

public interface ResourceApi {
    Path getPackDirectory();
    void addBedrockPack(File file);
    void addJavaPack(File file);

    static Optional<ResourceApi> api() { return ResourceApis.resolve(); }
}


