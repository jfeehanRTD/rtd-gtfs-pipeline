package com.rtd.pipeline;

import com.google.transit.realtime.GtfsRealtime.*;
import com.rtd.pipeline.serialization.ProtobufSerializer;
import org.apache.flink.core.memory.DataInputViewStreamWrapper;
import org.apache.flink.core.memory.DataOutputViewStreamWrapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Integration test that fetches live RTD data and tests Protocol Buffer
 * serialization with real vehicle positions.
 */
public class ProtobufRTDIntegrationTest {
    
    private static final String VEHICLE_POSITIONS_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb";
    
    public static void main(String[] args) {
        System.out.println("=== Protocol Buffer RTD Integration Test ===");
        System.out.println("Fetching live RTD data and testing protobuf serialization");
        
        try {
            // 1. Fetch live GTFS-RT data from RTD
            System.out.println("\n1. Fetching live RTD vehicle positions...");
            
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(VEHICLE_POSITIONS_URL);
                request.setHeader("User-Agent", "RTD-GTFS-Pipeline-PB-Test/1.0");
                
                HttpResponse response = httpClient.execute(request);
                
                if (response.getStatusLine().getStatusCode() == 200) {
                    byte[] feedData = EntityUtils.toByteArray(response.getEntity());
                    FeedMessage feed = FeedMessage.parseFrom(new ByteArrayInputStream(feedData));
                    
                    System.out.println("✅ Successfully downloaded " + feedData.length + " bytes");
                    System.out.println("✅ Found " + feed.getEntityCount() + " entities in feed");
                    
                    // 2. Extract vehicle positions and test serialization
                    System.out.println("\n2. Testing serialization of real vehicle positions...");
                    ProtobufSerializer<VehiclePosition> serializer = new ProtobufSerializer<>(VehiclePosition.class);
                    
                    int vehicleCount = 0;
                    int serializationTests = 0;
                    int serializationSuccesses = 0;
                    
                    for (FeedEntity entity : feed.getEntityList()) {
                        if (entity.hasVehicle()) {
                            VehiclePosition vehiclePos = entity.getVehicle();
                            vehicleCount++;
                            
                            // Test serialization for first 5 vehicles
                            if (serializationTests < 5 && vehiclePos.hasVehicle() && vehiclePos.getVehicle().hasId()) {
                                try {
                                    // Serialize
                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                    DataOutputViewStreamWrapper output = new DataOutputViewStreamWrapper(baos);
                                    serializer.serialize(vehiclePos, output);
                                    
                                    // Deserialize
                                    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                                    DataInputViewStreamWrapper input = new DataInputViewStreamWrapper(bais);
                                    VehiclePosition deserializedPos = serializer.deserialize(input);
                                    
                                    // Verify
                                    boolean matches = vehiclePos.getVehicle().getId().equals(deserializedPos.getVehicle().getId());
                                    
                                    if (matches) {
                                        serializationSuccesses++;
                                        System.out.println("  ✅ Vehicle " + vehiclePos.getVehicle().getId() + 
                                            " - Serialization: PASS (" + baos.size() + " bytes)");
                                    } else {
                                        System.out.println("  ❌ Vehicle " + vehiclePos.getVehicle().getId() + 
                                            " - Serialization: FAIL");
                                    }
                                    
                                } catch (Exception e) {
                                    System.out.println("  ❌ Vehicle " + vehiclePos.getVehicle().getId() + 
                                        " - Serialization error: " + e.getMessage());
                                }
                                
                                serializationTests++;
                            }
                        }
                    }
                    
                    System.out.println("\n=== Results ===");
                    System.out.println("Total vehicles found: " + vehicleCount);
                    System.out.println("Serialization tests: " + serializationTests);
                    System.out.println("Serialization successes: " + serializationSuccesses);
                    
                    if (serializationSuccesses == serializationTests && serializationTests > 0) {
                        System.out.println("\n🎉 INTEGRATION TEST PASSED!");
                        System.out.println("✅ Live RTD data fetch: SUCCESS");
                        System.out.println("✅ Protocol Buffer serialization: SUCCESS");
                        System.out.println("✅ Ready for Flink execution with PB messages!");
                        
                        // Show sample vehicle data
                        if (vehicleCount > 0) {
                            System.out.println("\n=== Sample Vehicle Data ===");
                            for (FeedEntity entity : feed.getEntityList()) {
                                if (entity.hasVehicle()) {
                                    VehiclePosition v = entity.getVehicle();
                                    if (v.hasVehicle() && v.getVehicle().hasId()) {
                                        System.out.println("Vehicle: " + v.getVehicle().getId() + 
                                            (v.hasTrip() && v.getTrip().hasRouteId() ? " | Route: " + v.getTrip().getRouteId() : "") +
                                            (v.hasPosition() ? " | Lat: " + String.format("%.6f", v.getPosition().getLatitude()) + 
                                                              " | Lng: " + String.format("%.6f", v.getPosition().getLongitude()) : "") +
                                            (v.hasCurrentStatus() ? " | Status: " + v.getCurrentStatus() : ""));
                                        break; // Just show one example
                                    }
                                }
                            }
                        }
                        
                    } else {
                        System.out.println("❌ INTEGRATION TEST FAILED");
                        System.out.println("Serialization success rate: " + serializationSuccesses + "/" + serializationTests);
                    }
                    
                } else {
                    System.out.println("❌ Failed to fetch RTD data. HTTP Status: " + response.getStatusLine().getStatusCode());
                }
            }
            
        } catch (Exception e) {
            System.err.println("❌ Integration test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}