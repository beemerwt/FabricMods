package com.github.beemerwt.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as optional for Jankson serialization.
 * - On write: omitted when the value is null/zero/empty/default.
 * - On read: missing keys are ignored (the field keeps its current/default value).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface JankOptional {
}

