package com.rtd.pipeline.occupancy;

import com.rtd.pipeline.occupancy.model.GTFSRTVehiclePosition;
import com.rtd.pipeline.occupancy.model.OccupancyStatus;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Processor for GTFS-Realtime Vehicle Position feed data.
 * Handles ingestion, filtering, and deduplication according to Arcadis IBI Group methodology.
 */
public class GTFSRTVPProcessor implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private static final LocalTime SERVICE_DATE_TRANSITION_TIME = LocalTime.of(3, 0); // 3 AM MDT
    
    /**
     * Filters GTFS-RT VP feed to include only IN_TRANSIT_TO records.
     */
    public static class InTransitToFilter implements FilterFunction<GTFSRTVehiclePosition> {
        @Override
        public boolean filter(GTFSRTVehiclePosition vehiclePosition) {
            return vehiclePosition != null && 
                   vehiclePosition.isValid() && 
                   vehiclePosition.isInTransitTo() &&
                   vehiclePosition.getOccupancyStatus() != OccupancyStatus.NO_DATA_AVAILABLE;
        }
    }
    
    /**
     * Transforms vehicle timestamp to service date using 3 AM transition logic.
     */
    public static class ServiceDateTransformer implements MapFunction<GTFSRTVehiclePosition, GTFSRTVehiclePosition> {
        @Override
        public GTFSRTVehiclePosition map(GTFSRTVehiclePosition vehiclePosition) {
            if (vehiclePosition.getVehicleTimestamp() != null) {
                LocalDateTime serviceDate = calculateServiceDate(vehiclePosition.getVehicleTimestamp());
                vehiclePosition.setServiceDate(serviceDate);
            }
            return vehiclePosition;
        }
        
        private LocalDateTime calculateServiceDate(LocalDateTime timestamp) {
            LocalTime time = timestamp.toLocalTime();
            if (time.isBefore(SERVICE_DATE_TRANSITION_TIME)) {
                // Before 3 AM - use previous day as service date
                return timestamp.minusDays(1);
            }
            return timestamp;
        }
    }
    
    /**
     * Deduplicates GTFS-RT VP records based on unique field combinations.
     */
    public static class VPDeduplicator implements FilterFunction<GTFSRTVehiclePosition> {
        private final Set<String> seenKeys = new HashSet<>();
        
        @Override
        public boolean filter(GTFSRTVehiclePosition vehiclePosition) {
            String uniqueKey = vehiclePosition.getUniqueKey();
            if (seenKeys.contains(uniqueKey)) {
                return false; // Duplicate - filter out
            }
            seenKeys.add(uniqueKey);
            return true;
        }
    }
    
    /**
     * Filters out light rail routes and routes not present in APC data.
     */
    public static class RouteFilter implements FilterFunction<GTFSRTVehiclePosition> {
        private final Set<String> validRouteIds;
        private final Set<String> excludedRoutePatterns;
        
        public RouteFilter(Set<String> validRouteIds, Set<String> excludedRoutePatterns) {
            this.validRouteIds = validRouteIds != null ? validRouteIds : new HashSet<>();
            this.excludedRoutePatterns = excludedRoutePatterns != null ? excludedRoutePatterns : new HashSet<>();
        }
        
        @Override
        public boolean filter(GTFSRTVehiclePosition vehiclePosition) {
            String routeId = vehiclePosition.getRouteId();
            if (routeId == null) {
                return false;
            }
            
            // Filter out light rail routes (typically contain "RAIL" or specific patterns)
            if (isLightRailRoute(routeId)) {
                return false;
            }
            
            // Filter out excluded patterns
            for (String pattern : excludedRoutePatterns) {
                if (routeId.contains(pattern)) {
                    return false;
                }
            }
            
            // If valid route IDs are specified, only include those
            if (!validRouteIds.isEmpty()) {
                return validRouteIds.contains(routeId) || hasRouteVariation(routeId);
            }
            
            return true;
        }
        
        private boolean isLightRailRoute(String routeId) {
            return routeId.toUpperCase().contains("RAIL") || 
                   routeId.toUpperCase().contains("TRAIN") ||
                   routeId.matches(".*[A-Z]LINE.*"); // A-LINE, B-LINE, etc.
        }
        
        private boolean hasRouteVariation(String routeId) {
            // Handle variations like 228A, 228F mapping to 228
            for (String validRoute : validRouteIds) {
                if (routeId.startsWith(validRoute) && routeId.length() > validRoute.length()) {
                    char suffix = routeId.charAt(validRoute.length());
                    if (Character.isLetter(suffix)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
    
    /**
     * Processes GTFS-RT VP feed with all filtering and transformation steps.
     * 
     * @param vpStream Raw GTFS-RT Vehicle Position stream
     * @param validRouteIds Set of valid route IDs from APC data
     * @param excludedPatterns Set of route patterns to exclude
     * @return Processed and filtered VP stream
     */
    public DataStream<GTFSRTVehiclePosition> processVPFeed(
            DataStream<GTFSRTVehiclePosition> vpStream,
            Set<String> validRouteIds,
            Set<String> excludedPatterns) {
        
        return vpStream
            .filter(new InTransitToFilter())
            .map(new ServiceDateTransformer())
            .filter(new RouteFilter(validRouteIds, excludedPatterns))
            .filter(new VPDeduplicator());
    }
    
    /**
     * Creates a simplified VP processor for testing or minimal filtering.
     * 
     * @param vpStream Raw VP stream
     * @return Minimally processed VP stream
     */
    public DataStream<GTFSRTVehiclePosition> processVPFeedSimple(DataStream<GTFSRTVehiclePosition> vpStream) {
        return vpStream
            .filter(new InTransitToFilter())
            .map(new ServiceDateTransformer());
    }
}