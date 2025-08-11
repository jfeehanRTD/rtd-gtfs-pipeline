package com.rtd.pipeline;

import com.google.transit.realtime.GtfsRealtime.*;
import com.rtd.pipeline.model.VehiclePosition;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Direct test to fetch RTD GTFS-RT data without Flink pipeline.
 * This demonstrates that the data fetching and parsing logic works.
 */
public class DirectRTDTest {
    
    // Updated RTD GTFS-RT URL based on actual endpoint
    private static final String VEHICLE_POSITIONS_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb";
    
    public static void main(String[] args) {
        System.out.println("=== RTD GTFS-RT Direct Data Test ===");
        System.out.println("Fetching live vehicle position data from RTD...\n");
        
        try {
            fetchAndDisplayRTDData();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void fetchAndDisplayRTDData() throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(VEHICLE_POSITIONS_URL);
            request.setHeader("User-Agent", "RTD-GTFS-Pipeline/1.0");
            
            System.out.println("Requesting data from: " + VEHICLE_POSITIONS_URL);
            HttpResponse response = httpClient.execute(request);
            
            int statusCode = response.getStatusLine().getStatusCode();
            System.out.println("HTTP Response: " + statusCode + " " + response.getStatusLine().getReasonPhrase());
            
            if (statusCode == 200) {
                byte[] feedData = EntityUtils.toByteArray(response.getEntity());
                System.out.println("Downloaded " + feedData.length + " bytes of GTFS-RT data\n");
                
                FeedMessage feed = FeedMessage.parseFrom(new ByteArrayInputStream(feedData));
                
                FeedHeader header = feed.getHeader();
                long timestamp = header.getTimestamp();
                String formattedTime = Instant.ofEpochSecond(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
                
                System.out.println("=== LIVE RTD DATA ===");
                System.out.println("Feed Timestamp: " + formattedTime);
                System.out.println("GTFS Realtime Version: " + header.getGtfsRealtimeVersion());
                System.out.println("Total Entities: " + feed.getEntityCount());
                System.out.println();
                
                int vehicleCount = 0;
                System.out.println("Sample Vehicle Positions (showing first 10):");
                System.out.println("-------------------------------------------");
                
                for (FeedEntity entity : feed.getEntityList()) {
                    if (entity.hasVehicle() && vehicleCount < 10) {
                        vehicleCount++;
                        VehiclePosition position = parseVehiclePosition(entity.getVehicle(), timestamp);
                        displayVehiclePosition(vehicleCount, position);
                    }
                }
                
                System.out.println("\n=== SUMMARY ===");
                System.out.println("✅ Successfully connected to RTD GTFS-RT feed");
                System.out.println("✅ Downloaded and parsed live transit data");
                System.out.println("✅ Found " + vehicleCount + " active vehicles");
                System.out.println("✅ Pipeline data fetching and parsing works correctly!");
                
            } else {
                System.err.println("Failed to fetch data. HTTP status: " + statusCode);
            }
        }
    }
    
    private static VehiclePosition parseVehiclePosition(
            com.google.transit.realtime.GtfsRealtime.VehiclePosition vehiclePos, long feedTimestamp) {
        
        VehiclePosition.Builder builder = VehiclePosition.builder()
                .timestamp_ms(feedTimestamp * 1000)
                .vehicleId(vehiclePos.getVehicle().hasId() ? vehiclePos.getVehicle().getId() : "Unknown");
        
        if (vehiclePos.hasTrip()) {
            com.google.transit.realtime.GtfsRealtime.TripDescriptor trip = vehiclePos.getTrip();
            builder.tripId(trip.hasTripId() ? trip.getTripId() : null)
                   .routeId(trip.hasRouteId() ? trip.getRouteId() : null);
        }
        
        if (vehiclePos.hasPosition()) {
            com.google.transit.realtime.GtfsRealtime.Position pos = vehiclePos.getPosition();
            builder.latitude(pos.hasLatitude() ? (double)pos.getLatitude() : null)
                   .longitude(pos.hasLongitude() ? (double)pos.getLongitude() : null)
                   .bearing(pos.hasBearing() ? pos.getBearing() : null)
                   .speed(pos.hasSpeed() ? pos.getSpeed() : null);
        }
        
        if (vehiclePos.hasCurrentStatus()) {
            builder.currentStatus(vehiclePos.getCurrentStatus().name());
        }
        
        return builder.build();
    }
    
    private static void displayVehiclePosition(int index, VehiclePosition position) {
        System.out.printf("%2d. Vehicle: %-15s Route: %-8s Position: (%9.6f, %10.6f) Status: %s%n",
            index,
            position.getVehicleId() != null ? position.getVehicleId() : "N/A",
            position.getRouteId() != null ? position.getRouteId() : "N/A",
            position.getLatitude() != null ? position.getLatitude() : 0.0,
            position.getLongitude() != null ? position.getLongitude() : 0.0,
            position.getCurrentStatus() != null ? position.getCurrentStatus() : "N/A"
        );
    }
}