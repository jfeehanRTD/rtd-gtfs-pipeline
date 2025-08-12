package com.rtd.pipeline.source;

import com.google.transit.realtime.GtfsRealtime.*;
import org.apache.flink.streaming.api.functions.source.legacy.SourceFunction;
import org.apache.flink.types.Row;
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
 * Flink 2.0.0 compatible GTFS-RT source using Row data types to avoid serialization issues.
 * Outputs Row objects instead of custom classes to prevent SimpleUdfStreamOperatorFactory errors.
 */
public class RTDRowSource implements SourceFunction<Row> {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDRowSource.class);
    
    private final String feedUrl;
    private final long fetchIntervalSeconds;
    private volatile boolean isRunning = true;
    
    public RTDRowSource(String feedUrl, long fetchIntervalSeconds) {
        this.feedUrl = feedUrl;
        this.fetchIntervalSeconds = fetchIntervalSeconds;
    }
    
    @Override
    public void run(SourceContext<Row> ctx) throws Exception {
        LOG.info("Starting RTD Row Source for URL: {} with interval: {} seconds", feedUrl, fetchIntervalSeconds);
        
        while (isRunning) {
            try {
                List<Row> newData = fetchVehiclePositionsAsRows();
                
                // Emit all fetched data
                for (Row row : newData) {
                    if (row != null && isRunning) {
                        synchronized (ctx.getCheckpointLock()) {
                            ctx.collect(row);
                        }
                    }
                }
                
                LOG.info("Emitted {} vehicle position rows from {}", newData.size(), feedUrl);
                
                // Wait for the specified interval
                Thread.sleep(fetchIntervalSeconds * 1000);
                
            } catch (InterruptedException e) {
                LOG.info("RTD Row Source interrupted");
                break;
            } catch (Exception e) {
                LOG.error("Error in RTD Row source: {}", e.getMessage(), e);
                // Wait a bit before retrying
                Thread.sleep(10000);
            }
        }
    }
    
    @Override
    public void cancel() {
        isRunning = false;
        LOG.info("RTD Row Source cancelled");
    }
    
    private List<Row> fetchVehiclePositionsAsRows() {
        List<Row> results = new ArrayList<>();
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(feedUrl);
            request.setHeader("User-Agent", "RTD-GTFS-Pipeline/1.0");
            
            HttpResponse response = httpClient.execute(request);
            
            if (response.getStatusLine().getStatusCode() == 200) {
                byte[] feedData = EntityUtils.toByteArray(response.getEntity());
                FeedMessage feed = FeedMessage.parseFrom(new ByteArrayInputStream(feedData));
                
                LOG.info("Successfully downloaded feed with {} entities from {}", 
                        feed.getEntityCount(), feedUrl);
                
                long feedTimestamp = feed.getHeader().getTimestamp() * 1000; // Convert to milliseconds
                
                for (FeedEntity entity : feed.getEntityList()) {
                    if (entity.hasVehicle()) {
                        Row vehicleRow = parseVehiclePositionToRow(entity.getVehicle(), feedTimestamp);
                        if (vehicleRow != null) {
                            results.add(vehicleRow);
                        }
                    }
                }
            } else {
                LOG.error("Failed to download feed from {}. HTTP Status: {}", 
                        feedUrl, response.getStatusLine().getStatusCode());
            }
        } catch (Exception e) {
            LOG.error("Error fetching GTFS-RT data from {}: {}", feedUrl, e.getMessage(), e);
        }
        
        return results;
    }
    
    /**
     * Converts GTFS-RT VehiclePosition to Flink Row with fixed schema:
     * 0: timestamp_ms (BIGINT)
     * 1: vehicle_id (STRING) 
     * 2: vehicle_label (STRING) - Fleet number like "6559"
     * 3: trip_id (STRING)
     * 4: route_id (STRING)
     * 5: latitude (DOUBLE)
     * 6: longitude (DOUBLE)
     * 7: bearing (FLOAT)
     * 8: speed (FLOAT)
     * 9: current_status (STRING)
     * 10: congestion_level (STRING)
     * 11: occupancy_status (STRING)
     */
    private Row parseVehiclePositionToRow(VehiclePosition vehiclePos, long feedTimestamp) {
        try {
            Row row = new Row(12); // 12 fields (added vehicle_label)
            
            // Timestamp
            row.setField(0, feedTimestamp);
            
            // Vehicle ID and Label
            row.setField(1, vehiclePos.getVehicle().hasId() ? vehiclePos.getVehicle().getId() : null);
            row.setField(2, vehiclePos.getVehicle().hasLabel() ? vehiclePos.getVehicle().getLabel() : null);
            
            // Trip and Route information
            if (vehiclePos.hasTrip()) {
                TripDescriptor trip = vehiclePos.getTrip();
                row.setField(3, trip.hasTripId() ? trip.getTripId() : null);
                row.setField(4, trip.hasRouteId() ? trip.getRouteId() : null);
            } else {
                row.setField(3, null);
                row.setField(4, null);
            }
            
            // Position information
            if (vehiclePos.hasPosition()) {
                Position pos = vehiclePos.getPosition();
                row.setField(5, pos.hasLatitude() ? (double) pos.getLatitude() : null);
                row.setField(6, pos.hasLongitude() ? (double) pos.getLongitude() : null);
                row.setField(7, pos.hasBearing() ? pos.getBearing() : null);
                row.setField(8, pos.hasSpeed() ? pos.getSpeed() : null);
            } else {
                row.setField(5, null);
                row.setField(6, null);
                row.setField(7, null);
                row.setField(8, null);
            }
            
            // Status information
            row.setField(9, vehiclePos.hasCurrentStatus() ? vehiclePos.getCurrentStatus().name() : null);
            row.setField(10, vehiclePos.hasCongestionLevel() ? vehiclePos.getCongestionLevel().name() : null);
            row.setField(11, vehiclePos.hasOccupancyStatus() ? vehiclePos.getOccupancyStatus().name() : null);
            
            return row;
            
        } catch (Exception e) {
            LOG.warn("Failed to parse vehicle position to row: {}", e.getMessage());
            return null;
        }
    }
}