package com.rtd.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtd.pipeline.testutils.RailCommMockData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Rail Communication Pipeline Tests")
class RailCommPipelineTest {
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }
    
    @Test
    @DisplayName("Should parse single train on-time message")
    void testSingleTrainOnTimeMessage() throws Exception {
        String jsonMessage = RailCommMockData.loadSingleTrainOnTime();
        
        JsonNode rootNode = objectMapper.readTree(jsonMessage);
        
        assertThat(rootNode.has("msg_time")).isTrue();
        assertThat(rootNode.has("trains")).isTrue();
        assertThat(rootNode.get("trains").isArray()).isTrue();
        assertThat(rootNode.get("trains")).hasSize(1);
        
        JsonNode train = rootNode.get("trains").get(0);
        assertThat(train.get("train").asText()).isEqualTo("35");
        assertThat(train.get("schedule_adherence").asText()).isEqualTo("0");
        assertThat(train.get("direction").asText()).isEqualTo("South");
        assertThat(train.get("destination_name").asText()).isEqualTo("RIDGEGATE");
    }
    
    @Test
    @DisplayName("Should parse single train delayed message")
    void testSingleTrainDelayedMessage() throws Exception {
        String jsonMessage = RailCommMockData.loadSingleTrainDelayed();
        
        JsonNode rootNode = objectMapper.readTree(jsonMessage);
        JsonNode train = rootNode.get("trains").get(0);
        
        assertThat(train.get("train").asText()).isEqualTo("72");
        assertThat(train.get("schedule_adherence").asInt()).isEqualTo(-120);
        assertThat(train.get("destination_name").asText()).isEqualTo("JEFF");
    }
    
    @Test
    @DisplayName("Should parse multiple trains message")
    void testMultipleTrainsMessage() throws Exception {
        String jsonMessage = RailCommMockData.loadMultipleTrains();
        
        JsonNode rootNode = objectMapper.readTree(jsonMessage);
        
        assertThat(rootNode.get("trains")).hasSize(3);
        
        JsonNode firstTrain = rootNode.get("trains").get(0);
        JsonNode secondTrain = rootNode.get("trains").get(1);
        JsonNode thirdTrain = rootNode.get("trains").get(2);
        
        assertThat(firstTrain.get("train").asText()).isEqualTo("35");
        assertThat(secondTrain.get("train").asText()).isEqualTo("78");
        assertThat(thirdTrain.get("train").asText()).isEqualTo("23");
        
        assertThat(firstTrain.get("direction").asText()).isEqualTo("South");
        assertThat(secondTrain.get("direction").asText()).isEqualTo("South");
        assertThat(thirdTrain.get("direction").asText()).isEqualTo("North");
    }
    
    @Test
    @DisplayName("Should handle minimal train data")
    void testMinimalTrainData() throws Exception {
        String jsonMessage = RailCommMockData.loadMinimalTrainData();
        
        JsonNode rootNode = objectMapper.readTree(jsonMessage);
        JsonNode train = rootNode.get("trains").get(0);
        
        assertThat(train.get("train").asText()).isEqualTo("5");
        assertThat(train.get("source").asText()).isEqualTo("TIMER");
        assertThat(train.has("service_date")).isFalse();
        assertThat(train.has("destination_name")).isFalse();
    }
    
    @Test
    @DisplayName("Should handle empty trains array")
    void testEmptyTrainsArray() throws Exception {
        String jsonMessage = RailCommMockData.loadEmptyTrains();
        
        JsonNode rootNode = objectMapper.readTree(jsonMessage);
        
        assertThat(rootNode.has("msg_time")).isTrue();
        assertThat(rootNode.get("trains").isArray()).isTrue();
        assertThat(rootNode.get("trains")).isEmpty();
    }
    
    @Test
    @DisplayName("Should validate train consist structure")
    void testTrainConsistStructure() throws Exception {
        String jsonMessage = RailCommMockData.loadSingleTrainOnTime();
        
        JsonNode rootNode = objectMapper.readTree(jsonMessage);
        JsonNode train = rootNode.get("trains").get(0);
        JsonNode trainConsist = train.get("train_consist");
        
        assertThat(trainConsist.isArray()).isTrue();
        assertThat(trainConsist).hasSize(2);
        
        JsonNode firstCar = trainConsist.get(0);
        assertThat(firstCar.get("car").asText()).isEqualTo("129");
        assertThat(firstCar.get("order").asText()).isEqualTo("1");
        
        JsonNode secondCar = trainConsist.get(1);
        assertThat(secondCar.get("car").asText()).isEqualTo("148");
        assertThat(secondCar.get("order").asText()).isEqualTo("2");
    }
    
    @Test
    @DisplayName("Should validate location data")
    void testLocationData() throws Exception {
        String jsonMessage = RailCommMockData.loadSingleTrainOnTime();
        
        JsonNode rootNode = objectMapper.readTree(jsonMessage);
        JsonNode train = rootNode.get("trains").get(0);
        
        assertThat(train.has("latitude")).isTrue();
        assertThat(train.has("longitude")).isTrue();
        assertThat(train.has("position")).isTrue();
        
        double latitude = train.get("latitude").asDouble();
        double longitude = train.get("longitude").asDouble();
        
        assertThat(latitude).isBetween(39.0, 40.0);
        assertThat(longitude).isBetween(-106.0, -104.0);
    }
    
    @Test
    @DisplayName("Should create custom train message")
    void testCustomTrainMessage() throws Exception {
        String customMessage = RailCommMockData.createCustomTrainMessage(
            "TEST-001", "99", "North", "39.7392", "-104.9903", "30", "ON_TIME"
        );
        
        JsonNode rootNode = objectMapper.readTree(customMessage);
        JsonNode train = rootNode.get("trains").get(0);
        
        assertThat(train.get("train").asText()).isEqualTo("TEST-001");
        assertThat(train.get("run").asText()).isEqualTo("99");
        assertThat(train.get("direction").asText()).isEqualTo("North");
        assertThat(train.get("schedule_adherence").asText()).isEqualTo("30");
    }
}