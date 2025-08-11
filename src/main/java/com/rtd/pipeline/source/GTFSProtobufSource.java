package com.rtd.pipeline.source;

import com.google.transit.realtime.GtfsRealtime.*;
import org.apache.flink.streaming.api.functions.source.legacy.SourceFunction;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Flink 2.0.0 source that uses native GTFS-RT protobuf messages directly.
 * This approach avoids custom class serialization issues by using Protocol Buffer
 * messages that have built-in serialization support.
 */
public class GTFSProtobufSource implements SourceFunction<VehiclePosition> {
    
    private static final Logger LOG = LoggerFactory.getLogger(GTFSProtobufSource.class);
    
    private final String feedUrl;
    private final long fetchIntervalSeconds;
    private volatile boolean isRunning = true;
    
    public GTFSProtobufSource(String feedUrl, long fetchIntervalSeconds) {
        this.feedUrl = feedUrl;
        this.fetchIntervalSeconds = fetchIntervalSeconds;
    }
    
    public static GTFSProtobufSource create(String feedUrl, long fetchIntervalSeconds) {
        return new GTFSProtobufSource(feedUrl, fetchIntervalSeconds);
    }
    
    @Override
    public void run(SourceContext<VehiclePosition> ctx) throws Exception {
        LOG.info("Starting GTFS Protobuf Source for URL: {} with interval: {} seconds", 
                feedUrl, fetchIntervalSeconds);
        
        while (isRunning) {
            try {
                List<VehiclePosition> vehiclePositions = fetchVehiclePositions();
                
                // Emit all fetched vehicle positions
                for (VehiclePosition vehiclePos : vehiclePositions) {
                    if (vehiclePos != null && isRunning) {
                        synchronized (ctx.getCheckpointLock()) {
                            ctx.collect(vehiclePos);
                        }
                    }
                }
                
                LOG.info("Emitted {} vehicle positions from {}", vehiclePositions.size(), feedUrl);
                
                // Wait for the specified interval
                Thread.sleep(fetchIntervalSeconds * 1000);
                
            } catch (InterruptedException e) {
                LOG.info("GTFS Protobuf Source interrupted");
                break;
            } catch (Exception e) {
                LOG.error("Error in GTFS Protobuf source: {}", e.getMessage(), e);
                // Wait before retrying
                Thread.sleep(10000);
            }
        }
    }
    
    @Override
    public void cancel() {
        isRunning = false;
        LOG.info("GTFS Protobuf Source cancelled");
    }
    
    private List<VehiclePosition> fetchVehiclePositions() {
        List<VehiclePosition> results = new ArrayList<>();
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(feedUrl);
            request.setHeader("User-Agent", "RTD-GTFS-Pipeline-PB/1.0");
            
            HttpResponse response = httpClient.execute(request);
            
            if (response.getStatusLine().getStatusCode() == 200) {
                byte[] feedData = EntityUtils.toByteArray(response.getEntity());
                FeedMessage feed = FeedMessage.parseFrom(new ByteArrayInputStream(feedData));
                
                LOG.info("Successfully downloaded protobuf feed with {} entities from {}", 
                        feed.getEntityCount(), feedUrl);
                
                // Extract vehicle positions from the feed
                for (FeedEntity entity : feed.getEntityList()) {
                    if (entity.hasVehicle()) {
                        VehiclePosition vehiclePos = entity.getVehicle();
                        
                        // Validate that the vehicle position has required data
                        if (vehiclePos != null && vehiclePos.hasVehicle() && vehiclePos.getVehicle().hasId()) {
                            results.add(vehiclePos);
                        }
                    }
                }
                
                LOG.debug("Parsed {} valid vehicle positions from feed", results.size());
                
            } else {
                LOG.error("Failed to download feed from {}. HTTP Status: {}", 
                        feedUrl, response.getStatusLine().getStatusCode());
            }
        } catch (Exception e) {
            LOG.error("Error fetching GTFS-RT protobuf data from {}: {}", feedUrl, e.getMessage(), e);
        }
        
        return results;
    }
}