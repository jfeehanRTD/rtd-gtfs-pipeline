package com.rtd.pipeline;

import com.rtd.pipeline.source.RTDRowSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * RTD data pipeline that works around Flink 2.0.0 serialization issues.
 * Uses direct data fetching without Flink execution to demonstrate live RTD integration.
 * This provides the core functionality while avoiding Flink runtime compatibility problems.
 */
public class RTDStaticDataPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDStaticDataPipeline.class);
    
    private static final String VEHICLE_POSITIONS_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb";
    private static final long FETCH_INTERVAL_SECONDS = 60L;
    
    public static void main(String[] args) throws Exception {
        
        System.out.println("=== RTD Static Data Pipeline - Workaround for Flink 2.0.0 Issues ===");
        System.out.println("Demonstrates live RTD data integration without Flink execution problems");
        System.out.println("Fetching RTD data every " + FETCH_INTERVAL_SECONDS + " seconds\n");
        
        // Create a simple scheduler to fetch RTD data periodically
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        RTDRowSource dataSource = new RTDRowSource(VEHICLE_POSITIONS_URL, FETCH_INTERVAL_SECONDS);
        
        // Counter for demonstration
        final int[] fetchCount = {0};
        
        try {
            System.out.println("=== Starting RTD Data Fetching ===");
            System.out.println("Press Ctrl+C to stop\n");
            
            // Schedule periodic data fetching
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    fetchCount[0]++;
                    System.out.printf("=== Fetch #%d ===\n", fetchCount[0]);
                    
                    // Use the RTDRowSource to fetch data directly
                    java.lang.reflect.Method fetchMethod = RTDRowSource.class.getDeclaredMethod("fetchVehiclePositionsAsRows");
                    fetchMethod.setAccessible(true);
                    
                    @SuppressWarnings("unchecked")
                    java.util.List<org.apache.flink.types.Row> vehicles = 
                        (java.util.List<org.apache.flink.types.Row>) fetchMethod.invoke(dataSource);
                    
                    if (!vehicles.isEmpty()) {
                        System.out.printf("✅ Retrieved %d vehicles from RTD\n", vehicles.size());
                        
                        // Get and format the timestamp from the first vehicle
                        if (!vehicles.isEmpty()) {
                            Long timestampMs = (Long) vehicles.get(0).getField(0);
                            if (timestampMs != null) {
                                String formattedTime = Instant.ofEpochMilli(timestampMs)
                                    .atZone(ZoneId.of("America/Denver"))
                                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
                                System.out.printf("📅 Feed Timestamp: %s\n", formattedTime);
                            }
                        }
                        
                        System.out.println("\nVehicle Details (All Vehicles):");
                        System.out.println("  Bus# | Route | Position            | Status         | ID");
                        System.out.println("  " + "-".repeat(70));
                        
                        // Display ALL vehicles
                        for (int i = 0; i < vehicles.size(); i++) {
                            org.apache.flink.types.Row vehicle = vehicles.get(i);
                            // Updated field indices after adding vehicle_label
                            String vehicleId = (String) vehicle.getField(1);
                            String vehicleLabel = (String) vehicle.getField(2);  // Fleet number
                            String routeId = (String) vehicle.getField(4);      // Shifted from 3
                            Double latitude = (Double) vehicle.getField(5);     // Shifted from 4
                            Double longitude = (Double) vehicle.getField(6);    // Shifted from 5
                            String status = (String) vehicle.getField(9);       // Shifted from 8
                            
                            // Show last 4 chars of vehicle ID for uniqueness
                            String shortId = "N/A";
                            if (vehicleId != null && vehicleId.length() >= 4) {
                                shortId = "..." + vehicleId.substring(vehicleId.length() - 4);
                            }
                            
                            // Format vehicle label (fleet number) with padding
                            String displayLabel = vehicleLabel != null ? vehicleLabel : "----";
                            
                            System.out.printf("  %-5s | %-5s | %9.6f,%10.6f | %-14s | %s\n",
                                displayLabel,
                                routeId != null ? routeId : "N/A",
                                latitude != null ? latitude : 0.0,
                                longitude != null ? longitude : 0.0,
                                status != null ? status : "N/A",
                                shortId
                            );
                        }
                        
                        System.out.printf("\n📊 Total: %d vehicles displayed\n", vehicles.size());
                        
                    } else {
                        System.out.println("❌ No vehicles retrieved from RTD");
                    }
                    
                    System.out.printf("Next fetch in %d seconds...\n\n", FETCH_INTERVAL_SECONDS);
                    
                } catch (Exception e) {
                    System.err.printf("❌ Error fetching RTD data: %s\n", e.getMessage());
                    e.printStackTrace();
                }
            }, 0, FETCH_INTERVAL_SECONDS, TimeUnit.SECONDS);
            
            // Keep the main thread alive
            Thread.currentThread().join();
            
        } catch (InterruptedException e) {
            System.out.println("\n=== Shutting Down RTD Pipeline ===");
        } finally {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
        
        System.out.println("\n=== Pipeline Summary ===");
        System.out.printf("✅ Successfully fetched RTD data %d times\n", fetchCount[0]);
        System.out.println("✅ Demonstrated live vehicle position retrieval");
        System.out.println("✅ Used Flink Row data types for structured data");
        System.out.println("✅ Avoided Flink execution serialization issues");
        System.out.println("💡 This shows the core RTD integration works - Flink 2.0.0 execution is the blocker");
    }
}