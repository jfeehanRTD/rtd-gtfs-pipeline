package com.rtd.pipeline.source;

import com.google.transit.realtime.GtfsRealtime.*;
import com.rtd.pipeline.model.VehiclePosition;
import com.rtd.pipeline.model.TripUpdate;
import com.rtd.pipeline.model.Alert;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.time.Instant;

/**
 * Custom Flink source function that periodically downloads GTFS-RT protobuf feeds
 * from RTD and emits parsed data objects.
 *
 * @param <T> The type of data object to emit (VehiclePosition, TripUpdate, or Alert)
 */
public class GTFSRealtimeSource<T> implements SourceFunction<T> {
    
    private static final Logger LOG = LoggerFactory.getLogger(GTFSRealtimeSource.class);
    
    private final String feedUrl;
    private final long fetchIntervalSeconds;
    private final Class<T> outputType;
    private volatile boolean running = true;
    
    public GTFSRealtimeSource(String feedUrl, long fetchIntervalSeconds, Class<T> outputType) {
        this.feedUrl = feedUrl;
        this.fetchIntervalSeconds = fetchIntervalSeconds;
        this.outputType = outputType;
    }
    
    @Override
    public void run(SourceContext<T> ctx) throws Exception {
        
        LOG.info("Starting GTFS-RT source for URL: {} with interval: {} seconds", 
                feedUrl, fetchIntervalSeconds);
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            
            while (running) {
                try {
                    LOG.debug("Fetching data from: {}", feedUrl);
                    
                    // Download the protobuf feed
                    HttpGet request = new HttpGet(feedUrl);
                    request.setHeader("User-Agent", "RTD-GTFS-Pipeline/1.0");
                    
                    HttpResponse response = httpClient.execute(request);
                    
                    if (response.getStatusLine().getStatusCode() == 200) {
                        byte[] feedData = EntityUtils.toByteArray(response.getEntity());
                        
                        // Parse the protobuf feed
                        FeedMessage feed = FeedMessage.parseFrom(new ByteArrayInputStream(feedData));
                        
                        LOG.info("Successfully downloaded feed with {} entities from {}", 
                                feed.getEntityCount(), feedUrl);
                        
                        // Process each entity in the feed
                        for (FeedEntity entity : feed.getEntityList()) {
                            T parsedObject = parseEntity(entity, feed.getHeader().getTimestamp());
                            if (parsedObject != null) {
                                ctx.collect(parsedObject);
                            }
                        }
                        
                    } else {
                        LOG.error("Failed to download feed from {}. HTTP Status: {}", 
                                feedUrl, response.getStatusLine().getStatusCode());
                    }
                    
                } catch (Exception e) {
                    LOG.error("Error processing GTFS-RT feed from {}: {}", feedUrl, e.getMessage(), e);
                }
                
                // Wait for the next fetch interval
                if (running) {
                    Thread.sleep(fetchIntervalSeconds * 1000);
                }
            }
            
        } catch (Exception e) {
            LOG.error("Fatal error in GTFS-RT source: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    public void cancel() {
        LOG.info("Cancelling GTFS-RT source for URL: {}", feedUrl);
        running = false;
    }
    
    /**
     * Parse a GTFS-RT entity into the appropriate data model object.
     */
    @SuppressWarnings("unchecked")
    private T parseEntity(FeedEntity entity, long feedTimestamp) {
        
        try {
            if (outputType == VehiclePosition.class && entity.hasVehicle()) {
                return (T) parseVehiclePosition(entity.getVehicle(), feedTimestamp);
                
            } else if (outputType == TripUpdate.class && entity.hasTripUpdate()) {
                return (T) parseTripUpdate(entity.getTripUpdate(), feedTimestamp);
                
            } else if (outputType == Alert.class && entity.hasAlert()) {
                return (T) parseAlert(entity.getAlert(), feedTimestamp);
            }
            
        } catch (Exception e) {
            LOG.warn("Failed to parse entity {}: {}", entity.getId(), e.getMessage());
        }
        
        return null;
    }
    
    private com.rtd.pipeline.model.VehiclePosition parseVehiclePosition(com.google.transit.realtime.GtfsRealtime.VehiclePosition vehiclePos, long feedTimestamp) {
        
        com.rtd.pipeline.model.VehiclePosition.Builder builder = com.rtd.pipeline.model.VehiclePosition.builder()
                .timestamp_ms(feedTimestamp * 1000) // Convert to milliseconds
                .vehicleId(vehiclePos.getVehicle().hasId() ? vehiclePos.getVehicle().getId() : null);
        
        // Trip information
        if (vehiclePos.hasTrip()) {
            com.google.transit.realtime.GtfsRealtime.TripDescriptor trip = vehiclePos.getTrip();
            builder.tripId(trip.hasTripId() ? trip.getTripId() : null)
                   .routeId(trip.hasRouteId() ? trip.getRouteId() : null);
        }
        
        // Position information
        if (vehiclePos.hasPosition()) {
            com.google.transit.realtime.GtfsRealtime.Position pos = vehiclePos.getPosition();
            builder.latitude(pos.hasLatitude() ? (double)pos.getLatitude() : null)
                   .longitude(pos.hasLongitude() ? (double)pos.getLongitude() : null)
                   .bearing(pos.hasBearing() ? pos.getBearing() : null)
                   .speed(pos.hasSpeed() ? pos.getSpeed() : null);
        }
        
        // Status information
        if (vehiclePos.hasCurrentStatus()) {
            builder.currentStatus(vehiclePos.getCurrentStatus().name());
        }
        
        if (vehiclePos.hasCongestionLevel()) {
            builder.congestionLevel(vehiclePos.getCongestionLevel().name());
        }
        
        if (vehiclePos.hasOccupancyStatus()) {
            builder.occupancyStatus(vehiclePos.getOccupancyStatus().name());
        }
        
        return builder.build();
    }
    
    private com.rtd.pipeline.model.TripUpdate parseTripUpdate(com.google.transit.realtime.GtfsRealtime.TripUpdate tripUpdate, long feedTimestamp) {
        
        com.rtd.pipeline.model.TripUpdate.Builder builder = com.rtd.pipeline.model.TripUpdate.builder()
                .timestamp_ms(feedTimestamp * 1000);
        
        // Trip information
        if (tripUpdate.hasTrip()) {
            com.google.transit.realtime.GtfsRealtime.TripDescriptor trip = tripUpdate.getTrip();
            builder.tripId(trip.hasTripId() ? trip.getTripId() : null)
                   .routeId(trip.hasRouteId() ? trip.getRouteId() : null)
                   .startDate(trip.hasStartDate() ? trip.getStartDate() : null)
                   .startTime(trip.hasStartTime() ? trip.getStartTime() : null);
            
            if (trip.hasScheduleRelationship()) {
                builder.scheduleRelationship(trip.getScheduleRelationship().name());
            }
        }
        
        // Vehicle information
        if (tripUpdate.hasVehicle() && tripUpdate.getVehicle().hasId()) {
            builder.vehicleId(tripUpdate.getVehicle().getId());
        }
        
        // Delay information (from stop time updates)
        if (tripUpdate.getStopTimeUpdateCount() > 0) {
            // Use the first stop time update for overall trip delay
            com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate firstStop = tripUpdate.getStopTimeUpdate(0);
            if (firstStop.hasArrival() && firstStop.getArrival().hasDelay()) {
                builder.delaySeconds(firstStop.getArrival().getDelay());
            } else if (firstStop.hasDeparture() && firstStop.getDeparture().hasDelay()) {
                builder.delaySeconds(firstStop.getDeparture().getDelay());
            }
        }
        
        return builder.build();
    }
    
    private com.rtd.pipeline.model.Alert parseAlert(com.google.transit.realtime.GtfsRealtime.Alert alert, long feedTimestamp) {
        
        com.rtd.pipeline.model.Alert.Builder builder = com.rtd.pipeline.model.Alert.builder()
                .timestamp_ms(feedTimestamp * 1000);
        
        // Cause and effect
        if (alert.hasCause()) {
            builder.cause(alert.getCause().name());
        }
        
        if (alert.hasEffect()) {
            builder.effect(alert.getEffect().name());
        }
        
        // Text content
        if (alert.hasHeaderText()) {
            builder.headerText(getTranslatedText(alert.getHeaderText()));
        }
        
        if (alert.hasDescriptionText()) {
            builder.descriptionText(getTranslatedText(alert.getDescriptionText()));
        }
        
        if (alert.hasUrl()) {
            builder.url(getTranslatedText(alert.getUrl()));
        }
        
        // Active periods
        if (alert.getActivePeriodCount() > 0) {
            com.google.transit.realtime.GtfsRealtime.TimeRange activePeriod = alert.getActivePeriod(0);
            if (activePeriod.hasStart()) {
                builder.activePeriodStart(activePeriod.getStart() * 1000);
            }
            if (activePeriod.hasEnd()) {
                builder.activePeriodEnd(activePeriod.getEnd() * 1000);
            }
        }
        
        // Generate alert ID from hash if not provided
        builder.alertId(String.valueOf(alert.hashCode()));
        
        return builder.build();
    }
    
    /**
     * Extract English text from a TranslatedString, falling back to first available translation.
     */
    private String getTranslatedText(com.google.transit.realtime.GtfsRealtime.TranslatedString translatedString) {
        if (translatedString.getTranslationCount() == 0) {
            return null;
        }
        
        // Try to find English translation first
        for (com.google.transit.realtime.GtfsRealtime.TranslatedString.Translation translation : translatedString.getTranslationList()) {
            if ("en".equals(translation.getLanguage()) || !translation.hasLanguage()) {
                return translation.getText();
            }
        }
        
        // Fall back to first available translation
        return translatedString.getTranslation(0).getText();
    }
}