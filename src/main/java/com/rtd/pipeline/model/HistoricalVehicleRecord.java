package com.rtd.pipeline.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;
import java.util.Objects;

/**
 * Historical record of vehicle position data for trend analysis and disruption detection.
 * This class represents a time-stamped snapshot of vehicle state for storage in data sinks.
 */
public class HistoricalVehicleRecord {
    
    @JsonProperty("record_id")
    private String recordId;
    
    @JsonProperty("vehicle_id")
    private String vehicleId;
    
    @JsonProperty("route_id")
    private String routeId;
    
    @JsonProperty("trip_id")
    private String tripId;
    
    @JsonProperty("vehicle_type")
    private VehicleType vehicleType;
    
    @JsonProperty("latitude")
    private Double latitude;
    
    @JsonProperty("longitude")
    private Double longitude;
    
    @JsonProperty("bearing")
    private Float bearing;
    
    @JsonProperty("speed_kmh")
    private Float speedKmh;
    
    @JsonProperty("current_status")
    private String currentStatus;
    
    @JsonProperty("occupancy_status")
    private String occupancyStatus;
    
    @JsonProperty("congestion_level")
    private String congestionLevel;
    
    @JsonProperty("delay_seconds")
    private Integer delaySeconds;
    
    @JsonProperty("schedule_relationship")
    private String scheduleRelationship;
    
    @JsonProperty("original_timestamp_ms")
    private Long originalTimestampMs;
    
    @JsonProperty("ingestion_timestamp_ms")
    private Long ingestionTimestampMs;
    
    @JsonProperty("processing_timestamp_ms")  
    private Long processingTimestampMs;
    
    @JsonProperty("service_date")
    private String serviceDate; // YYYY-MM-DD format
    
    @JsonProperty("service_hour")
    private Integer serviceHour; // 0-47 for GTFS time
    
    @JsonProperty("is_missing")
    private Boolean isMissing;
    
    @JsonProperty("missing_duration_minutes")
    private Integer missingDurationMinutes;
    
    @JsonProperty("service_disruption_level")
    private DisruptionLevel serviceDisruptionLevel;

    public enum VehicleType {
        LIGHT_RAIL("LR"),
        BUS("BUS"),
        BRT("BRT"),
        SHUTTLE("SHUTTLE");
        
        private final String code;
        
        VehicleType(String code) {
            this.code = code;
        }
        
        public String getCode() { return code; }
        
        public static VehicleType fromRouteId(String routeId) {
            if (routeId == null) return BUS;
            
            // Light rail routes are single letters
            if (routeId.length() == 1 && Character.isLetter(routeId.charAt(0))) {
                return LIGHT_RAIL;
            }
            
            // BRT routes
            if (routeId.startsWith("FF") || routeId.startsWith("BRT")) {
                return BRT;
            }
            
            // Shuttle routes
            if (routeId.toLowerCase().contains("shuttle") || 
                routeId.toLowerCase().contains("circulator")) {
                return SHUTTLE;
            }
            
            return BUS; // Default
        }
    }

    public enum DisruptionLevel {
        NORMAL(0, "Normal service"),
        MINOR(1, "Minor delays or gaps"),
        MODERATE(2, "Moderate service disruption"),
        MAJOR(3, "Major service disruption"),
        CRITICAL(4, "Critical service failure");
        
        private final int severity;
        private final String description;
        
        DisruptionLevel(int severity, String description) {
            this.severity = severity;
            this.description = description;
        }
        
        public int getSeverity() { return severity; }
        public String getDescription() { return description; }
    }

    // Default constructor for Flink deserialization
    public HistoricalVehicleRecord() {
        this.ingestionTimestampMs = Instant.now().toEpochMilli();
        this.processingTimestampMs = Instant.now().toEpochMilli();
        this.isMissing = false;
        this.serviceDisruptionLevel = DisruptionLevel.NORMAL;
    }

    // Constructor from VehiclePosition
    public HistoricalVehicleRecord(VehiclePosition position) {
        this();
        this.recordId = generateRecordId(position.getVehicleId(), position.getTimestamp());
        this.vehicleId = position.getVehicleId();
        this.routeId = position.getRouteId();
        this.tripId = position.getTripId();
        this.vehicleType = VehicleType.fromRouteId(position.getRouteId());
        this.latitude = position.getLatitude();
        this.longitude = position.getLongitude();
        this.bearing = position.getBearing();
        this.speedKmh = position.getSpeed();
        this.currentStatus = position.getCurrentStatus();
        this.occupancyStatus = position.getOccupancyStatus();
        this.congestionLevel = position.getCongestionLevel();
        this.originalTimestampMs = position.getTimestamp();
        
        // Set service date and hour
        if (position.getTimestamp() != null) {
            Instant instant = Instant.ofEpochMilli(position.getTimestamp());
            this.serviceDate = instant.toString().substring(0, 10); // YYYY-MM-DD
            this.serviceHour = instant.atZone(java.time.ZoneId.of("America/Denver"))
                .getHour(); // Mountain Time
        }
    }

    // Constructor from TripUpdate
    public HistoricalVehicleRecord(TripUpdate tripUpdate) {
        this();
        this.recordId = generateRecordId(tripUpdate.getVehicleId(), tripUpdate.getTimestamp());
        this.vehicleId = tripUpdate.getVehicleId();
        this.routeId = tripUpdate.getRouteId();
        this.tripId = tripUpdate.getTripId();
        this.vehicleType = VehicleType.fromRouteId(tripUpdate.getRouteId());
        this.delaySeconds = tripUpdate.getDelaySeconds();
        this.scheduleRelationship = tripUpdate.getScheduleRelationship();
        this.originalTimestampMs = tripUpdate.getTimestamp();
        
        // Set service date and hour
        if (tripUpdate.getTimestamp() != null) {
            Instant instant = Instant.ofEpochMilli(tripUpdate.getTimestamp());
            this.serviceDate = instant.toString().substring(0, 10); // YYYY-MM-DD
            this.serviceHour = instant.atZone(java.time.ZoneId.of("America/Denver"))
                .getHour(); // Mountain Time
        }
    }

    // Constructor for missing vehicle record
    public static HistoricalVehicleRecord createMissingVehicleRecord(String vehicleId, String routeId, 
                                                                   int missingDurationMinutes,
                                                                   DisruptionLevel disruptionLevel) {
        HistoricalVehicleRecord record = new HistoricalVehicleRecord();
        record.recordId = generateRecordId(vehicleId, Instant.now().toEpochMilli());
        record.vehicleId = vehicleId;
        record.routeId = routeId;
        record.vehicleType = VehicleType.fromRouteId(routeId);
        record.isMissing = true;
        record.missingDurationMinutes = missingDurationMinutes;
        record.serviceDisruptionLevel = disruptionLevel;
        
        Instant now = Instant.now();
        record.serviceDate = now.toString().substring(0, 10);
        record.serviceHour = now.atZone(java.time.ZoneId.of("America/Denver")).getHour();
        
        return record;
    }

    private static String generateRecordId(String vehicleId, Long timestamp) {
        if (vehicleId == null) vehicleId = "UNKNOWN";
        if (timestamp == null) timestamp = Instant.now().toEpochMilli();
        return String.format("%s_%d", vehicleId, timestamp);
    }

    // Builder pattern for flexible construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private HistoricalVehicleRecord record = new HistoricalVehicleRecord();

        public Builder recordId(String recordId) { record.recordId = recordId; return this; }
        public Builder vehicleId(String vehicleId) { record.vehicleId = vehicleId; return this; }
        public Builder routeId(String routeId) { 
            record.routeId = routeId; 
            record.vehicleType = VehicleType.fromRouteId(routeId);
            return this; 
        }
        public Builder tripId(String tripId) { record.tripId = tripId; return this; }
        public Builder vehicleType(VehicleType vehicleType) { record.vehicleType = vehicleType; return this; }
        public Builder latitude(Double latitude) { record.latitude = latitude; return this; }
        public Builder longitude(Double longitude) { record.longitude = longitude; return this; }
        public Builder bearing(Float bearing) { record.bearing = bearing; return this; }
        public Builder speedKmh(Float speedKmh) { record.speedKmh = speedKmh; return this; }
        public Builder currentStatus(String currentStatus) { record.currentStatus = currentStatus; return this; }
        public Builder occupancyStatus(String occupancyStatus) { record.occupancyStatus = occupancyStatus; return this; }
        public Builder congestionLevel(String congestionLevel) { record.congestionLevel = congestionLevel; return this; }
        public Builder delaySeconds(Integer delaySeconds) { record.delaySeconds = delaySeconds; return this; }
        public Builder scheduleRelationship(String scheduleRelationship) { record.scheduleRelationship = scheduleRelationship; return this; }
        public Builder originalTimestampMs(Long originalTimestampMs) { record.originalTimestampMs = originalTimestampMs; return this; }
        public Builder ingestionTimestampMs(Long ingestionTimestampMs) { record.ingestionTimestampMs = ingestionTimestampMs; return this; }
        public Builder processingTimestampMs(Long processingTimestampMs) { record.processingTimestampMs = processingTimestampMs; return this; }
        public Builder serviceDate(String serviceDate) { record.serviceDate = serviceDate; return this; }
        public Builder serviceHour(Integer serviceHour) { record.serviceHour = serviceHour; return this; }
        public Builder isMissing(Boolean isMissing) { record.isMissing = isMissing; return this; }
        public Builder missingDurationMinutes(Integer missingDurationMinutes) { record.missingDurationMinutes = missingDurationMinutes; return this; }
        public Builder serviceDisruptionLevel(DisruptionLevel serviceDisruptionLevel) { record.serviceDisruptionLevel = serviceDisruptionLevel; return this; }

        public HistoricalVehicleRecord build() {
            if (record.recordId == null) {
                record.recordId = generateRecordId(record.vehicleId, record.originalTimestampMs);
            }
            return record;
        }
    }

    // Getters and setters
    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }

    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }

    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Float getBearing() { return bearing; }
    public void setBearing(Float bearing) { this.bearing = bearing; }

    public Float getSpeedKmh() { return speedKmh; }
    public void setSpeedKmh(Float speedKmh) { this.speedKmh = speedKmh; }

    public String getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(String currentStatus) { this.currentStatus = currentStatus; }

    public String getOccupancyStatus() { return occupancyStatus; }
    public void setOccupancyStatus(String occupancyStatus) { this.occupancyStatus = occupancyStatus; }

    public String getCongestionLevel() { return congestionLevel; }
    public void setCongestionLevel(String congestionLevel) { this.congestionLevel = congestionLevel; }

    public Integer getDelaySeconds() { return delaySeconds; }
    public void setDelaySeconds(Integer delaySeconds) { this.delaySeconds = delaySeconds; }

    public String getScheduleRelationship() { return scheduleRelationship; }
    public void setScheduleRelationship(String scheduleRelationship) { this.scheduleRelationship = scheduleRelationship; }

    public Long getOriginalTimestampMs() { return originalTimestampMs; }
    public void setOriginalTimestampMs(Long originalTimestampMs) { this.originalTimestampMs = originalTimestampMs; }

    public Long getIngestionTimestampMs() { return ingestionTimestampMs; }
    public void setIngestionTimestampMs(Long ingestionTimestampMs) { this.ingestionTimestampMs = ingestionTimestampMs; }

    public Long getProcessingTimestampMs() { return processingTimestampMs; }
    public void setProcessingTimestampMs(Long processingTimestampMs) { this.processingTimestampMs = processingTimestampMs; }

    public String getServiceDate() { return serviceDate; }
    public void setServiceDate(String serviceDate) { this.serviceDate = serviceDate; }

    public Integer getServiceHour() { return serviceHour; }
    public void setServiceHour(Integer serviceHour) { this.serviceHour = serviceHour; }

    public Boolean getIsMissing() { return isMissing; }
    public void setIsMissing(Boolean isMissing) { this.isMissing = isMissing; }

    public Integer getMissingDurationMinutes() { return missingDurationMinutes; }
    public void setMissingDurationMinutes(Integer missingDurationMinutes) { this.missingDurationMinutes = missingDurationMinutes; }

    public DisruptionLevel getServiceDisruptionLevel() { return serviceDisruptionLevel; }
    public void setServiceDisruptionLevel(DisruptionLevel serviceDisruptionLevel) { this.serviceDisruptionLevel = serviceDisruptionLevel; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HistoricalVehicleRecord that = (HistoricalVehicleRecord) o;
        return Objects.equals(recordId, that.recordId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordId);
    }

    @Override
    public String toString() {
        return "HistoricalVehicleRecord{" +
                "recordId='" + recordId + '\'' +
                ", vehicleId='" + vehicleId + '\'' +
                ", routeId='" + routeId + '\'' +
                ", vehicleType=" + vehicleType +
                ", isMissing=" + isMissing +
                ", serviceDisruptionLevel=" + serviceDisruptionLevel +
                ", serviceDate='" + serviceDate + '\'' +
                ", serviceHour=" + serviceHour +
                '}';
    }
}