package com.example.cifixer.core;

import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * SSL configuration for external API calls with proper certificate validation.
 * Provides secure HTTP clients with configurable SSL settings.
 */
@Configuration
public class SslConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(SslConfiguration.class);
    
    @Value("${security.ssl.verification.enabled:true}")
    private boolean sslVerificationEnabled;
    
    @Value("${security.ssl.trust-store.path:}")
    private String trustStorePath;
    
    @Value("${security.ssl.trust-store.password:}")
    private String trustStorePassword;
    
    @Value("${external.apis.github.timeout.connect:10000}")
    private int githubConnectTimeout;
    
    @Value("${external.apis.github.timeout.read:30000}")
    private int githubReadTimeout;
    
    @Value("${external.apis.llm.timeout.connect:5000}")
    private int llmConnectTimeout;
    
    @Value("${external.apis.llm.timeout.read:120000}")
    private int llmReadTimeout;
    
    private final SecretManager secretManager;
    
    public SslConfiguration(SecretManager secretManager) {
        this.secretManager = secretManager;
    }
    
    /**
     * Creates a secure OkHttpClient for GitHub API calls.
     *
     * @return Configured OkHttpClient for GitHub
     */
    @Bean("githubHttpClient")
    public OkHttpClient githubHttpClient() {
        return createSecureHttpClient(
            Duration.ofMillis(githubConnectTimeout),
            Duration.ofMillis(githubReadTimeout),
            "GitHub API"
        );
    }
    
    /**
     * Creates a secure OkHttpClient for LLM API calls.
     *
     * @return Configured OkHttpClient for LLM
     */
    @Bean("llmHttpClient")
    public OkHttpClient llmHttpClient() {
        return createSecureHttpClient(
            Duration.ofMillis(llmConnectTimeout),
            Duration.ofMillis(llmReadTimeout),
            "LLM API"
        );
    }
    
    /**
     * Creates a secure HTTP client with SSL validation.
     *
     * @param connectTimeout Connection timeout
     * @param readTimeout Read timeout
     * @param clientName Name for logging
     * @return Configured OkHttpClient
     */
    private OkHttpClient createSecureHttpClient(Duration connectTimeout, Duration readTimeout, String clientName) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .readTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .writeTimeout(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true);
        
        if (sslVerificationEnabled) {
            configureSslValidation(builder, clientName);
        } else {
            logger.warn("SSL verification is DISABLED for {} - this is not recommended for production", clientName);
            configureInsecureSsl(builder);
        }
        
        return builder.build();
    }
    
    /**
     * Configures proper SSL validation with optional custom trust store.
     *
     * @param builder OkHttpClient builder
     * @param clientName Name for logging
     */
    private void configureSslValidation(OkHttpClient.Builder builder, String clientName) {
        try {
            // Use custom trust store if configured
            if (StringUtils.hasText(trustStorePath)) {
                TrustManager[] trustManagers = createCustomTrustManagers();
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustManagers, null);
                
                builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0]);
                logger.info("Configured {} with custom trust store: {}", clientName, trustStorePath);
            } else {
                // Use system default trust store
                logger.info("Configured {} with system default SSL validation", clientName);
            }
            
            // Add hostname verification
            builder.hostnameVerifier(createSecureHostnameVerifier());
            
        } catch (Exception e) {
            logger.error("Failed to configure SSL for {}: {}", clientName, e.getMessage());
            throw new IllegalStateException("SSL configuration failed", e);
        }
    }
    
    /**
     * Configures insecure SSL (for development only).
     *
     * @param builder OkHttpClient builder
     */
    private void configureInsecureSsl(OkHttpClient.Builder builder) {
        try {
            // Create trust manager that accepts all certificates
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        // Accept all client certificates
                    }
                    
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        // Accept all server certificates
                    }
                    
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[]{};
                    }
                }
            };
            
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            
            builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);
            
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure insecure SSL", e);
        }
    }
    
    /**
     * Creates custom trust managers from configured trust store.
     *
     * @return Array of trust managers
     */
    private TrustManager[] createCustomTrustManagers() 
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        
        try (FileInputStream trustStoreStream = new FileInputStream(trustStorePath)) {
            char[] password = StringUtils.hasText(trustStorePassword) ? 
                trustStorePassword.toCharArray() : null;
            trustStore.load(trustStoreStream, password);
        }
        
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        
        return trustManagerFactory.getTrustManagers();
    }
    
    /**
     * Creates a secure hostname verifier.
     *
     * @return HostnameVerifier that performs proper verification
     */
    private HostnameVerifier createSecureHostnameVerifier() {
        return new HostnameVerifier() {
            private final HostnameVerifier defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
            
            @Override
            public boolean verify(String hostname, SSLSession session) {
                // Use default hostname verification
                boolean result = defaultVerifier.verify(hostname, session);
                
                if (!result) {
                    logger.warn("Hostname verification failed for: {}", hostname);
                }
                
                return result;
            }
        };
    }
    
    /**
     * Validates SSL configuration on startup.
     *
     * @return true if SSL configuration is valid
     */
    public boolean validateSslConfiguration() {
        try {
            if (StringUtils.hasText(trustStorePath)) {
                // Validate trust store exists and is readable
                KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                try (FileInputStream trustStoreStream = new FileInputStream(trustStorePath)) {
                    char[] password = StringUtils.hasText(trustStorePassword) ? 
                        trustStorePassword.toCharArray() : null;
                    trustStore.load(trustStoreStream, password);
                }
                logger.info("SSL trust store validation successful: {}", trustStorePath);
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("SSL configuration validation failed: {}", e.getMessage());
            return false;
        }
    }
}