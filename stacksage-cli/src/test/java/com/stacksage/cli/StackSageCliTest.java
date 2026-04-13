package com.stacksage.cli;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StackSageCliTest {

    private StackSageCli cliWithEnv(Map<String, String> env) {
        return new StackSageCli(env::get);
    }

    @Test
    void resolveServerUrl_flagTakesPrecedence() {
        StackSageCli cli = cliWithEnv(Map.of("STACKSAGE_SERVER", "http://env:1111"));
        String result = cli.resolveServerUrl("http://custom:9090");
        assertThat(result).isEqualTo("http://custom:9090");
    }

    @Test
    void resolveServerUrl_stripsTrailingSlash() {
        StackSageCli cli = cliWithEnv(Map.of());
        String result = cli.resolveServerUrl("http://custom:9090/");
        assertThat(result).isEqualTo("http://custom:9090");
    }

    @Test
    void resolveServerUrl_nullFlag_usesEnv() {
        StackSageCli cli = cliWithEnv(Map.of("STACKSAGE_SERVER", "http://env-server:8080"));
        String result = cli.resolveServerUrl(null);
        assertThat(result).isEqualTo("http://env-server:8080");
    }

    @Test
    void resolveServerUrl_nullFlag_noEnv_usesDefault() {
        StackSageCli cli = cliWithEnv(Map.of());
        String result = cli.resolveServerUrl(null);
        assertThat(result).isEqualTo("http://localhost:8080");
    }

    @Test
    void resolveServerUrl_blankFlag_usesDefault() {
        StackSageCli cli = cliWithEnv(Map.of());
        String result = cli.resolveServerUrl("  ");
        assertThat(result).isEqualTo("http://localhost:8080");
    }
}
