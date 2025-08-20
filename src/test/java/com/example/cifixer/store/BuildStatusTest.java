package com.example.cifixer.store;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuildStatusTest {

    @Test
    void shouldHaveAllExpectedStatuses() {
        BuildStatus[] statuses = BuildStatus.values();
        
        assertThat(statuses).contains(
            BuildStatus.PROCESSING,
            BuildStatus.COMPLETED,
            BuildStatus.FAILED
        );
    }

    @Test
    void shouldHaveCorrectStringRepresentation() {
        assertThat(BuildStatus.PROCESSING.toString()).isEqualTo("PROCESSING");
        assertThat(BuildStatus.COMPLETED.toString()).isEqualTo("COMPLETED");
        assertThat(BuildStatus.FAILED.toString()).isEqualTo("FAILED");
    }

    @Test
    void shouldSupportValueOf() {
        assertThat(BuildStatus.valueOf("PROCESSING")).isEqualTo(BuildStatus.PROCESSING);
        assertThat(BuildStatus.valueOf("COMPLETED")).isEqualTo(BuildStatus.COMPLETED);
        assertThat(BuildStatus.valueOf("FAILED")).isEqualTo(BuildStatus.FAILED);
    }

    @Test
    void shouldBeComparable() {
        assertThat(BuildStatus.PROCESSING.ordinal()).isLessThan(BuildStatus.COMPLETED.ordinal());
        assertThat(BuildStatus.COMPLETED.ordinal()).isLessThan(BuildStatus.FAILED.ordinal());
    }
}