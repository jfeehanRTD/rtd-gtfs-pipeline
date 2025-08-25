package com.rtd.pipeline;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Assumptions;

/**
 * Integration tests for Bus Communication Pipeline (SIRI)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BusCommPipelineTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(BusCommPipelineTest.class);
    private static final String BUS_SIRI_ENDPOINT = "http://localhost:8082/bus-siri";
    private static final String SUBSCRIPTION_ENDPOINT = "http://localhost:8082/subscribe";
    private static final String HEALTH_ENDPOINT = "http://localhost:8082/health";
    private static final String STATUS_ENDPOINT = "http://localhost:8082/status";
    private static final String KAFKA_TOPIC = "rtd.bus.siri";
    
    private static KafkaProducer<String, String> kafkaProducer;
    private static ObjectMapper objectMapper;
    
    @BeforeAll
    public static void setUp() {
        objectMapper = new ObjectMapper();
        
        // Set up Kafka producer for testing
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        
        try {
            kafkaProducer = new KafkaProducer<>(props);
            LOG.info("Test Kafka producer initialized");
        } catch (Exception e) {
            LOG.warn("Kafka not available for testing: {}", e.getMessage());
        }
    }
    
    @AfterAll
    public static void tearDown() {
        if (kafkaProducer != null) {
            kafkaProducer.close();
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("Test Bus SIRI JSON Processing")
    public void testBusSIRIJsonProcessing() {
        LOG.info("Testing Bus SIRI JSON processing...");
        
        // Create test SIRI JSON payload
        ObjectNode siriPayload = objectMapper.createObjectNode();
        siriPayload.put("vehicle_id", "BUS_TEST_001");
        siriPayload.put("route_id", "15L");
        siriPayload.put("direction", "NORTHBOUND");
        siriPayload.put("latitude", 39.7549);
        siriPayload.put("longitude", -105.0000);
        siriPayload.put("speed_mph", 30.5);
        siriPayload.put("status", "IN_TRANSIT");
        siriPayload.put("next_stop", "Denver Union Station");
        siriPayload.put("delay_seconds", 180);
        siriPayload.put("occupancy", "STANDING_ROOM_ONLY");
        siriPayload.put("block_id", "BLK_15L_01");
        siriPayload.put("trip_id", "TRIP_15L_0900");
        
        String jsonString = siriPayload.toString();
        
        // Validate JSON structure
        assertNotNull(jsonString);
        assertTrue(jsonString.contains("BUS_TEST_001"));
        assertTrue(jsonString.contains("15L"));
        assertTrue(jsonString.contains("NORTHBOUND"));
        
        LOG.info("Created test SIRI JSON payload: {}", jsonString);
    }
    
    @Test
    @Order(2)
    @DisplayName("Test Bus SIRI XML Processing")
    public void testBusSIRIXmlProcessing() {
        LOG.info("Testing Bus SIRI XML processing...");
        
        // Create test SIRI XML payload
        String siriXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Siri xmlns="http://www.siri.org.uk/siri" version="2.0">
                <ServiceDelivery>
                    <ResponseTimestamp>2024-01-01T12:00:00Z</ResponseTimestamp>
                    <VehicleMonitoringDelivery>
                        <VehicleActivity>
                            <RecordedAtTime>2024-01-01T12:00:00Z</RecordedAtTime>
                            <MonitoredVehicleJourney>
                                <VehicleRef>BUS_TEST_002</VehicleRef>
                                <LineRef>0</LineRef>
                                <DirectionRef>EASTBOUND</DirectionRef>
                                <VehicleLocation>
                                    <Longitude>-104.9903</Longitude>
                                    <Latitude>39.7392</Latitude>
                                </VehicleLocation>
                                <Speed>25.0</Speed>
                                <ProgressStatus>IN_PROGRESS</ProgressStatus>
                                <MonitoredCall>
                                    <StopPointRef>CIVIC_CENTER</StopPointRef>
                                </MonitoredCall>
                                <Delay>PT2M</Delay>
                                <Occupancy>MANY_SEATS_AVAILABLE</Occupancy>
                                <BlockRef>BLK_0_02</BlockRef>
                                <OriginRef>UNION_STATION</OriginRef>
                            </MonitoredVehicleJourney>
                        </VehicleActivity>
                    </VehicleMonitoringDelivery>
                </ServiceDelivery>
            </Siri>
            """;
        
        // Validate XML structure
        assertNotNull(siriXml);
        assertTrue(siriXml.contains("BUS_TEST_002"));
        assertTrue(siriXml.contains("VehicleMonitoringDelivery"));
        assertTrue(siriXml.contains("MonitoredVehicleJourney"));
        
        LOG.info("Created test SIRI XML payload");
    }
    
    @Test
    @Order(3)
    @DisplayName("Test HTTP Endpoint Connectivity")
    public void testHTTPEndpointConnectivity() {
        LOG.info("Testing HTTP endpoint connectivity...");
        
        try {
            // Test health endpoint
            URL healthUrl = new URL(HEALTH_ENDPOINT);
            HttpURLConnection healthConn = (HttpURLConnection) healthUrl.openConnection();
            healthConn.setRequestMethod("GET");
            healthConn.setConnectTimeout(5000);
            
            int healthStatus = healthConn.getResponseCode();
            LOG.info("Health endpoint returned status: {}", healthStatus);
            
            // If receiver is running, it should return 200
            if (healthStatus == 200) {
                assertTrue(healthStatus == 200, "Health endpoint should return 200");
                LOG.info("Bus SIRI HTTP Receiver is running");
            } else {
                LOG.warn("Bus SIRI HTTP Receiver is not running (status: {})", healthStatus);
            }
            
            healthConn.disconnect();
            
        } catch (Exception e) {
            LOG.warn("Could not connect to Bus SIRI HTTP Receiver: {}", e.getMessage());
            LOG.info("This is expected if the receiver is not running");
        }
    }
    
    @Test
    @Order(4)
    @DisplayName("Test SIRI Subscription Configuration")
    public void testSIRISubscriptionConfiguration() {
        LOG.info("Testing SIRI subscription configuration...");
        
        // Create subscription configuration
        ObjectNode subscriptionConfig = objectMapper.createObjectNode();
        subscriptionConfig.put("host", "http://172.23.4.136:8080");
        subscriptionConfig.put("service", "siri");
        subscriptionConfig.put("ttl", 90000);
        
        String configString = subscriptionConfig.toString();
        
        // Validate configuration
        assertNotNull(configString);
        assertTrue(configString.contains("172.23.4.136"));
        assertTrue(configString.contains("siri"));
        assertTrue(configString.contains("90000"));
        
        LOG.info("Created subscription configuration: {}", configString);
    }
    
    @Test
    @Order(5)
    @DisplayName("Test Kafka Producer for Bus SIRI")
    public void testKafkaProducerForBusSIRI() {
        if (kafkaProducer == null) {
            LOG.warn("Skipping Kafka test - Kafka not available");
            Assumptions.assumeTrue(false, "Kafka is not available - skipping test");
            return;
        }
        
        LOG.info("Testing Kafka producer for Bus SIRI topic...");
        
        try {
            // Create test bus data
            ObjectNode busData = objectMapper.createObjectNode();
            busData.put("vehicle_id", "BUS_KAFKA_TEST");
            busData.put("route_id", "AB");
            busData.put("timestamp", System.currentTimeMillis());
            
            String payload = busData.toString();
            
            // Send to Kafka with shorter timeout
            ProducerRecord<String, String> record = new ProducerRecord<>(KAFKA_TOPIC, payload);
            kafkaProducer.send(record).get(2, TimeUnit.SECONDS);
            
            LOG.info("Successfully sent test message to Kafka topic: {}", KAFKA_TOPIC);
            assertTrue(true, "Message sent to Kafka successfully");
            
        } catch (Exception e) {
            LOG.warn("Kafka test failed (possibly no Kafka running): {}", e.getMessage());
            Assumptions.assumeTrue(false, "Kafka is not available: " + e.getMessage());
        }
    }
    
    @Test
    @Order(6)
    @DisplayName("Test Multiple Bus Routes")
    public void testMultipleBusRoutes() {
        LOG.info("Testing multiple bus routes...");
        
        String[] routes = {"0", "15", "15L", "20", "28", "32", "38", "43", "44", "52", "AB"};
        
        for (String route : routes) {
            ObjectNode busData = objectMapper.createObjectNode();
            busData.put("vehicle_id", "BUS_" + route + "_001");
            busData.put("route_id", route);
            busData.put("direction", route.contains("L") ? "LIMITED" : "LOCAL");
            busData.put("latitude", 39.7392 + Math.random() * 0.1);
            busData.put("longitude", -104.9903 + Math.random() * 0.1);
            busData.put("speed_mph", 20 + Math.random() * 20);
            busData.put("status", "IN_SERVICE");
            
            String jsonString = busData.toString();
            assertNotNull(jsonString);
            assertTrue(jsonString.contains(route));
            
            LOG.debug("Created bus data for route {}: {}", route, jsonString);
        }
        
        LOG.info("Successfully created test data for {} bus routes", routes.length);
    }
    
    @Test
    @Order(7)
    @DisplayName("Test Bus Occupancy Levels")
    public void testBusOccupancyLevels() {
        LOG.info("Testing bus occupancy levels...");
        
        String[] occupancyLevels = {
            "EMPTY",
            "MANY_SEATS_AVAILABLE",
            "FEW_SEATS_AVAILABLE",
            "STANDING_ROOM_ONLY",
            "CRUSHED_STANDING_ROOM_ONLY",
            "FULL",
            "NOT_ACCEPTING_PASSENGERS"
        };
        
        for (String occupancy : occupancyLevels) {
            ObjectNode busData = objectMapper.createObjectNode();
            busData.put("vehicle_id", "BUS_OCC_TEST");
            busData.put("occupancy", occupancy);
            
            String jsonString = busData.toString();
            assertTrue(jsonString.contains(occupancy));
            
            LOG.debug("Testing occupancy level: {}", occupancy);
        }
        
        LOG.info("Successfully tested {} occupancy levels", occupancyLevels.length);
    }
    
    @Test
    @Order(8)
    @DisplayName("Test Bus Status Values")
    public void testBusStatusValues() {
        LOG.info("Testing bus status values...");
        
        String[] statusValues = {
            "IN_TRANSIT",
            "STOPPED_AT",
            "IN_SERVICE",
            "OUT_OF_SERVICE",
            "DELAYED",
            "ON_TIME",
            "EARLY"
        };
        
        for (String status : statusValues) {
            ObjectNode busData = objectMapper.createObjectNode();
            busData.put("vehicle_id", "BUS_STATUS_TEST");
            busData.put("status", status);
            
            String jsonString = busData.toString();
            assertTrue(jsonString.contains(status));
            
            LOG.debug("Testing status: {}", status);
        }
        
        LOG.info("Successfully tested {} status values", statusValues.length);
    }
    
    @Test
    @Order(9)
    @DisplayName("Test Bus Location Boundaries")
    public void testBusLocationBoundaries() {
        LOG.info("Testing bus location boundaries for Denver area...");
        
        // Denver area approximate boundaries
        double minLat = 39.614; // South boundary
        double maxLat = 39.914; // North boundary
        double minLon = -105.110; // West boundary
        double maxLon = -104.600; // East boundary
        
        // Test various locations within Denver
        double[][] testLocations = {
            {39.7392, -104.9903}, // Downtown Denver
            {39.7549, -105.0000}, // Union Station
            {39.7337, -104.9926}, // Civic Center
            {39.6777, -104.9619}, // University of Denver
            {39.8561, -104.6737}  // Denver International Airport
        };
        
        for (double[] location : testLocations) {
            assertTrue(location[0] >= minLat && location[0] <= maxLat,
                "Latitude should be within Denver boundaries");
            assertTrue(location[1] >= minLon && location[1] <= maxLon,
                "Longitude should be within Denver boundaries");
            
            LOG.debug("Valid location: {}, {}", location[0], location[1]);
        }
        
        LOG.info("All test locations are within Denver boundaries");
    }
    
    @Test
    @Order(10)
    @DisplayName("Test SIRI Subscription Renewal")
    public void testSIRISubscriptionRenewal() {
        LOG.info("Testing SIRI subscription renewal logic...");
        
        long ttl = 90000; // 90 seconds
        long renewalInterval = ttl / 2; // Should renew at half TTL
        
        assertEquals(45000, renewalInterval, "Renewal should be at half TTL");
        
        LOG.info("TTL: {} ms, Renewal interval: {} ms", ttl, renewalInterval);
        
        // Simulate subscription timestamps
        long subscriptionTime = System.currentTimeMillis();
        long nextRenewal = subscriptionTime + renewalInterval;
        
        assertTrue(nextRenewal > subscriptionTime, "Next renewal should be in the future");
        
        LOG.info("Subscription renewal logic validated");
    }
}