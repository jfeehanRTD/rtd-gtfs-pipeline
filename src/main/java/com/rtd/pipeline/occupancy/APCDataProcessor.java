package com.rtd.pipeline.occupancy;

import com.rtd.pipeline.occupancy.model.APCData;
import com.rtd.pipeline.occupancy.model.OccupancyStatus;
import com.rtd.pipeline.occupancy.model.VehicleInfo;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Processor for APC (Automatic Passenger Counter) data.
 * Applies lag logic and calculates occupancy status for comparison with GTFS-RT data.
 */
public class APCDataProcessor implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final OccupancyAnalyzer occupancyAnalyzer;
    private final VehicleCapacityService capacityService;
    
    public APCDataProcessor(OccupancyAnalyzer occupancyAnalyzer, VehicleCapacityService capacityService) {
        this.occupancyAnalyzer = occupancyAnalyzer;
        this.capacityService = capacityService;
    }
    
    /**
     * Calculates APC occupancy status based on passenger load and vehicle capacity.
     */
    public static class APCOccupancyCalculator implements MapFunction<APCData, APCData> {
        private final OccupancyAnalyzer analyzer;
        private final VehicleCapacityService capacityService;
        
        public APCOccupancyCalculator(OccupancyAnalyzer analyzer, VehicleCapacityService capacityService) {
            this.analyzer = analyzer;
            this.capacityService = capacityService;
        }
        
        @Override
        public APCData map(APCData apcData) {
            // Get vehicle info for capacity calculation
            VehicleInfo vehicleInfo = capacityService.getVehicleInfoByCode(apcData.getVehicleCode())
                .orElse(capacityService.createDefaultVehicleInfo(null, apcData.getVehicleCode()));
            
            // Update APC data with vehicle capacity info
            apcData.setMaxSeats(vehicleInfo.getMaxSeats());
            apcData.setMaxStands(vehicleInfo.getMaxStands());
            
            // Calculate occupancy status
            OccupancyStatus occupancyStatus = analyzer.calculateOccupancyStatus(
                apcData.getPassengerLoad(), vehicleInfo);
            apcData.setApcOccupancyStatus(occupancyStatus);
            
            return apcData;
        }
    }
    
    /**
     * Applies lag to APC data to represent load between previous and current stop.
     * Partitioned by vehicle code, trip code, date, and pattern index.
     */
    public static class APCLagProcessor implements MapFunction<APCData, APCData> {
        private final Map<String, APCData> previousRecords = new HashMap<>();
        
        @Override
        public APCData map(APCData currentRecord) {
            String partitionKey = getPartitionKey(currentRecord);
            
            APCData previousRecord = previousRecords.get(partitionKey);
            if (previousRecord != null) {
                // Apply lag - use previous record's passenger load for current stop approach
                APCData laggedRecord = new APCData();
                copyRecord(currentRecord, laggedRecord);
                laggedRecord.setPassengerLoad(previousRecord.getPassengerLoad());
                laggedRecord.setApcOccupancyStatus(previousRecord.getApcOccupancyStatus());
                laggedRecord.setLagApplied(true);
                
                // Update previous record for next iteration
                previousRecords.put(partitionKey, currentRecord);
                
                return laggedRecord;
            } else {
                // First record in partition - store and return as is
                previousRecords.put(partitionKey, currentRecord);
                return currentRecord;
            }
        }
        
        private String getPartitionKey(APCData apcData) {
            return String.join("|",
                apcData.getVehicleCode() != null ? apcData.getVehicleCode() : "",
                apcData.getTripCode() != null ? apcData.getTripCode() : "",
                apcData.getOperatingDate() != null ? apcData.getOperatingDate().toLocalDate().toString() : "",
                String.valueOf(apcData.getPatternIndex())
            );
        }
        
        private void copyRecord(APCData source, APCData target) {
            target.setVehicleCode(source.getVehicleCode());
            target.setTripCode(source.getTripCode());
            target.setStopCode(source.getStopCode());
            target.setOperatingDate(source.getOperatingDate());
            target.setPatternIndex(source.getPatternIndex());
            target.setMaxSeats(source.getMaxSeats());
            target.setMaxStands(source.getMaxStands());
            target.setRouteId(source.getRouteId());
            target.setTimestamp(source.getTimestamp());
        }
    }
    
    /**
     * Filters out APC records with invalid or null occupancy status.
     */
    public static class ValidOccupancyFilter implements FilterFunction<APCData> {
        @Override
        public boolean filter(APCData apcData) {
            return apcData != null && 
                   apcData.isValid() && 
                   apcData.hasValidOccupancyStatus();
        }
    }
    
    /**
     * Filters APC data to only include records within specified date range.
     */
    public static class DateRangeFilter implements FilterFunction<APCData> {
        private final String startDate;
        private final String endDate;
        
        public DateRangeFilter(String startDate, String endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
        
        @Override
        public boolean filter(APCData apcData) {
            if (apcData.getOperatingDate() == null) {
                return false;
            }
            
            String recordDate = apcData.getOperatingDate().toLocalDate().toString();
            return (startDate == null || recordDate.compareTo(startDate) >= 0) &&
                   (endDate == null || recordDate.compareTo(endDate) <= 0);
        }
    }
    
    /**
     * Processes APC data stream with all transformation and filtering steps.
     * 
     * @param apcStream Raw APC data stream
     * @param startDate Start date filter (optional)
     * @param endDate End date filter (optional)
     * @param applyLag Whether to apply lag processing
     * @return Processed APC data stream
     */
    public DataStream<APCData> processAPCData(DataStream<APCData> apcStream, 
                                             String startDate, 
                                             String endDate, 
                                             boolean applyLag) {
        DataStream<APCData> processedStream = apcStream
            .map(new APCOccupancyCalculator(occupancyAnalyzer, capacityService));
        
        if (applyLag) {
            processedStream = processedStream.map(new APCLagProcessor());
        }
        
        processedStream = processedStream.filter(new ValidOccupancyFilter());
        
        if (startDate != null || endDate != null) {
            processedStream = processedStream.filter(new DateRangeFilter(startDate, endDate));
        }
        
        return processedStream;
    }
    
    /**
     * Simple APC processing without lag or date filtering.
     * 
     * @param apcStream Raw APC stream
     * @return Processed APC stream
     */
    public DataStream<APCData> processAPCDataSimple(DataStream<APCData> apcStream) {
        return apcStream
            .map(new APCOccupancyCalculator(occupancyAnalyzer, capacityService))
            .filter(new ValidOccupancyFilter());
    }
}