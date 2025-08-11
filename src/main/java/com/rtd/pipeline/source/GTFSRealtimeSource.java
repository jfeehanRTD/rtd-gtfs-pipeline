package com.rtd.pipeline.source;

import com.google.transit.realtime.GtfsRealtime.*;
import com.rtd.pipeline.model.VehiclePosition;
import com.rtd.pipeline.model.TripUpdate;
import com.rtd.pipeline.model.Alert;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom Flink source that periodically downloads GTFS-RT protobuf feeds
 * from RTD and emits parsed data objects using DataGeneratorSource.
 *
 * @param <T> The type of data object to emit (VehiclePosition, TripUpdate, or Alert)
 */
public class GTFSRealtimeSource<T> {
    
    private static final Logger LOG = LoggerFactory.getLogger(GTFSRealtimeSource.class);
    
    public static <T> DataGeneratorSource<T> create(String feedUrl, long fetchIntervalSeconds, Class<T> outputType) {
        return new DataGeneratorSource<T>(
                new GTFSRealtimeGeneratorFunction<T>(feedUrl, fetchIntervalSeconds, outputType),
                Long.MAX_VALUE, // Generate indefinitely
                RateLimiterStrategy.perSecond(1.0 / fetchIntervalSeconds), // Rate limit based on fetch interval
                TypeInformation.of(outputType) // Type information for the output class
        );
    }
    
    /**
     * Generator function that fetches and parses GTFS-RT data.
     */
    private static class GTFSRealtimeGeneratorFunction<T> implements GeneratorFunction<Long, T> {
        
        private final String feedUrl;
        private final long fetchIntervalSeconds;
        private final Class<T> outputType;
        private final AtomicLong lastFetchTime = new AtomicLong(0);
        private volatile List<T> currentData = new ArrayList<>();
        private volatile int currentIndex = 0;
        
        public GTFSRealtimeGeneratorFunction(String feedUrl, long fetchIntervalSeconds, Class<T> outputType) {
            this.feedUrl = feedUrl;
            this.fetchIntervalSeconds = fetchIntervalSeconds;
            this.outputType = outputType;
        }
        
        @Override
        public T map(Long value) throws Exception {
            long currentTime = System.currentTimeMillis();
            
            // Check if we need to fetch new data
            if (currentTime - lastFetchTime.get() > fetchIntervalSeconds * 1000) {
                if (lastFetchTime.compareAndSet(lastFetchTime.get(), currentTime)) {
                    fetchNewData();
                }
            }
            
            // Return next item from current data
            synchronized (this) {
                if (currentData.isEmpty()) {
                    return null; // Will cause the generator to skip this emission
                }
                
                if (currentIndex >= currentData.size()) {
                    currentIndex = 0; // Reset to beginning
                }
                
                return currentData.get(currentIndex++);
            }
        }
        
        private void fetchNewData() {
            try {
                LOG.debug("Fetching data from: {}", feedUrl);
                
                List<T> newData = new ArrayList<>();
                
                try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                    HttpGet request = new HttpGet(feedUrl);
                    request.setHeader("User-Agent", "RTD-GTFS-Pipeline/1.0");
                    
                    HttpResponse response = httpClient.execute(request);
                    
                    if (response.getStatusLine().getStatusCode() == 200) {
                        byte[] feedData = EntityUtils.toByteArray(response.getEntity());
                        
                        FeedMessage feed = FeedMessage.parseFrom(new ByteArrayInputStream(feedData));
                        
                        LOG.info("Successfully downloaded feed with {} entities from {}", 
                                feed.getEntityCount(), feedUrl);
                        
                        for (FeedEntity entity : feed.getEntityList()) {
                            T parsedObject = parseEntity(entity, feed.getHeader().getTimestamp());
                            if (parsedObject != null) {
                                newData.add(parsedObject);
                            }
                        }
                        
                        synchronized (this) {
                            currentData = newData;
                            currentIndex = 0;
                        }
                        
                    } else {
                        LOG.error("Failed to download feed from {}. HTTP Status: {}", 
                                feedUrl, response.getStatusLine().getStatusCode());
                    }
                }
                
            } catch (Exception e) {
                LOG.error("Error fetching GTFS-RT data from {}: {}", feedUrl, e.getMessage(), e);
            }
        }
        
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
        
        private com.rtd.pipeline.model.VehiclePosition parseVehiclePosition(
                com.google.transit.realtime.GtfsRealtime.VehiclePosition vehiclePos, long feedTimestamp) {
            
            com.rtd.pipeline.model.VehiclePosition.Builder builder = com.rtd.pipeline.model.VehiclePosition.builder()
                    .timestamp_ms(feedTimestamp * 1000)
                    .vehicleId(vehiclePos.getVehicle().hasId() ? vehiclePos.getVehicle().getId() : null);
            
            if (vehiclePos.hasTrip()) {
                com.google.transit.realtime.GtfsRealtime.TripDescriptor trip = vehiclePos.getTrip();
                builder.tripId(trip.hasTripId() ? trip.getTripId() : null)
                       .routeId(trip.hasRouteId() ? trip.getRouteId() : null);
            }
            
            if (vehiclePos.hasPosition()) {
                com.google.transit.realtime.GtfsRealtime.Position pos = vehiclePos.getPosition();
                builder.latitude(pos.hasLatitude() ? (double)pos.getLatitude() : null)
                       .longitude(pos.hasLongitude() ? (double)pos.getLongitude() : null)
                       .bearing(pos.hasBearing() ? pos.getBearing() : null)
                       .speed(pos.hasSpeed() ? pos.getSpeed() : null);
            }
            
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
        
        private com.rtd.pipeline.model.TripUpdate parseTripUpdate(
                com.google.transit.realtime.GtfsRealtime.TripUpdate tripUpdate, long feedTimestamp) {
            
            com.rtd.pipeline.model.TripUpdate.Builder builder = com.rtd.pipeline.model.TripUpdate.builder()
                    .timestamp_ms(feedTimestamp * 1000);
            
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
            
            if (tripUpdate.hasVehicle() && tripUpdate.getVehicle().hasId()) {
                builder.vehicleId(tripUpdate.getVehicle().getId());
            }
            
            if (tripUpdate.getStopTimeUpdateCount() > 0) {
                com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate firstStop = tripUpdate.getStopTimeUpdate(0);
                if (firstStop.hasArrival() && firstStop.getArrival().hasDelay()) {
                    builder.delaySeconds(firstStop.getArrival().getDelay());
                } else if (firstStop.hasDeparture() && firstStop.getDeparture().hasDelay()) {
                    builder.delaySeconds(firstStop.getDeparture().getDelay());
                }
            }
            
            return builder.build();
        }
        
        private com.rtd.pipeline.model.Alert parseAlert(
                com.google.transit.realtime.GtfsRealtime.Alert alert, long feedTimestamp) {
            
            com.rtd.pipeline.model.Alert.Builder builder = com.rtd.pipeline.model.Alert.builder()
                    .timestamp_ms(feedTimestamp * 1000);
            
            if (alert.hasCause()) {
                builder.cause(alert.getCause().name());
            }
            
            if (alert.hasEffect()) {
                builder.effect(alert.getEffect().name());
            }
            
            if (alert.hasHeaderText()) {
                builder.headerText(getTranslatedText(alert.getHeaderText()));
            }
            
            if (alert.hasDescriptionText()) {
                builder.descriptionText(getTranslatedText(alert.getDescriptionText()));
            }
            
            if (alert.hasUrl()) {
                builder.url(getTranslatedText(alert.getUrl()));
            }
            
            if (alert.getActivePeriodCount() > 0) {
                com.google.transit.realtime.GtfsRealtime.TimeRange activePeriod = alert.getActivePeriod(0);
                if (activePeriod.hasStart()) {
                    builder.activePeriodStart(activePeriod.getStart() * 1000);
                }
                if (activePeriod.hasEnd()) {
                    builder.activePeriodEnd(activePeriod.getEnd() * 1000);
                }
            }
            
            builder.alertId(String.valueOf(alert.hashCode()));
            
            return builder.build();
        }
        
        private String getTranslatedText(com.google.transit.realtime.GtfsRealtime.TranslatedString translatedString) {
            if (translatedString.getTranslationCount() == 0) {
                return null;
            }
            
            for (com.google.transit.realtime.GtfsRealtime.TranslatedString.Translation translation : translatedString.getTranslationList()) {
                if ("en".equals(translation.getLanguage()) || !translation.hasLanguage()) {
                    return translation.getText();
                }
            }
            
            return translatedString.getTranslation(0).getText();
        }
    }
}