package com.rtd.pipeline.validation;

import com.rtd.pipeline.model.VehiclePosition;
import com.rtd.pipeline.model.TripUpdate;
import com.rtd.pipeline.model.Alert;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for validating GTFS-RT data according to GTFS-RT specification.
 * Provides comprehensive validation for Vehicle Positions, Trip Updates, and Alerts.
 */
public class GTFSRTValidator {

    // GTFS-RT enumeration values according to specification
    private static final List<String> VALID_VEHICLE_STATUS = Arrays.asList(
        "INCOMING_AT", "STOPPED_AT", "IN_TRANSIT_TO"
    );

    private static final List<String> VALID_CONGESTION_LEVELS = Arrays.asList(
        "UNKNOWN_CONGESTION_LEVEL", "RUNNING_SMOOTHLY", "STOP_AND_GO", 
        "CONGESTION", "SEVERE_CONGESTION"
    );

    private static final List<String> VALID_OCCUPANCY_STATUS = Arrays.asList(
        "EMPTY", "MANY_SEATS_AVAILABLE", "FEW_SEATS_AVAILABLE", 
        "STANDING_ROOM_ONLY", "CRUSHED_STANDING_ROOM_ONLY", 
        "FULL", "NOT_ACCEPTING_PASSENGERS"
    );

    private static final List<String> VALID_SCHEDULE_RELATIONSHIPS = Arrays.asList(
        "SCHEDULED", "ADDED", "UNSCHEDULED", "CANCELED"
    );

    private static final List<String> VALID_ALERT_CAUSES = Arrays.asList(
        "UNKNOWN_CAUSE", "OTHER_CAUSE", "TECHNICAL_PROBLEM", "STRIKE", 
        "DEMONSTRATION", "ACCIDENT", "HOLIDAY", "WEATHER", "MAINTENANCE", 
        "CONSTRUCTION", "POLICE_ACTIVITY", "MEDICAL_EMERGENCY"
    );

    private static final List<String> VALID_ALERT_EFFECTS = Arrays.asList(
        "NO_SERVICE", "REDUCED_SERVICE", "SIGNIFICANT_DELAYS", "DETOUR", 
        "ADDITIONAL_SERVICE", "MODIFIED_SERVICE", "OTHER_EFFECT", 
        "UNKNOWN_EFFECT", "STOP_MOVED", "NO_EFFECT", "ACCESSIBILITY_ISSUE"
    );

    // Coordinate validation patterns
    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;
    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;

    // Denver metro area bounds for RTD validation
    private static final double DENVER_MIN_LATITUDE = 39.0;
    private static final double DENVER_MAX_LATITUDE = 40.5;
    private static final double DENVER_MIN_LONGITUDE = -105.5;
    private static final double DENVER_MAX_LONGITUDE = -104.0;

    // Time validation
    private static final long MAX_TIMESTAMP_AGE_MS = 24 * 60 * 60 * 1000; // 24 hours
    private static final Pattern TIME_PATTERN = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{8}$"); // YYYYMMDD

    /**
     * Validates a VehiclePosition according to GTFS-RT specification
     */
    public static ValidationResult validateVehiclePosition(VehiclePosition position) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (position == null) {
            errors.add("VehiclePosition cannot be null");
            return new ValidationResult(false, errors, warnings);
        }

        // Required field validation
        if (position.getVehicleId() == null || position.getVehicleId().trim().isEmpty()) {
            errors.add("Vehicle ID is required");
        }

        // Position validation
        if (position.getLatitude() == null || position.getLongitude() == null) {
            errors.add("Both latitude and longitude are required");
        } else {
            if (position.getLatitude() < MIN_LATITUDE || position.getLatitude() > MAX_LATITUDE) {
                errors.add("Latitude must be between -90 and 90 degrees");
            }
            if (position.getLongitude() < MIN_LONGITUDE || position.getLongitude() > MAX_LONGITUDE) {
                errors.add("Longitude must be between -180 and 180 degrees");
            }

            // RTD-specific geographic validation
            if (position.getLatitude() < DENVER_MIN_LATITUDE || position.getLatitude() > DENVER_MAX_LATITUDE ||
                position.getLongitude() < DENVER_MIN_LONGITUDE || position.getLongitude() > DENVER_MAX_LONGITUDE) {
                warnings.add("Vehicle position is outside Denver metro area (may be valid for express routes)");
            }
        }

        // Bearing validation
        if (position.getBearing() != null) {
            if (position.getBearing() < 0 || position.getBearing() > 360) {
                errors.add("Bearing must be between 0 and 360 degrees");
            }
        }

        // Speed validation
        if (position.getSpeed() != null && position.getSpeed() < 0) {
            errors.add("Speed cannot be negative");
        }
        if (position.getSpeed() != null && position.getSpeed() > 200) {
            warnings.add("Speed over 200 km/h seems unusually high for transit vehicle");
        }

        // Timestamp validation
        if (position.getTimestamp() == null) {
            warnings.add("Timestamp is missing");
        } else {
            long currentTime = Instant.now().toEpochMilli();
            if (Math.abs(currentTime - position.getTimestamp()) > MAX_TIMESTAMP_AGE_MS) {
                warnings.add("Timestamp is more than 24 hours old or in the future");
            }
        }

        // Enum validation
        if (position.getCurrentStatus() != null && 
            !VALID_VEHICLE_STATUS.contains(position.getCurrentStatus())) {
            errors.add("Invalid current_status: " + position.getCurrentStatus());
        }

        if (position.getCongestionLevel() != null && 
            !VALID_CONGESTION_LEVELS.contains(position.getCongestionLevel())) {
            errors.add("Invalid congestion_level: " + position.getCongestionLevel());
        }

        if (position.getOccupancyStatus() != null && 
            !VALID_OCCUPANCY_STATUS.contains(position.getOccupancyStatus())) {
            errors.add("Invalid occupancy_status: " + position.getOccupancyStatus());
        }

        // Trip and route validation
        if (position.getTripId() == null && position.getRouteId() == null) {
            warnings.add("Both trip_id and route_id are missing - at least one should be provided");
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Validates a TripUpdate according to GTFS-RT specification
     */
    public static ValidationResult validateTripUpdate(TripUpdate tripUpdate) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (tripUpdate == null) {
            errors.add("TripUpdate cannot be null");
            return new ValidationResult(false, errors, warnings);
        }

        // Required field validation
        if (tripUpdate.getTripId() == null || tripUpdate.getTripId().trim().isEmpty()) {
            errors.add("Trip ID is required");
        }

        // Schedule relationship validation
        if (tripUpdate.getScheduleRelationship() != null && 
            !VALID_SCHEDULE_RELATIONSHIPS.contains(tripUpdate.getScheduleRelationship())) {
            errors.add("Invalid schedule_relationship: " + tripUpdate.getScheduleRelationship());
        }

        // Date format validation
        if (tripUpdate.getStartDate() != null) {
            if (!DATE_PATTERN.matcher(tripUpdate.getStartDate()).matches()) {
                errors.add("Start date must be in YYYYMMDD format");
            } else {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
                        .withResolverStyle(ResolverStyle.SMART);
                    LocalDate parsedDate = LocalDate.parse(tripUpdate.getStartDate(), formatter);
                    
                    // Additional check: ensure the parsed date matches the original string
                    // This catches cases like "20240230" which gets parsed as "20240302"
                    String reformattedDate = parsedDate.format(formatter);
                    if (!reformattedDate.equals(tripUpdate.getStartDate())) {
                        errors.add("Invalid start date: " + tripUpdate.getStartDate());
                    }
                } catch (DateTimeParseException e) {
                    errors.add("Invalid start date: " + tripUpdate.getStartDate());
                }
            }
        }

        // Time format validation
        if (tripUpdate.getStartTime() != null) {
            String[] parts = tripUpdate.getStartTime().split(":");
            if (parts.length == 3) {
                boolean formatValid = true;
                try {
                    int hours = Integer.parseInt(parts[0]);
                    int minutes = Integer.parseInt(parts[1]);
                    int seconds = Integer.parseInt(parts[2]);
                    
                    // GTFS allows hours > 24 for service that spans midnight
                    if (hours < 0 || hours > 47) {
                        errors.add("Hours must be between 0 and 47");
                        formatValid = false;
                    }
                    if (minutes < 0 || minutes > 59) {
                        errors.add("Minutes must be between 0 and 59");
                        formatValid = false;
                    }
                    if (seconds < 0 || seconds > 59) {
                        errors.add("Seconds must be between 0 and 59");
                        formatValid = false;
                    }
                    
                    // Check if format matches HH:MM:SS pattern when values are valid
                    if (formatValid && !TIME_PATTERN.matcher(tripUpdate.getStartTime()).matches()) {
                        errors.add("Start time must be in HH:MM:SS format");
                    }
                    
                } catch (NumberFormatException e) {
                    errors.add("Invalid time format: " + tripUpdate.getStartTime());
                }
            } else {
                errors.add("Start time must be in HH:MM:SS format");
            }
        }

        // Delay validation
        if (tripUpdate.getDelaySeconds() != null) {
            if (Math.abs(tripUpdate.getDelaySeconds()) > 7200) { // 2 hours
                warnings.add("Delay of " + tripUpdate.getDelaySeconds() + " seconds (>" + 
                           Math.abs(tripUpdate.getDelaySeconds()) / 60 + " minutes) seems unusually large");
            }
        }

        // Timestamp validation
        if (tripUpdate.getTimestamp() == null) {
            warnings.add("Timestamp is missing");
        } else {
            long currentTime = Instant.now().toEpochMilli();
            if (Math.abs(currentTime - tripUpdate.getTimestamp()) > MAX_TIMESTAMP_AGE_MS) {
                warnings.add("Timestamp is more than 24 hours old or in the future");
            }
        }

        // Cross-validation
        if (tripUpdate.getVehicleId() == null && tripUpdate.getTripId() != null) {
            warnings.add("Trip update has trip_id but no vehicle_id");
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Validates an Alert according to GTFS-RT specification
     */
    public static ValidationResult validateAlert(Alert alert) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (alert == null) {
            errors.add("Alert cannot be null");
            return new ValidationResult(false, errors, warnings);
        }

        // Alert ID validation
        if (alert.getAlertId() == null || alert.getAlertId().trim().isEmpty()) {
            errors.add("Alert ID is required");
        }

        // Cause validation
        if (alert.getCause() != null && !VALID_ALERT_CAUSES.contains(alert.getCause())) {
            errors.add("Invalid cause: " + alert.getCause());
        }

        // Effect validation
        if (alert.getEffect() != null && !VALID_ALERT_EFFECTS.contains(alert.getEffect())) {
            errors.add("Invalid effect: " + alert.getEffect());
        }

        // Text content validation
        if ((alert.getHeaderText() == null || alert.getHeaderText().trim().isEmpty()) &&
            (alert.getDescriptionText() == null || alert.getDescriptionText().trim().isEmpty())) {
            warnings.add("Alert should have either header_text or description_text");
        }

        // URL validation
        if (alert.getUrl() != null && !alert.getUrl().trim().isEmpty()) {
            if (!isValidUrl(alert.getUrl())) {
                errors.add("Invalid URL format: " + alert.getUrl());
            }
        }

        // Active period validation
        if (alert.getActivePeriodStart() != null && alert.getActivePeriodEnd() != null) {
            if (alert.getActivePeriodStart() >= alert.getActivePeriodEnd()) {
                errors.add("Active period start must be before end");
            }

            long currentTime = Instant.now().getEpochSecond();
            if (alert.getActivePeriodEnd() < currentTime) {
                warnings.add("Alert has expired (active period end is in the past)");
            }
        }

        // Timestamp validation
        if (alert.getTimestamp() == null) {
            warnings.add("Timestamp is missing");
        } else {
            long currentTime = Instant.now().toEpochMilli();
            if (Math.abs(currentTime - alert.getTimestamp()) > MAX_TIMESTAMP_AGE_MS) {
                warnings.add("Timestamp is more than 24 hours old or in the future");
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Simple URL validation
     */
    private static boolean isValidUrl(String url) {
        try {
            return url.startsWith("http://") || url.startsWith("https://");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validation result container
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = new ArrayList<>(errors);
            this.warnings = new ArrayList<>(warnings);
        }

        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }

        public boolean hasWarnings() { return !warnings.isEmpty(); }
        public boolean hasErrors() { return !errors.isEmpty(); }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Valid: ").append(valid).append("\n");
            if (!errors.isEmpty()) {
                sb.append("Errors:\n");
                errors.forEach(error -> sb.append("  - ").append(error).append("\n"));
            }
            if (!warnings.isEmpty()) {
                sb.append("Warnings:\n");
                warnings.forEach(warning -> sb.append("  - ").append(warning).append("\n"));
            }
            return sb.toString();
        }
    }
}