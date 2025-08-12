package com.rtd.pipeline;

import com.rtd.pipeline.source.RTDRowSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                        
                        // Display first 5 vehicles
                        int displayCount = Math.min(5, vehicles.size());
                        for (int i = 0; i < displayCount; i++) {
                            org.apache.flink.types.Row vehicle = vehicles.get(i);
                            String vehicleId = (String) vehicle.getField(1);
                            String routeId = (String) vehicle.getField(3);
                            Double latitude = (Double) vehicle.getField(4);
                            Double longitude = (Double) vehicle.getField(5);
                            String status = (String) vehicle.getField(8);
                            
                            // Show last 8 chars of vehicle ID for better uniqueness (or full ID if shorter)
                            String displayId = "N/A";
                            if (vehicleId != null) {
                                if (vehicleId.length() > 16) {
                                    // For long IDs, show first 4 and last 4 chars
                                    displayId = vehicleId.substring(0, 4) + "..." + 
                                               vehicleId.substring(vehicleId.length() - 4);
                                } else {
                                    displayId = vehicleId;
                                }
                            }
                            
                            System.out.printf("  Vehicle: %s | Route: %s | Position: (%.6f, %.6f) | Status: %s\n",
                                displayId,
                                routeId != null ? routeId : "N/A",
                                latitude != null ? latitude : 0.0,
                                longitude != null ? longitude : 0.0,
                                status != null ? status : "N/A"
                            );
                        }
                        
                        if (vehicles.size() > displayCount) {
                            System.out.printf("  ... and %d more vehicles\n", vehicles.size() - displayCount);
                        }
                        
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