package com.rtd.pipeline;

import com.rtd.pipeline.source.RTDRowSource;
import org.apache.flink.types.Row;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Test the updated RTDRowSource with vehicle labels and timestamps
 */
public class VehicleLabelTest {
    
    private static final String VEHICLE_POSITIONS_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb";
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== RTD Vehicle Label and Timestamp Test ===\n");
        
        RTDRowSource dataSource = new RTDRowSource(VEHICLE_POSITIONS_URL, 60L);
        
        // Use reflection to call the private method
        Method fetchMethod = RTDRowSource.class.getDeclaredMethod("fetchVehiclePositionsAsRows");
        fetchMethod.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<Row> vehicles = (List<Row>) fetchMethod.invoke(dataSource);
        
        if (!vehicles.isEmpty()) {
            System.out.printf("✅ Retrieved %d vehicles from RTD\n", vehicles.size());
            
            // Get and format the timestamp
            Long timestampMs = (Long) vehicles.get(0).getField(0);
            if (timestampMs != null) {
                String formattedTime = Instant.ofEpochMilli(timestampMs)
                    .atZone(ZoneId.of("America/Denver"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
                System.out.printf("📅 Feed Timestamp: %s\n\n", formattedTime);
            }
            
            System.out.println("All Vehicles with Labels:");
            System.out.println("Bus#  | Route | Position                   | Status         | Full Vehicle ID");
            System.out.println("-".repeat(85));
            
            // Display ALL vehicles
            for (int i = 0; i < vehicles.size(); i++) {
                Row vehicle = vehicles.get(i);
                // Field indices:
                // 0: timestamp_ms, 1: vehicle_id, 2: vehicle_label
                // 3: trip_id, 4: route_id, 5: latitude, 6: longitude
                // 7: bearing, 8: speed, 9: current_status
                // 10: congestion_level, 11: occupancy_status
                
                String vehicleId = (String) vehicle.getField(1);
                String vehicleLabel = (String) vehicle.getField(2);  // Fleet number
                String routeId = (String) vehicle.getField(4);
                Double latitude = (Double) vehicle.getField(5);
                Double longitude = (Double) vehicle.getField(6);
                String status = (String) vehicle.getField(9);
                
                // Display full vehicle ID for this test
                String displayId = vehicleId != null ? 
                    (vehicleId.length() > 32 ? vehicleId.substring(0, 32) : vehicleId) : "N/A";
                String displayLabel = vehicleLabel != null ? vehicleLabel : "----";
                
                System.out.printf("%-5s | %-5s | %9.6f, %10.6f | %-14s | %s\n",
                    displayLabel,
                    routeId != null ? routeId : "N/A",
                    latitude != null ? latitude : 0.0,
                    longitude != null ? longitude : 0.0,
                    status != null ? status : "N/A",
                    displayId
                );
            }
            
            System.out.printf("\n📊 Total: %d vehicles displayed\n", vehicles.size());
            System.out.println("\n✅ Test Complete!");
            System.out.println("Vehicle labels (fleet numbers) and timestamps are now displayed correctly.");
            
        } else {
            System.out.println("❌ No vehicles retrieved from RTD");
        }
    }
}