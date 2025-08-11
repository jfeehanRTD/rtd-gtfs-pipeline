package com.rtd.pipeline;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple test pipeline using DataGeneratorSource to verify Flink setup works.
 */
public class DataGenTestPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(DataGenTestPipeline.class);
    
    public static void main(String[] args) throws Exception {
        
        LOG.info("Starting Data Generator Test Pipeline");
        
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        
        // Create a simple data generator source  
        DataGeneratorSource<String> dataGenSource = new DataGeneratorSource<String>(
            (GeneratorFunction<Long, String>) value -> "Generated message: " + value,
            10L, // Generate 10 messages
            RateLimiterStrategy.perSecond(1.0), // 1 message per second
            org.apache.flink.api.common.typeinfo.Types.STRING
        );
        
        DataStream<String> testStream = env.fromSource(
            dataGenSource,
            WatermarkStrategy.noWatermarks(),
            "Test Data Generator"
        );
        
        // Just print the results
        testStream.print("Test Output");
        
        // Execute the job
        env.execute("Data Generator Test Pipeline");
    }
}