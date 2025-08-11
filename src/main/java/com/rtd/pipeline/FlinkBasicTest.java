package com.rtd.pipeline;

import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Basic Flink 2.0.0 test to verify execution without serialization issues.
 * Uses the simplest possible Flink operations to test the runtime.
 */
public class FlinkBasicTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(FlinkBasicTest.class);
    
    public static void main(String[] args) throws Exception {
        
        System.out.println("=== Basic Flink 2.0.0 Test ===");
        System.out.println("Testing simple Flink operations without custom serialization");
        
        try {
            // Create the simplest possible Flink environment
            Configuration config = new Configuration();
            StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment(1, config);
            env.setParallelism(1);
            
            // Create a simple stream from a collection (no custom serialization)
            DataStream<String> simpleStream = env.fromCollection(
                Arrays.asList(
                    "RTD Vehicle: Bus-001 at (39.7392, -104.9903) Route: 15",
                    "RTD Vehicle: Bus-002 at (39.7512, -104.9876) Route: 16", 
                    "RTD Vehicle: Train-A1 at (39.7621, -104.9789) Route: A-Line",
                    "RTD Vehicle: Bus-003 at (39.7334, -105.0102) Route: 40",
                    "RTD Vehicle: Train-B2 at (39.7456, -104.9654) Route: B-Line"
                ),
                Types.STRING
            ).name("Simple RTD Data Stream");
            
            // Add a simple transformation 
            DataStream<String> processedStream = simpleStream
                .map(line -> {
                    String timestamp = java.time.LocalTime.now().toString().substring(0, 8);
                    return "[" + timestamp + "] " + line;
                })
                .name("Add Timestamp");
            
            // Print the results
            processedStream.print("BASIC-FLINK-TEST");
            
            System.out.println("=== Starting Basic Flink Job ===");
            System.out.println("This tests if Flink 2.0.0 execution works at all...\n");
            
            // Execute the job
            env.execute("Basic Flink Test");
            
            System.out.println("\n=== SUCCESS: Basic Flink Execution Works! ===");
            
        } catch (Exception e) {
            System.err.println("Basic Flink execution failed: " + e.getMessage());
            e.printStackTrace();
            
            System.out.println("\n=== Test Results ===");
            if (e.getMessage().contains("SimpleUdfStreamOperatorFactory")) {
                System.out.println("❌ SimpleUdfStreamOperatorFactory issue persists even with basic operations");
                System.out.println("💡 This suggests a deeper Flink 2.0.0 configuration problem");
            } else {
                System.out.println("✅ Avoided SimpleUdfStreamOperatorFactory issue");
                System.out.println("❌ Different Flink execution problem: " + e.getClass().getSimpleName());
            }
        }
    }
}