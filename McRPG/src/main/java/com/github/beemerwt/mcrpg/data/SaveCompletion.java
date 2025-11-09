package com.github.beemerwt.mcrpg.data;

import java.util.UUID;

public record SaveCompletion(UUID id, long seq, boolean ok) {}
