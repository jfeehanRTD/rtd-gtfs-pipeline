package com.rtd.pipeline.occupancy;

import com.rtd.pipeline.occupancy.model.APCData;
import com.rtd.pipeline.occupancy.model.GTFSRTVehiclePosition;
import com.rtd.pipeline.occupancy.model.OccupancyComparisonRecord;
import org.apache.flink.api.common.functions.CoGroupFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import java.time.Duration;
import org.apache.flink.util.Collector;
import java.io.Serializable;
import java.util.Iterator;

/**
 * Service for joining GTFS-RT Vehicle Position data with APC data
 * based on the methodology from the Arcadis IBI Group study.
 */
public class DataJoiningService implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * Key selector for GTFS-RT Vehicle Position records.
     */
    public static class GTFSRTVPKeySelector implements KeySelector<GTFSRTVehiclePosition, String> {
        @Override
        public String getKey(GTFSRTVehiclePosition vp) {
            return createJoinKey(
                vp.getVehicleId(),
                vp.getTripId(), 
                vp.getStopId(),
                vp.getServiceDate() != null ? vp.getServiceDate().toLocalDate().toString() : ""
            );
        }
    }
    
    /**
     * Key selector for APC data records.
     */
    public static class APCDataKeySelector implements KeySelector<APCData, String> {
        @Override
        public String getKey(APCData apc) {
            return createJoinKey(
                apc.getVehicleCode(),
                apc.getTripCode(),
                apc.getStopCode(), 
                apc.getOperatingDate() != null ? apc.getOperatingDate().toLocalDate().toString() : ""
            );
        }
    }
    
    /**
     * CoGroup function that performs the actual joining of GTFS-RT VP and APC data.
     */
    public static class OccupancyDataJoiner implements CoGroupFunction<GTFSRTVehiclePosition, APCData, OccupancyComparisonRecord> {
        
        @Override
        public void coGroup(Iterable<GTFSRTVehiclePosition> vpRecords, 
                           Iterable<APCData> apcRecords, 
                           Collector<OccupancyComparisonRecord> collector) {
            
            Iterator<GTFSRTVehiclePosition> vpIter = vpRecords.iterator();
            Iterator<APCData> apcIter = apcRecords.iterator();
            
            // Process all combinations of VP and APC records with matching keys
            while (vpIter.hasNext()) {
                GTFSRTVehiclePosition vpRecord = vpIter.next();
                
                // Reset APC iterator for each VP record
                Iterator<APCData> apcIterInner = apcRecords.iterator();
                while (apcIterInner.hasNext()) {
                    APCData apcRecord = apcIterInner.next();
                    
                    // Create comparison record
                    OccupancyComparisonRecord comparison = createComparisonRecord(vpRecord, apcRecord);
                    if (comparison.isValid()) {
                        calculateComparisonMetrics(comparison);
                        collector.collect(comparison);
                    }
                }
            }
        }
        
        private OccupancyComparisonRecord createComparisonRecord(GTFSRTVehiclePosition vpRecord, APCData apcRecord) {
            OccupancyComparisonRecord record = new OccupancyComparisonRecord();
            
            // Common fields
            record.setVehicleId(vpRecord.getVehicleId());
            record.setTripId(vpRecord.getTripId());
            record.setStopId(vpRecord.getStopId());
            record.setRouteId(vpRecord.getRouteId());
            record.setServiceDate(vpRecord.getServiceDate());
            
            // GTFS-RT fields
            record.setGtfsrtOccupancyStatus(vpRecord.getOccupancyStatus());
            record.setGtfsrtOccupancyPercentage(vpRecord.getOccupancyPercentage());
            record.setGtfsrtTimestamp(vpRecord.getVehicleTimestamp());
            record.setCurrentStatus(vpRecord.getCurrentStatus());
            record.setCongestionLevel(vpRecord.getCongestionLevel());
            
            // APC fields
            record.setPassengerLoad(apcRecord.getPassengerLoad());
            record.setApcOccupancyStatus(apcRecord.getApcOccupancyStatus());
            record.setMaxSeats(apcRecord.getMaxSeats());
            record.setMaxStands(apcRecord.getMaxStands());
            record.setApcTimestamp(apcRecord.getTimestamp());
            record.setLagApplied(apcRecord.isLagApplied());
            
            return record;
        }
        
        private void calculateComparisonMetrics(OccupancyComparisonRecord record) {
            // Check if occupancy statuses match
            boolean statusMatch = record.getGtfsrtOccupancyStatus() == record.getApcOccupancyStatus();
            record.setOccupancyStatusMatch(statusMatch);
            
            // Calculate numeric difference between occupancy statuses
            int gtfsrtNumeric = record.getGtfsrtOccupancyStatus().getNumericValue();
            int apcNumeric = record.getApcOccupancyStatus().getNumericValue();
            record.setOccupancyStatusDifference(Math.abs(gtfsrtNumeric - apcNumeric));
            
            // Calculate percentage difference
            double gtfsrtPercentage = record.getGtfsrtOccupancyPercentage();
            double apcPercentage = record.getCalculatedOccupancyPercentage();
            record.setOccupancyPercentageDifference(Math.abs(gtfsrtPercentage - apcPercentage));
        }
    }
    
    /**
     * Joins GTFS-RT VP and APC data streams to create occupancy comparison records.
     * 
     * @param vpStream GTFS-RT Vehicle Position stream
     * @param apcStream APC data stream
     * @param windowSizeMinutes Window size for joining in minutes
     * @return Stream of joined occupancy comparison records
     */
    public DataStream<OccupancyComparisonRecord> joinOccupancyData(
            DataStream<GTFSRTVehiclePosition> vpStream,
            DataStream<APCData> apcStream,
            int windowSizeMinutes) {
        
        return vpStream
            .coGroup(apcStream)
            .where(new GTFSRTVPKeySelector())
            .equalTo(new APCDataKeySelector())
            .window(TumblingProcessingTimeWindows.of(Duration.ofMinutes(windowSizeMinutes)))
            .apply(new OccupancyDataJoiner());
    }
    
    /**
     * Joins data streams with default 5-minute window.
     * 
     * @param vpStream GTFS-RT Vehicle Position stream
     * @param apcStream APC data stream
     * @return Stream of joined occupancy comparison records
     */
    public DataStream<OccupancyComparisonRecord> joinOccupancyData(
            DataStream<GTFSRTVehiclePosition> vpStream,
            DataStream<APCData> apcStream) {
        return joinOccupancyData(vpStream, apcStream, 5);
    }
    
    /**
     * Creates a standardized join key from the component fields.
     * 
     * @param vehicleId Vehicle ID or code
     * @param tripId Trip ID or code  
     * @param stopId Stop ID or code
     * @param serviceDate Service date as string
     * @return Standardized join key
     */
    private static String createJoinKey(String vehicleId, String tripId, String stopId, String serviceDate) {
        return String.join("|",
            vehicleId != null ? vehicleId : "",
            tripId != null ? tripId : "",
            stopId != null ? stopId : "",
            serviceDate != null ? serviceDate : ""
        );
    }
}