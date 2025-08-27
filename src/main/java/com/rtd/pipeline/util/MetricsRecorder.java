package com.rtd.pipeline.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class for recording metrics to the FeedMetricsController
 * Provides async methods to record messages, connections, and errors
 */
public class MetricsRecorder {
    
    private static final Logger LOG = LoggerFactory.getLogger(MetricsRecorder.class);
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    
    private static final String METRICS_BASE_URL = "http://localhost:8080/api/metrics";
    
    /**
     * Record a message for the specified feed type
     * 
     * @param feedType The feed type (siri, lrgps, railcomm)
     * @param isConnection Whether this message represents a successful connection
     * @param isError Whether this message represents an error
     */
    public static void recordMessage(String feedType, boolean isConnection, boolean isError) {
        CompletableFuture.runAsync(() -> {
            try {
                String url = String.format("%s/%s/message?isConnection=%s&isError=%s", 
                    METRICS_BASE_URL, feedType.toLowerCase(), isConnection, isError);
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(1))
                        .build();
                
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            LOG.trace("Recorded {} metrics: connection={}, error={}", 
                                feedType, isConnection, isError);
                        } else {
                            LOG.debug("Failed to record {} metrics: HTTP {}", 
                                feedType, response.statusCode());
                        }
                    })
                    .exceptionally(ex -> {
                        LOG.trace("Error recording {} metrics: {}", feedType, ex.getMessage());
                        return null;
                    });
                
            } catch (Exception e) {
                LOG.trace("Exception recording {} metrics: {}", feedType, e.getMessage());
            }
        });
    }
    
    /**
     * Record a successful connection/message
     */
    public static void recordConnection(String feedType) {
        recordMessage(feedType, true, false);
    }
    
    /**
     * Record an error
     */
    public static void recordError(String feedType) {
        recordMessage(feedType, false, true);
    }
    
    /**
     * Record a regular message (not connection, not error)
     */
    public static void recordRegularMessage(String feedType) {
        recordMessage(feedType, false, false);
    }
    
    /**
     * Check if a message payload indicates a connection/valid data
     * This is a simple heuristic that can be overridden per feed type
     */
    public static boolean isValidConnectionData(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return false;
        }
        
        // Check for common indicators of valid transit data
        return payload.contains("vehicle") || 
               payload.contains("position") || 
               payload.contains("location") ||
               payload.contains("coordinate") ||
               payload.contains("lat") ||
               payload.contains("lon") ||
               payload.contains("gps") ||
               payload.toLowerCase().contains("siri") ||
               payload.toLowerCase().contains("train");
    }
    
    /**
     * Check if a message payload indicates an error
     */
    public static boolean isErrorData(String payload) {
        if (payload == null) {
            return true;
        }
        
        String lowercasePayload = payload.toLowerCase();
        return lowercasePayload.contains("error") ||
               lowercasePayload.contains("failed") ||
               lowercasePayload.contains("exception") ||
               lowercasePayload.contains("timeout") ||
               lowercasePayload.contains("unavailable") ||
               lowercasePayload.contains("invalid");
    }
}