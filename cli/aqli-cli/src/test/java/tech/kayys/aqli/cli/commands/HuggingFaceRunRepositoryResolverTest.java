package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import tech.kayys.aqli.sdk.model.HuggingFacePullRequest;
import tech.kayys.aqli.sdk.runtime.AqliRuntime;

class HuggingFaceRunRepositoryResolverTest {

    @Test
    void reusesAnIndexedRepositoryWithoutPulling() throws Exception {
        AqliRuntime runtime = mock(AqliRuntime.class);
        LocalModelIndex.Entry existing = new LocalModelIndex.Entry();

        LocalModelIndex.Entry resolved = HuggingFaceRunRepositoryResolver.resolve(
                options(false, false),
                runtime,
                (repository, format) -> Optional.of(existing),
                () -> { },
                ignored -> { },
                ignored -> { },
                ignored -> { });

        assertSame(existing, resolved);
        verify(runtime, never()).pullHuggingFaceModel(any(HuggingFacePullRequest.class), any());
    }

    @Test
    void offlineModeReportsTheMissingRepositoryWithoutPulling() throws Exception {
        AqliRuntime runtime = mock(AqliRuntime.class);
        java.util.List<String> errors = new java.util.ArrayList<>();

        LocalModelIndex.Entry resolved = HuggingFaceRunRepositoryResolver.resolve(
                options(true, false),
                runtime,
                (repository, format) -> Optional.empty(),
                () -> { },
                ignored -> { },
                errors::add,
                ignored -> { });

        assertNull(resolved);
        org.junit.jupiter.api.Assertions.assertEquals(2, errors.size());
        verify(runtime, never()).pullHuggingFaceModel(any(HuggingFacePullRequest.class), any());
    }

    @Test
    void refreshesTheIndexAfterPullAndReturnsTheDownloadedEntry() throws Exception {
        AqliRuntime runtime = mock(AqliRuntime.class);
        LocalModelIndex.Entry downloaded = new LocalModelIndex.Entry();
        AtomicBoolean refreshed = new AtomicBoolean();

        LocalModelIndex.Entry resolved = HuggingFaceRunRepositoryResolver.resolve(
                options(false, true),
                runtime,
                (repository, format) -> refreshed.get() ? Optional.of(downloaded) : Optional.empty(),
                () -> refreshed.set(true),
                ignored -> { },
                ignored -> { },
                ignored -> { });

        assertSame(downloaded, resolved);
        verify(runtime).pullHuggingFaceModel(any(HuggingFacePullRequest.class), any());
    }

    private static HuggingFaceRunRepositoryResolver.Options options(boolean offline, boolean quiet) {
        return new HuggingFaceRunRepositoryResolver.Options(
                "hf:owner/model",
                "gguf",
                "native",
                false,
                offline,
                false,
                "main",
                quiet,
                false);
    }
}
