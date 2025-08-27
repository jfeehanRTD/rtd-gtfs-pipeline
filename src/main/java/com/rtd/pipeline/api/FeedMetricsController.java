package com.rtd.pipeline.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Feed Metrics Controller - Provides real-time metrics for SIRI, LRGPS, and RailComm feeds
 * Tracks messages per minute, connections, errors, and feed health for admin dashboard
 */
@RestController
@RequestMapping("/api/metrics")
@CrossOrigin(origins = "http://localhost:3000")
public class FeedMetricsController {
    
    private static final Logger logger = LoggerFactory.getLogger(FeedMetricsController.class);
    
    // Feed metrics storage (thread-safe)
    private final Map<String, FeedMetrics> feedMetricsMap = new ConcurrentHashMap<>();
    private final LocalDateTime startTime = LocalDateTime.now();
    
    // Initialize metrics for all feeds
    public FeedMetricsController() {
        feedMetricsMap.put("siri", new FeedMetrics("SIRI Bus Feed"));
        feedMetricsMap.put("lrgps", new FeedMetrics("LRGPS Light Rail Feed")); 
        feedMetricsMap.put("railcomm", new FeedMetrics("RailComm SCADA Feed"));
    }
    
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllFeedMetrics() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> feeds = new HashMap<>();
        
        for (Map.Entry<String, FeedMetrics> entry : feedMetricsMap.entrySet()) {
            feeds.put(entry.getKey(), entry.getValue().toMap());
        }
        
        response.put("feeds", feeds);
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        response.put("uptime_ms", java.time.Duration.between(startTime, LocalDateTime.now()).toMillis());
        
        logger.debug("All feed metrics requested");
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{feedType}")
    public ResponseEntity<Map<String, Object>> getFeedMetrics(@PathVariable String feedType) {
        FeedMetrics metrics = feedMetricsMap.get(feedType.toLowerCase());
        if (metrics == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> response = metrics.toMap();
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        logger.debug("Metrics requested for feed: {}", feedType);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{feedType}/message")
    public ResponseEntity<Void> recordMessage(
            @PathVariable String feedType,
            @RequestParam(defaultValue = "false") boolean isConnection,
            @RequestParam(defaultValue = "false") boolean isError) {
        
        FeedMetrics metrics = feedMetricsMap.get(feedType.toLowerCase());
        if (metrics != null) {
            metrics.recordMessage(isConnection, isError);
            logger.trace("Message recorded for {}: connection={}, error={}", feedType, isConnection, isError);
        }
        
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/{feedType}/reset")
    public ResponseEntity<Void> resetFeedMetrics(@PathVariable String feedType) {
        FeedMetrics metrics = feedMetricsMap.get(feedType.toLowerCase());
        if (metrics != null) {
            metrics.reset();
            logger.info("Metrics reset for feed: {}", feedType);
        }
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Thread-safe metrics tracking for individual feeds
     */
    public static class FeedMetrics {
        private final String feedName;
        private final LocalDateTime createdAt;
        private final AtomicLong totalMessages = new AtomicLong(0);
        private final AtomicLong totalConnections = new AtomicLong(0);
        private final AtomicLong totalErrors = new AtomicLong(0);
        
        // 5-second interval tracking
        private final AtomicInteger messagesLast5s = new AtomicInteger(0);
        private final AtomicInteger connectionsLast5s = new AtomicInteger(0);
        private final AtomicInteger errorsLast5s = new AtomicInteger(0);
        private volatile LocalDateTime lastIntervalReset = LocalDateTime.now();
        
        // Status tracking
        private volatile LocalDateTime lastMessage = null;
        private volatile String healthStatus = "waiting"; // waiting, healthy, warning, error
        
        public FeedMetrics(String feedName) {
            this.feedName = feedName;
            this.createdAt = LocalDateTime.now();
        }
        
        public synchronized void recordMessage(boolean isConnection, boolean isError) {
            totalMessages.incrementAndGet();
            messagesLast5s.incrementAndGet();
            lastMessage = LocalDateTime.now();
            
            if (isConnection) {
                totalConnections.incrementAndGet();
                connectionsLast5s.incrementAndGet();
            }
            
            if (isError) {
                totalErrors.incrementAndGet();
                errorsLast5s.incrementAndGet();
                healthStatus = "error";
            } else {
                // Update health status based on recent activity
                if (totalMessages.get() > 0) {
                    long errorRate = (totalErrors.get() * 100) / totalMessages.get();
                    if (errorRate > 10) {
                        healthStatus = "warning";
                    } else {
                        healthStatus = "healthy";
                    }
                }
            }
            
            // Reset 5-second counters if needed
            if (java.time.Duration.between(lastIntervalReset, LocalDateTime.now()).getSeconds() >= 5) {
                resetInterval();
            }
        }
        
        private synchronized void resetInterval() {
            messagesLast5s.set(0);
            connectionsLast5s.set(0);
            errorsLast5s.set(0);
            lastIntervalReset = LocalDateTime.now();
        }
        
        public void reset() {
            totalMessages.set(0);
            totalConnections.set(0);
            totalErrors.set(0);
            resetInterval();
            healthStatus = "waiting";
            lastMessage = null;
        }
        
        public Map<String, Object> toMap() {
            LocalDateTime now = LocalDateTime.now();
            long uptimeSeconds = java.time.Duration.between(createdAt, now).getSeconds();
            long avgMessagesPerMin = uptimeSeconds > 0 ? (totalMessages.get() * 60) / uptimeSeconds : 0;
            long avgConnectionsPerMin = uptimeSeconds > 0 ? (totalConnections.get() * 60) / uptimeSeconds : 0;
            
            // Auto-reset 5s counters if interval has passed
            if (java.time.Duration.between(lastIntervalReset, now).getSeconds() >= 5) {
                resetInterval();
            }
            
            Map<String, Object> map = new HashMap<>();
            map.put("feed_name", feedName);
            map.put("health_status", healthStatus);
            map.put("last_message", lastMessage != null ? lastMessage.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
            
            // 5-second interval metrics
            Map<String, Object> interval = new HashMap<>();
            interval.put("messages", messagesLast5s.get());
            interval.put("connections", connectionsLast5s.get());
            interval.put("errors", errorsLast5s.get());
            interval.put("interval_seconds", 5);
            map.put("last_5s", interval);
            
            // Running totals
            Map<String, Object> totals = new HashMap<>();
            totals.put("messages", totalMessages.get());
            totals.put("connections", totalConnections.get());
            totals.put("errors", totalErrors.get());
            totals.put("uptime_seconds", uptimeSeconds);
            totals.put("avg_messages_per_min", avgMessagesPerMin);
            totals.put("avg_connections_per_min", avgConnectionsPerMin);
            map.put("totals", totals);
            
            // Calculated rates
            if (totalMessages.get() > 0) {
                double connectionRate = (double) totalConnections.get() * 100 / totalMessages.get();
                double errorRate = (double) totalErrors.get() * 100 / totalMessages.get();
                
                Map<String, Object> rates = new HashMap<>();
                rates.put("connection_rate_percent", Math.round(connectionRate * 10.0) / 10.0);
                rates.put("error_rate_percent", Math.round(errorRate * 100.0) / 100.0);
                map.put("rates", rates);
            }
            
            return map;
        }
    }
}