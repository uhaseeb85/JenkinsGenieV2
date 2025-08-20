package com.example.cifixer.monitoring;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseMetricsTest {

    private MeterRegistry meterRegistry;
    private DatabaseMetrics databaseMetrics;

    @Mock
    private HikariDataSource hikariDataSource;

    @Mock
    private HikariPoolMXBean hikariPoolMXBean;

    @Mock
    private DataSource nonHikariDataSource;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void shouldInitializeMetricsWithHikariDataSource() {
        // Given
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(5);
        when(hikariPoolMXBean.getIdleConnections()).thenReturn(3);
        when(hikariPoolMXBean.getTotalConnections()).thenReturn(8);
        
        // When
        databaseMetrics = new DatabaseMetrics(meterRegistry, hikariDataSource);
        databaseMetrics.initializeMetrics();
        
        // Then
        assertThat(meterRegistry.find("cifixer.database.connections.active").gauge()).isNotNull();
        assertThat(meterRegistry.find("cifixer.database.connections.idle").gauge()).isNotNull();
        assertThat(meterRegistry.find("cifixer.database.connections.total").gauge()).isNotNull();
        assertThat(meterRegistry.find("cifixer.database.connections.pending").gauge()).isNotNull();
    }

    @Test
    void shouldHandleNonHikariDataSource() {
        // When
        databaseMetrics = new DatabaseMetrics(meterRegistry, nonHikariDataSource);
        databaseMetrics.initializeMetrics();
        
        // Then
        assertThat(databaseMetrics.getActiveConnections()).isEqualTo(0);
        assertThat(databaseMetrics.getIdleConnections()).isEqualTo(0);
        assertThat(databaseMetrics.getTotalConnections()).isEqualTo(0);
    }

    @Test
    void shouldReturnHealthyStatusWhenConnectionsAreNormal() {
        // Given
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(5);
        when(hikariPoolMXBean.getIdleConnections()).thenReturn(3);
        when(hikariPoolMXBean.getTotalConnections()).thenReturn(8);
        when(hikariPoolMXBean.getThreadsAwaitingConnection()).thenReturn(2);

        
        databaseMetrics = new DatabaseMetrics(meterRegistry, hikariDataSource);
        databaseMetrics.initializeMetrics();
        
        // When
        Health health = databaseMetrics.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("activeConnections", 5);
        assertThat(health.getDetails()).containsEntry("idleConnections", 3);
        assertThat(health.getDetails()).containsEntry("totalConnections", 8);
        assertThat(health.getDetails()).containsEntry("threadsAwaitingConnection", 2);
    }

    @Test
    void shouldReturnUnhealthyStatusWhenTooManyThreadsAwaiting() {
        // Given
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(8);
        when(hikariPoolMXBean.getIdleConnections()).thenReturn(0);
        when(hikariPoolMXBean.getTotalConnections()).thenReturn(8);
        when(hikariPoolMXBean.getThreadsAwaitingConnection()).thenReturn(10); // > 5

        
        databaseMetrics = new DatabaseMetrics(meterRegistry, hikariDataSource);
        databaseMetrics.initializeMetrics();
        
        // When
        Health health = databaseMetrics.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "Too many threads awaiting connections");
        assertThat(health.getDetails()).containsEntry("threadsAwaitingConnection", 10);
    }

    @Test
    void shouldReturnUnhealthyStatusWhenNoConnections() {
        // Given
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(0);
        when(hikariPoolMXBean.getIdleConnections()).thenReturn(0);
        when(hikariPoolMXBean.getTotalConnections()).thenReturn(0);
        when(hikariPoolMXBean.getThreadsAwaitingConnection()).thenReturn(0);

        
        databaseMetrics = new DatabaseMetrics(meterRegistry, hikariDataSource);
        databaseMetrics.initializeMetrics();
        
        // When
        Health health = databaseMetrics.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "No database connections available");
    }

    @Test
    void shouldReturnUnknownStatusWhenHikariMXBeanNotAvailable() {
        // Given
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(null);
        
        databaseMetrics = new DatabaseMetrics(meterRegistry, hikariDataSource);
        databaseMetrics.initializeMetrics();
        
        // When
        Health health = databaseMetrics.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("reason", "HikariCP metrics not available");
    }

    @Test
    void shouldReturnDownStatusOnException() {
        // Given
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        when(hikariPoolMXBean.getActiveConnections()).thenThrow(new RuntimeException("Connection error"));
        
        databaseMetrics = new DatabaseMetrics(meterRegistry, hikariDataSource);
        databaseMetrics.initializeMetrics();
        
        // When
        Health health = databaseMetrics.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error", "Connection error");
    }

    @Test
    void shouldReturnCorrectMetricValues() {
        // Given
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(7);
        when(hikariPoolMXBean.getIdleConnections()).thenReturn(3);
        when(hikariPoolMXBean.getTotalConnections()).thenReturn(10);
        when(hikariPoolMXBean.getThreadsAwaitingConnection()).thenReturn(1);

        
        databaseMetrics = new DatabaseMetrics(meterRegistry, hikariDataSource);
        databaseMetrics.initializeMetrics();
        
        // When/Then
        assertThat(databaseMetrics.getActiveConnections()).isEqualTo(7);
        assertThat(databaseMetrics.getIdleConnections()).isEqualTo(3);
        assertThat(databaseMetrics.getTotalConnections()).isEqualTo(10);
        assertThat(databaseMetrics.getThreadsAwaitingConnection()).isEqualTo(1);
    }

    @Test
    void shouldHandleNullHikariPoolMXBean() {
        // Given
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(null);
        
        databaseMetrics = new DatabaseMetrics(meterRegistry, hikariDataSource);
        databaseMetrics.initializeMetrics();
        
        // When/Then
        assertThat(databaseMetrics.getActiveConnections()).isEqualTo(0);
        assertThat(databaseMetrics.getIdleConnections()).isEqualTo(0);
        assertThat(databaseMetrics.getTotalConnections()).isEqualTo(0);
        assertThat(databaseMetrics.getThreadsAwaitingConnection()).isEqualTo(0);
    }
}