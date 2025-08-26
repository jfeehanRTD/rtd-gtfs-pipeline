package com.rtd.pipeline.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class HealthController {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);
    private final LocalDateTime startTime = LocalDateTime.now();
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("last_update", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        health.put("uptime_ms", java.time.Duration.between(startTime, LocalDateTime.now()).toMillis());
        health.put("service", "rtd-api-server");
        
        // TODO: Connect to real vehicle data source to get actual count
        // health.put("vehicle_count", vehicleDataService.getActiveVehicleCount());
        
        logger.debug("Health check requested");
        return ResponseEntity.ok(health);
    }
    
    @GetMapping("/vehicles")
    public ResponseEntity<Map<String, Object>> getVehicles() {
        Map<String, Object> response = new HashMap<>();
        
        // TODO: Connect to real vehicle data source
        // List<Vehicle> vehicles = vehicleDataService.getActiveVehicles();
        // response.put("vehicles", vehicles);
        response.put("vehicles", new Object[0]); // Placeholder until real data source is connected
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("total_count", 0); // TODO: Get actual count from data source
        metadata.put("last_update", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        metadata.put("source", "rtd-gtfs-rt-feed");
        response.put("metadata", metadata);
        
        logger.debug("Vehicle data requested");
        return ResponseEntity.ok(response);
    }
}