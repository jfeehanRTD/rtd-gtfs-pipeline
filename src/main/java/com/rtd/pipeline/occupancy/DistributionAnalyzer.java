package com.rtd.pipeline.occupancy;

import com.rtd.pipeline.occupancy.model.OccupancyComparisonRecord;
import com.rtd.pipeline.occupancy.model.OccupancyStatus;
import com.rtd.pipeline.occupancy.model.VehicleType;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.datastream.DataStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for analyzing occupancy distribution patterns and vehicle type variations.
 * Implements the distribution analysis from the Arcadis IBI Group methodology.
 */
public class DistributionAnalyzer implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * Model for vehicle type capacity analysis.
     */
    public static class VehicleTypeAnalysis implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private int maxSeats;
        private int maxStands;
        private String vehicleTypeDescription;
        private Map<Integer, Map<OccupancyStatus, Long>> loadStatusDistribution = new HashMap<>();
        private long totalRecords;
        
        public VehicleTypeAnalysis() {}
        
        public VehicleTypeAnalysis(int maxSeats, int maxStands) {
            this.maxSeats = maxSeats;
            this.maxStands = maxStands;
            this.vehicleTypeDescription = getCapacityDescription(maxSeats, maxStands);
        }
        
        public int getMaxSeats() {
            return maxSeats;
        }
        
        public void setMaxSeats(int maxSeats) {
            this.maxSeats = maxSeats;
        }
        
        public int getMaxStands() {
            return maxStands;
        }
        
        public void setMaxStands(int maxStands) {
            this.maxStands = maxStands;
        }
        
        public String getVehicleTypeDescription() {
            return vehicleTypeDescription;
        }
        
        public void setVehicleTypeDescription(String vehicleTypeDescription) {
            this.vehicleTypeDescription = vehicleTypeDescription;
        }
        
        public Map<Integer, Map<OccupancyStatus, Long>> getLoadStatusDistribution() {
            return loadStatusDistribution;
        }
        
        public void setLoadStatusDistribution(Map<Integer, Map<OccupancyStatus, Long>> loadStatusDistribution) {
            this.loadStatusDistribution = loadStatusDistribution;
        }
        
        public long getTotalRecords() {
            return totalRecords;
        }
        
        public void setTotalRecords(long totalRecords) {
            this.totalRecords = totalRecords;
        }
        
        public void addLoadStatusRecord(int passengerLoad, OccupancyStatus status) {
            loadStatusDistribution.computeIfAbsent(passengerLoad, k -> new HashMap<>())
                .put(status, loadStatusDistribution.get(passengerLoad).getOrDefault(status, 0L) + 1);
            totalRecords++;
        }
        
        public int getTotalCapacity() {
            return maxSeats + maxStands;
        }
        
        private String getCapacityDescription(int seats, int stands) {
            if (seats == 37 && stands == 36) {
                return "Coach (37 seats, 36 standing)";
            } else if (seats == 36 && stands == 8) {
                return "Standard 40' (36 seats, 8 standing)";
            } else if (seats == 57 && stands == 23) {
                return "Articulated (57 seats, 23 standing)";
            } else {
                return String.format("Custom (%d seats, %d standing)", seats, stands);
            }
        }
        
        @Override
        public String toString() {
            return "VehicleTypeAnalysis{" +
                    "vehicleTypeDescription='" + vehicleTypeDescription + '\'' +
                    ", maxSeats=" + maxSeats +
                    ", maxStands=" + maxStands +
                    ", totalRecords=" + totalRecords +
                    '}';
        }
    }
    
    /**
     * Model for occupancy overlap analysis showing how same passenger loads
     * can result in different occupancy statuses due to vehicle capacity differences.
     */
    public static class OccupancyOverlapAnalysis implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private int passengerLoad;
        private Map<OccupancyStatus, Long> statusCounts = new HashMap<>();
        private Map<String, Long> vehicleTypeOverlaps = new HashMap<>();
        private boolean hasOverlap;
        
        public OccupancyOverlapAnalysis() {}
        
        public OccupancyOverlapAnalysis(int passengerLoad) {
            this.passengerLoad = passengerLoad;
        }
        
        public int getPassengerLoad() {
            return passengerLoad;
        }
        
        public void setPassengerLoad(int passengerLoad) {
            this.passengerLoad = passengerLoad;
        }
        
        public Map<OccupancyStatus, Long> getStatusCounts() {
            return statusCounts;
        }
        
        public void setStatusCounts(Map<OccupancyStatus, Long> statusCounts) {
            this.statusCounts = statusCounts;
        }
        
        public Map<String, Long> getVehicleTypeOverlaps() {
            return vehicleTypeOverlaps;
        }
        
        public void setVehicleTypeOverlaps(Map<String, Long> vehicleTypeOverlaps) {
            this.vehicleTypeOverlaps = vehicleTypeOverlaps;
        }
        
        public boolean isHasOverlap() {
            return hasOverlap;
        }
        
        public void setHasOverlap(boolean hasOverlap) {
            this.hasOverlap = hasOverlap;
        }
        
        public void addStatusRecord(OccupancyStatus status, String vehicleTypeDesc) {
            statusCounts.put(status, statusCounts.getOrDefault(status, 0L) + 1);
            vehicleTypeOverlaps.put(vehicleTypeDesc, vehicleTypeOverlaps.getOrDefault(vehicleTypeDesc, 0L) + 1);
            
            // Check if this passenger load has multiple occupancy statuses (overlap)
            hasOverlap = statusCounts.size() > 1;
        }
        
        @Override
        public String toString() {
            return "OccupancyOverlapAnalysis{" +
                    "passengerLoad=" + passengerLoad +
                    ", statusCounts=" + statusCounts +
                    ", hasOverlap=" + hasOverlap +
                    ", vehicleTypes=" + vehicleTypeOverlaps.size() +
                    '}';
        }
    }
    
    /**
     * Maps comparison records to vehicle type analysis.
     */
    public static class VehicleTypeMapper implements MapFunction<OccupancyComparisonRecord, VehicleTypeAnalysis> {
        @Override
        public VehicleTypeAnalysis map(OccupancyComparisonRecord record) {
            VehicleTypeAnalysis analysis = new VehicleTypeAnalysis(record.getMaxSeats(), record.getMaxStands());
            analysis.addLoadStatusRecord(record.getPassengerLoad(), record.getApcOccupancyStatus());
            return analysis;
        }
    }
    
    /**
     * Maps comparison records to occupancy overlap analysis.
     */
    public static class OccupancyOverlapMapper implements MapFunction<OccupancyComparisonRecord, OccupancyOverlapAnalysis> {
        @Override
        public OccupancyOverlapAnalysis map(OccupancyComparisonRecord record) {
            OccupancyOverlapAnalysis analysis = new OccupancyOverlapAnalysis(record.getPassengerLoad());
            String vehicleTypeDesc = getVehicleTypeDescription(record.getMaxSeats(), record.getMaxStands());
            analysis.addStatusRecord(record.getApcOccupancyStatus(), vehicleTypeDesc);
            return analysis;
        }
        
        private String getVehicleTypeDescription(int seats, int stands) {
            if (seats == 37 && stands == 36) return "Coach";
            if (seats == 36 && stands == 8) return "Standard40";
            if (seats == 57 && stands == 23) return "Articulated";
            return "Other";
        }
    }
    
    /**
     * Key selector for vehicle type grouping.
     */
    public static class VehicleTypeKeySelector implements KeySelector<VehicleTypeAnalysis, String> {
        @Override
        public String getKey(VehicleTypeAnalysis analysis) {
            return analysis.getMaxSeats() + "|" + analysis.getMaxStands();
        }
    }
    
    /**
     * Key selector for occupancy overlap grouping.
     */
    public static class OccupancyOverlapKeySelector implements KeySelector<OccupancyOverlapAnalysis, Integer> {
        @Override
        public Integer getKey(OccupancyOverlapAnalysis analysis) {
            return analysis.getPassengerLoad();
        }
    }
    
    /**
     * Reduces vehicle type analyses to aggregate data.
     */
    public static class VehicleTypeReducer implements ReduceFunction<VehicleTypeAnalysis> {
        @Override
        public VehicleTypeAnalysis reduce(VehicleTypeAnalysis analysis1, VehicleTypeAnalysis analysis2) {
            VehicleTypeAnalysis result = new VehicleTypeAnalysis(analysis1.getMaxSeats(), analysis1.getMaxStands());
            
            // Combine load status distributions
            Map<Integer, Map<OccupancyStatus, Long>> combinedDist = new HashMap<>(analysis1.getLoadStatusDistribution());
            for (Map.Entry<Integer, Map<OccupancyStatus, Long>> loadEntry : analysis2.getLoadStatusDistribution().entrySet()) {
                int load = loadEntry.getKey();
                Map<OccupancyStatus, Long> statusMap = combinedDist.computeIfAbsent(load, k -> new HashMap<>());
                for (Map.Entry<OccupancyStatus, Long> statusEntry : loadEntry.getValue().entrySet()) {
                    statusMap.put(statusEntry.getKey(), 
                        statusMap.getOrDefault(statusEntry.getKey(), 0L) + statusEntry.getValue());
                }
            }
            
            result.setLoadStatusDistribution(combinedDist);
            result.setTotalRecords(analysis1.getTotalRecords() + analysis2.getTotalRecords());
            return result;
        }
    }
    
    /**
     * Reduces occupancy overlap analyses to aggregate data.
     */
    public static class OccupancyOverlapReducer implements ReduceFunction<OccupancyOverlapAnalysis> {
        @Override
        public OccupancyOverlapAnalysis reduce(OccupancyOverlapAnalysis analysis1, OccupancyOverlapAnalysis analysis2) {
            OccupancyOverlapAnalysis result = new OccupancyOverlapAnalysis(analysis1.getPassengerLoad());
            
            // Combine status counts
            Map<OccupancyStatus, Long> combinedStatus = new HashMap<>(analysis1.getStatusCounts());
            for (Map.Entry<OccupancyStatus, Long> entry : analysis2.getStatusCounts().entrySet()) {
                combinedStatus.put(entry.getKey(), 
                    combinedStatus.getOrDefault(entry.getKey(), 0L) + entry.getValue());
            }
            
            // Combine vehicle type overlaps
            Map<String, Long> combinedVehicleTypes = new HashMap<>(analysis1.getVehicleTypeOverlaps());
            for (Map.Entry<String, Long> entry : analysis2.getVehicleTypeOverlaps().entrySet()) {
                combinedVehicleTypes.put(entry.getKey(), 
                    combinedVehicleTypes.getOrDefault(entry.getKey(), 0L) + entry.getValue());
            }
            
            result.setStatusCounts(combinedStatus);
            result.setVehicleTypeOverlaps(combinedVehicleTypes);
            result.setHasOverlap(combinedStatus.size() > 1);
            return result;
        }
    }
    
    /**
     * Analyzes occupancy distribution by vehicle type configuration.
     * 
     * @param comparisonStream Stream of occupancy comparison records
     * @return Stream of vehicle type analyses
     */
    public DataStream<VehicleTypeAnalysis> analyzeVehicleTypeDistribution(DataStream<OccupancyComparisonRecord> comparisonStream) {
        return comparisonStream
            .map(new VehicleTypeMapper())
            .keyBy(new VehicleTypeKeySelector())
            .reduce(new VehicleTypeReducer());
    }
    
    /**
     * Analyzes occupancy status overlaps caused by different vehicle capacities.
     * 
     * @param comparisonStream Stream of occupancy comparison records
     * @return Stream of occupancy overlap analyses
     */
    public DataStream<OccupancyOverlapAnalysis> analyzeOccupancyOverlaps(DataStream<OccupancyComparisonRecord> comparisonStream) {
        return comparisonStream
            .map(new OccupancyOverlapMapper())
            .keyBy(new OccupancyOverlapKeySelector())
            .reduce(new OccupancyOverlapReducer());
    }
    
    /**
     * Generates passenger experience impact analysis based on crowding levels.
     * 
     * @param comparisonStream Stream of occupancy comparison records
     * @return Stream of formatted passenger experience reports
     */
    public DataStream<String> generatePassengerExperienceAnalysis(DataStream<OccupancyComparisonRecord> comparisonStream) {
        return comparisonStream.map(record -> {
            double occupancyPercentage = record.getCalculatedOccupancyPercentage();
            double perceivedTimeIncrease = calculatePerceivedTimeIncrease(occupancyPercentage);
            
            return String.format("Vehicle %s (Route %s): Load=%d passengers, Occupancy=%.1f%%, " +
                    "Perceived time increase: +%.1f%% (Status: %s)", 
                    record.getVehicleId(), record.getRouteId(), record.getPassengerLoad(),
                    occupancyPercentage * 100, perceivedTimeIncrease, record.getApcOccupancyStatus());
        });
    }
    
    /**
     * Calculates perceived time increase based on vehicle crowding.
     * Based on research showing passenger experience deterioration with crowding.
     * 
     * @param occupancyPercentage Vehicle occupancy as percentage (0.0 to 1.0+)
     * @return Perceived time increase percentage
     */
    private double calculatePerceivedTimeIncrease(double occupancyPercentage) {
        // Based on standard 40ft bus example from Arcadis analysis:
        // At 37 passengers (full seats), 25% perceived time increase
        if (occupancyPercentage <= 0.5) {
            return 0.0; // No increase for low occupancy
        } else if (occupancyPercentage <= 0.75) {
            return 10.0; // Light crowding
        } else if (occupancyPercentage <= 0.95) {
            return 25.0; // Moderate crowding (seats full)
        } else if (occupancyPercentage <= 1.0) {
            return 40.0; // Heavy crowding (standing room)
        } else {
            return 60.0; // Severe crowding (over capacity)
        }
    }
}