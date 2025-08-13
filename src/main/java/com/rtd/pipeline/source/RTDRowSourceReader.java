package com.rtd.pipeline.source;

import com.google.transit.realtime.GtfsRealtime.*;
import org.apache.flink.api.connector.source.ReaderOutput;
import org.apache.flink.api.connector.source.SourceReader;
import org.apache.flink.api.connector.source.SourceReaderContext;
import org.apache.flink.core.io.InputStatus;
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
import java.util.concurrent.CompletableFuture;

/**
 * SourceReader implementation for RTD GTFS-RT data.
 */
public class RTDRowSourceReader implements SourceReader<Row, RTDRowSplit> {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDRowSourceReader.class);
    
    private final SourceReaderContext context;
    private final String feedUrl;
    private final long fetchIntervalSeconds;
    private final List<RTDRowSplit> assignedSplits = new ArrayList<>();
    private long lastFetchTime = 0L;
    private boolean isRunning = true;
    
    public RTDRowSourceReader(SourceReaderContext context, String feedUrl, long fetchIntervalSeconds) {
        this.context = context;
        this.feedUrl = feedUrl;
        this.fetchIntervalSeconds = fetchIntervalSeconds;
    }
    
    @Override
    public void start() {
        LOG.info("Starting RTD Row Source Reader for URL: {}", feedUrl);
    }
    
    @Override
    public InputStatus pollNext(ReaderOutput<Row> output) throws Exception {
        if (!isRunning || assignedSplits.isEmpty()) {
            return InputStatus.NOTHING_AVAILABLE;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // Check if it's time to fetch new data
        if (currentTime - lastFetchTime >= fetchIntervalSeconds * 1000) {
            List<Row> vehicleRows = fetchVehiclePositionsAsRows();
            
            // Emit all fetched rows
            for (Row row : vehicleRows) {
                output.collect(row);
            }
            
            lastFetchTime = currentTime;
            LOG.info("Emitted {} vehicle rows from RTD feed", vehicleRows.size());
        }
        
        return InputStatus.MORE_AVAILABLE;
    }
    
    @Override
    public List<RTDRowSplit> snapshotState(long checkpointId) {
        return new ArrayList<>(assignedSplits);
    }
    
    @Override
    public CompletableFuture<Void> isAvailable() {
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void addSplits(List<RTDRowSplit> splits) {
        assignedSplits.addAll(splits);
        LOG.info("Added {} splits to RTD Row Source Reader", splits.size());
    }
    
    @Override
    public void notifyNoMoreSplits() {
        LOG.info("No more splits will be assigned to RTD Row Source Reader");
    }
    
    @Override
    public void close() throws Exception {
        isRunning = false;
        LOG.info("RTD Row Source Reader closed");
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
                
                LOG.debug("Successfully downloaded feed with {} entities", feed.getEntityCount());
                
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
    
    private Row parseVehiclePositionToRow(VehiclePosition vehiclePos, long feedTimestamp) {
        try {
            Row row = new Row(12);
            
            row.setField(0, feedTimestamp);
            row.setField(1, vehiclePos.getVehicle().hasId() ? vehiclePos.getVehicle().getId() : null);
            row.setField(2, vehiclePos.getVehicle().hasLabel() ? vehiclePos.getVehicle().getLabel() : null);
            
            if (vehiclePos.hasTrip()) {
                TripDescriptor trip = vehiclePos.getTrip();
                row.setField(3, trip.hasTripId() ? trip.getTripId() : null);
                row.setField(4, trip.hasRouteId() ? trip.getRouteId() : null);
            } else {
                row.setField(3, null);
                row.setField(4, null);
            }
            
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