package com.rtd.pipeline.testutils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class RailCommMockData {
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    
    public static String loadSingleTrainOnTime() {
        return loadJsonResource("mock-data/rail-comm/single_train_on_time.json");
    }
    
    public static String loadSingleTrainDelayed() {
        return loadJsonResource("mock-data/rail-comm/single_train_delayed.json");
    }
    
    public static String loadMultipleTrains() {
        return loadJsonResource("mock-data/rail-comm/multiple_trains.json");
    }
    
    public static String loadMinimalTrainData() {
        return loadJsonResource("mock-data/rail-comm/minimal_train_data.json");
    }
    
    public static String loadEmptyTrains() {
        return loadJsonResource("mock-data/rail-comm/empty_trains.json");
    }
    
    public static String loadRawSampleMessages() {
        return loadTextResource("mock-data/rail-comm/sample_json_messages.json");
    }
    
    private static String loadJsonResource(String resourcePath) {
        try (InputStream inputStream = RailCommMockData.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            
            String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            
            // Validate JSON format
            objectMapper.readTree(json);
            
            return json;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load mock data: " + resourcePath, e);
        }
    }
    
    private static String loadTextResource(String resourcePath) {
        try (InputStream inputStream = RailCommMockData.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load text resource: " + resourcePath, e);
        }
    }
    
    public static class TrainScenarios {
        public static final String ON_TIME = "ON_TIME";
        public static final String DELAYED = "DELAYED";
        public static final String EARLY = "EARLY";
        public static final String STOPPED = "STOPPED";
    }
    
    public static class TrainSources {
        public static final String TRACK_CIRCUIT = "TRACK_CIRCUIT";
        public static final String TWC = "TWC";
        public static final String TIMER = "TIMER";
    }
    
    public static class TrainDirections {
        public static final String NORTH = "North";
        public static final String SOUTH = "South";
        public static final String EAST = "East";
        public static final String WEST = "West";
    }
    
    public static String createCustomTrainMessage(String trainId, String run, String direction, 
                                                 String latitude, String longitude, 
                                                 String scheduleAdherence, String status) {
        return String.format("""
            {
              "msg_time": "2025-08-13 21:00:00",
              "trains": [
                {
                  "last_movement_time": "2025-08-13 21:00:00",
                  "service_date": "2025-08-13",
                  "run": "%s",
                  "destination_name": "TEST_DEST",
                  "destination_id": "99",
                  "trip": "1",
                  "direction": "%s",
                  "train": "%s",
                  "train_consist": [
                    {
                      "car": "100",
                      "order": "1"
                    }
                  ],
                  "source": "TRACK_CIRCUIT",
                  "position": "TEST Track",
                  "latitude": "%s",
                  "longitude": "%s",
                  "scheduled_time_to_next": "60",
                  "scheduled_stop_time": "2025-08-13 21:01:00",
                  "schedule_adherence": "%s",
                  "prev_stop": "PREV",
                  "current_stop": "CURRENT",
                  "next_stop": "NEXT"
                }
              ]
            }
            """, run, direction, trainId, latitude, longitude, scheduleAdherence);
    }
}