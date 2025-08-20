package com.example.cifixer.monitoring;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

/**
 * Component for monitoring database connection pool metrics and health.
 */
@Component
public class DatabaseMetrics implements HealthIndicator {

    private final MeterRegistry meterRegistry;
    private final DataSource dataSource;
    private HikariPoolMXBean hikariPoolMXBean;

    public DatabaseMetrics(MeterRegistry meterRegistry, DataSource dataSource) {
        this.meterRegistry = meterRegistry;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initializeMetrics() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            this.hikariPoolMXBean = hikariDataSource.getHikariPoolMXBean();
            
            if (hikariPoolMXBean != null) {
                registerHikariMetrics();
            }
        }
    }

    private void registerHikariMetrics() {
        // Active connections
        Gauge.builder("cifixer.database.connections.active", this, DatabaseMetrics::getActiveConnections)
                .description("Number of active database connections")
                .register(meterRegistry);

        // Idle connections
        Gauge.builder("cifixer.database.connections.idle", this, DatabaseMetrics::getIdleConnections)
                .description("Number of idle database connections")
                .register(meterRegistry);

        // Total connections
        Gauge.builder("cifixer.database.connections.total", this, DatabaseMetrics::getTotalConnections)
                .description("Total number of database connections")
                .register(meterRegistry);

        // Threads awaiting connection
        Gauge.builder("cifixer.database.connections.pending", this, DatabaseMetrics::getThreadsAwaitingConnection)
                .description("Number of threads awaiting database connections")
                .register(meterRegistry);
    }

    public int getActiveConnections() {
        return hikariPoolMXBean != null ? hikariPoolMXBean.getActiveConnections() : 0;
    }

    public int getIdleConnections() {
        return hikariPoolMXBean != null ? hikariPoolMXBean.getIdleConnections() : 0;
    }

    public int getTotalConnections() {
        return hikariPoolMXBean != null ? hikariPoolMXBean.getTotalConnections() : 0;
    }

    public int getThreadsAwaitingConnection() {
        return hikariPoolMXBean != null ? hikariPoolMXBean.getThreadsAwaitingConnection() : 0;
    }



    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        
        try {
            if (hikariPoolMXBean != null) {
                int activeConnections = getActiveConnections();
                int totalConnections = getTotalConnections();
                int threadsAwaiting = getThreadsAwaitingConnection();
                
                builder.withDetail("activeConnections", activeConnections)
                       .withDetail("idleConnections", getIdleConnections())
                       .withDetail("totalConnections", totalConnections)
                       .withDetail("threadsAwaitingConnection", threadsAwaiting);
                
                // Health check logic
                if (threadsAwaiting > 5) {
                    builder.down().withDetail("reason", "Too many threads awaiting connections");
                } else if (totalConnections == 0) {
                    builder.down().withDetail("reason", "No database connections available");
                } else {
                    builder.up();
                }
            } else {
                builder.unknown().withDetail("reason", "HikariCP metrics not available");
            }
        } catch (Exception e) {
            builder.down().withDetail("error", e.getMessage());
        }
        
        return builder.build();
    }
}