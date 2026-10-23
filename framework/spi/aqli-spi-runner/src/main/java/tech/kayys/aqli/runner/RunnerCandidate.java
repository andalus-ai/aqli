package tech.kayys.aqli.runner;

import tech.kayys.aljabr.spi.model.RunnerMetadata;

/**
 * Candidate runner for selection with score
 */
public record RunnerCandidate(
                String name,
                int score,
                RunnerMetadata metadata) {
}
