package com.rtd.pipeline;

import com.google.transit.realtime.GtfsRealtime.*;
import com.rtd.pipeline.serialization.ProtobufSerializer;
import org.apache.flink.core.memory.DataInputViewStreamWrapper;
import org.apache.flink.core.memory.DataOutputViewStreamWrapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Simple test to verify Protocol Buffer serialization works correctly
 * for Flink without the complexity of full pipeline execution.
 */
public class SimpleProtobufTest {
    
    public static void main(String[] args) {
        System.out.println("=== Simple Protocol Buffer Serialization Test ===");
        
        try {
            // Create a sample vehicle position protobuf message
            VehiclePosition testVehiclePos = VehiclePosition.newBuilder()
                .setVehicle(VehicleDescriptor.newBuilder()
                    .setId("TEST_VEHICLE_123")
                    .build())
                .setTrip(TripDescriptor.newBuilder()
                    .setTripId("TRIP_456")
                    .setRouteId("ROUTE_789")
                    .build())
                .setPosition(Position.newBuilder()
                    .setLatitude(39.7392f)
                    .setLongitude(-104.9903f)
                    .setBearing(90.0f)
                    .setSpeed(15.5f)
                    .build())
                .setCurrentStatus(VehiclePosition.VehicleStopStatus.IN_TRANSIT_TO)
                .build();
            
            System.out.println("✅ Created test protobuf message: " + testVehiclePos.getVehicle().getId());
            
            // Test our custom protobuf serializer
            ProtobufSerializer<VehiclePosition> serializer = new ProtobufSerializer<>(VehiclePosition.class);
            
            // Serialize
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputViewStreamWrapper output = new DataOutputViewStreamWrapper(baos);
            serializer.serialize(testVehiclePos, output);
            
            byte[] serializedData = baos.toByteArray();
            System.out.println("✅ Serialized to " + serializedData.length + " bytes");
            
            // Deserialize
            ByteArrayInputStream bais = new ByteArrayInputStream(serializedData);
            DataInputViewStreamWrapper input = new DataInputViewStreamWrapper(bais);
            VehiclePosition deserializedPos = serializer.deserialize(input);
            
            System.out.println("✅ Deserialized vehicle: " + deserializedPos.getVehicle().getId());
            
            // Verify the data matches
            boolean vehicleIdMatch = testVehiclePos.getVehicle().getId().equals(deserializedPos.getVehicle().getId());
            boolean routeIdMatch = testVehiclePos.getTrip().getRouteId().equals(deserializedPos.getTrip().getRouteId());
            boolean latMatch = Math.abs(testVehiclePos.getPosition().getLatitude() - deserializedPos.getPosition().getLatitude()) < 0.001;
            boolean lngMatch = Math.abs(testVehiclePos.getPosition().getLongitude() - deserializedPos.getPosition().getLongitude()) < 0.001;
            
            System.out.println("=== Verification Results ===");
            System.out.println("Vehicle ID match: " + vehicleIdMatch);
            System.out.println("Route ID match: " + routeIdMatch); 
            System.out.println("Latitude match: " + latMatch + " (" + testVehiclePos.getPosition().getLatitude() + " vs " + deserializedPos.getPosition().getLatitude() + ")");
            System.out.println("Longitude match: " + lngMatch + " (" + testVehiclePos.getPosition().getLongitude() + " vs " + deserializedPos.getPosition().getLongitude() + ")");
            
            boolean allTestsPass = vehicleIdMatch && routeIdMatch && latMatch && lngMatch;
            
            System.out.println("\n=== Final Result ===");
            if (allTestsPass) {
                System.out.println("✅ SUCCESS: Protocol Buffer serialization works perfectly!");
                System.out.println("✅ This approach should resolve Flink's SimpleUdfStreamOperatorFactory issues");
                System.out.println("✅ Native protobuf messages avoid custom class serialization problems");
            } else {
                System.out.println("❌ FAILED: Serialization/deserialization mismatch detected");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Test failed with exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}