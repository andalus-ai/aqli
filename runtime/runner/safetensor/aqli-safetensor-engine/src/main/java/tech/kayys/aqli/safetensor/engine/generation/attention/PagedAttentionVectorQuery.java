/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.generation.attention;

record PagedAttentionVectorQuery(
        int batchIndex,
        int qHeadIndex,
        int queryIndex,
        int kvHeadIndex,
        int absolutePosition,
        int minPosition,
        long queryByteOffset,
        long outputElementIndex,
        boolean debugProbe) {
}
