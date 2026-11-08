/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.forward;

import static tech.kayys.aqli.safetensor.engine.forward.DirectForwardTensorOps.selectLastToken;

import tech.kayys.aljabr.safetensor.core.tensor.AccelOps;
import tech.kayys.aljabr.safetensor.core.tensor.AccelTensor;
import tech.kayys.aqli.safetensor.engine.generation.DirectInferenceProfiler;
import tech.kayys.aljabr.spi.model.ModelConfig;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

final class DirectForwardOutputProjection {
    private DirectForwardOutputProjection() {
    }

    static AccelTensor prefillLogits(DirectForwardPrefillLogitsRequest request) {
        DirectForwardOutputProjectionContext context = request.context();
        AccelTensor hidden = AccelTensor.view(request.hiddenSegment(), request.hiddenShape());
        AccelTensor normed = finalNorm(context, hidden, request.hiddenShape(), request.seqLen());
        if (hidden != request.embeddings() && hidden != normed) {
            hidden.close();
        }

        AccelTensor lmHeadW = lmHeadOrThrow(context.resolvedWeights());
        if (request.verboseTokens() && request.seqLen() > 1) {
            DirectForwardLogits.debugSequencePositionLogits(normed, lmHeadW, request.seqLen(),
                    (input, weight, bias) -> linear(context,
                            false, input, weight, bias, null, null));
        }

        AccelTensor lastPos = selectLastToken(normed, request.seqLen());
        if (Boolean.getBoolean("aqli.debug.mamba")) {
            float[] fnData = lastPos.toFloatArray();
            System.err.printf("[LOGITS-DBG] FinalNorm last token: [%.6f, %.6f, %.6f, %.6f]%n",
                    fnData[0], fnData[1], fnData[2], fnData[3]);
            System.err.flush();
        }
        AccelTensor logitsOutput = DirectForwardLogits.reusableOutputTensor(context.workspace(), lastPos, lmHeadW);
        try {
            AccelTensor projected = projectLogits(context, false, lastPos, lmHeadW, logitsOutput);
            if (Boolean.getBoolean("aqli.debug.mamba")) {
                float[] lData = projected.toFloatArray();
                // Find top 5
                java.util.PriorityQueue<int[]> pq = new java.util.PriorityQueue<>(
                        (a, b) -> Float.compare(Float.intBitsToFloat(a[1]), Float.intBitsToFloat(b[1])));
                for (int i = 0; i < lData.length; i++) {
                    pq.offer(new int[]{i, Float.floatToIntBits(lData[i])});
                    if (pq.size() > 5) pq.poll();
                }
                java.util.List<int[]> top = new java.util.ArrayList<>(pq);
                top.sort((a, b) -> Float.compare(Float.intBitsToFloat(b[1]), Float.intBitsToFloat(a[1])));
                System.err.print("[LOGITS-DBG] Top 5 logits: ");
                for (int[] pair : top) {
                    System.err.printf("(%d, %.4f) ", pair[0], Float.intBitsToFloat(pair[1]));
                }
                System.err.println();
                System.err.flush();
            }
            return projected;
        } finally {
            lastPos.closeWithParent();
        }
    }

    static AccelTensor decodeLogits(DirectForwardDecodeLogitsRequest request) {
        DirectForwardOutputProjectionContext context = request.context();
        AccelTensor finalHidden = AccelTensor.view(request.hiddenSegment(), request.hiddenShape());
        AccelTensor normed = finalNorm(context, finalHidden, request.hiddenShape(), 1);
        if (finalHidden != normed) {
            finalHidden.close();
        }

        AccelTensor lmHeadW = lmHeadOrThrow(context.resolvedWeights());
        AccelTensor logitsOutput = request.reuseLogitsOutput()
                ? DirectForwardLogits.reusableOutputTensor(context.workspace(), normed, lmHeadW)
                : null;
        try {
            return projectLogits(context, true, normed, lmHeadW, logitsOutput);
        } finally {
            if (normed.dataPtr() != context.workspace().getNormedAttnSeg()) {
                normed.close();
            }
        }
    }

    private static AccelTensor finalNorm(DirectForwardOutputProjectionContext context,
                                         AccelTensor hidden,
                                         long[] hiddenShape,
                                         int rows) {
        ResolvedModelWeights resolvedWeights = context.resolvedWeights();
        ModelConfig config = context.config();
        if (context.runtime().canUseMetalElementwise(context.traits(), rows)) {
            AccelTensor normed = AccelTensor.view(context.workspace().getNormedAttnSeg(), hiddenShape);
            DirectForwardElementwiseOps.rmsNormRowsMetal(
                    context.runtime().metalBinding(),
                    normed.dataPtr(),
                    hidden.dataPtr(),
                    resolvedWeights.finalNorm().dataPtr(),
                    rows,
                    config.hiddenSize(),
                    (float) config.rmsNormEps(),
                    resolvedWeights.addOneRmsNorm());
            return normed;
        }
        return AccelOps.rmsNorm(hidden, resolvedWeights.finalNorm(), config.rmsNormEps(),
                resolvedWeights.addOneRmsNorm());
    }

    private static AccelTensor projectLogits(DirectForwardOutputProjectionContext context,
                                             boolean decodeLogitsPhase,
                                             AccelTensor input,
                                             AccelTensor lmHeadW,
                                             AccelTensor outputBuffer) {
        long tLogits0 = System.nanoTime();
        AccelTensor logits = linear(context, decodeLogitsPhase, input, lmHeadW, null, "logits", outputBuffer);
        
        if (context.config().finalLogitSoftcapping() != null) {
            float softcap = context.config().finalLogitSoftcapping().floatValue();
            if (softcap > 0.0f) {
                applyFinalSoftcap(logits, softcap);
            }
        }

        if (context.config().lmHeadMultiplier() != null) {
            float multiplier = context.config().resolvedLmHeadMultiplier();
            if (multiplier != 1.0f) {
                applyLmHeadMultiplier(logits, multiplier);
            }
        }
        
        DirectInferenceProfiler.recordLogitsProjectionNanos(System.nanoTime() - tLogits0);
        return logits;
    }

    private static AccelTensor applyFinalSoftcap(AccelTensor logits, float softcap) {
        float[] floats = logits.toFloatArray();
        for (int i = 0; i < floats.length; i++) {
            floats[i] = (float) (Math.tanh(floats[i] / softcap) * softcap);
        }
        MemorySegment.copy(floats, 0, logits.dataPtr(), ValueLayout.JAVA_FLOAT, 0L, floats.length);
        return logits;
    }

    private static void applyLmHeadMultiplier(AccelTensor logits, float multiplier) {
        MemorySegment seg = logits.dataPtr();
        long numFloats = logits.numel();
        for (long i = 0; i < numFloats; i++) {
            long offset = i * Float.BYTES;
            float val = seg.get(ValueLayout.JAVA_FLOAT, offset);
            seg.set(ValueLayout.JAVA_FLOAT, offset, val * multiplier);
        }
    }

    private static AccelTensor linear(DirectForwardOutputProjectionContext context,
                                      boolean decodeLogitsPhase,
                                      AccelTensor input,
                                      AccelTensor weight,
                                      AccelTensor bias,
                                      String profileKey,
                                      AccelTensor outputBuffer) {
        return DirectForwardLinearProjection.linear(DirectForwardLinearRequest.logitsProjection(
                new DirectForwardLinearContext(context.runtime(), context.traits(), context.config()),
                decodeLogitsPhase,
                input,
                weight,
                bias,
                profileKey,
                outputBuffer));
    }

    private static AccelTensor lmHeadOrThrow(ResolvedModelWeights resolvedWeights) {
        AccelTensor lmHeadW = resolvedWeights.lmHead();
        if (lmHeadW == null) {
            throw new IllegalStateException(
                    "Missing lm_head weight. Safetensor file might be incomplete or config.tie_word_embeddings is missing.");
        }
        return lmHeadW;
    }

}
