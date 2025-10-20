package com.github.beemerwt.resourcelib;

import java.util.Optional;

public final class ResourceApis {
    private static volatile ResourceApi INSTANCE;
    private ResourceApis() {}
    public static void install(ResourceApi impl) { INSTANCE = impl; }
    public static Optional<ResourceApi> resolve() {
        return Optional.ofNullable(INSTANCE);
    }
}
