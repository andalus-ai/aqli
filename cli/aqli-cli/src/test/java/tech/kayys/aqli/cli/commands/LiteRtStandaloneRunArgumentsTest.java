package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class LiteRtStandaloneRunArgumentsTest {

    @Test
    void buildsTheLiteRtStandaloneCommandVectorInStableOrder() {
        assertArrayEquals(
                new String[] {
                        "run",
                        "--modelFile", "/models/gemma.litertlm",
                        "--prompt", "explain Java FFM",
                        "--max-tokens", "128",
                        "--temperature", "0.7",
                        "--top-k", "40",
                        "--top-p", "0.9",
                        "--provider", "litert"
                },
                LiteRtStandaloneRunArguments.toArgs(
                        "/models/gemma.litertlm",
                        "explain Java FFM",
                        128,
                        0.7,
                        40,
                        0.9));
    }
}
