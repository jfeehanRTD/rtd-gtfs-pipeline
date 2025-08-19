package com.rtd.pipeline;

import com.rtd.pipeline.occupancy.*;
import com.rtd.pipeline.occupancy.AccuracyCalculator.AccuracyMetrics;
import com.rtd.pipeline.occupancy.DistributionAnalyzer.VehicleTypeAnalysis;
import com.rtd.pipeline.occupancy.ReportGenerator.OccupancyDistribution;
import com.rtd.pipeline.occupancy.model.*;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.util.Set;
import java.util.HashSet;

/**
 * Main pipeline for RTD Real-Time Vehicle Occupancy Accuracy Analysis.
 * Implements the comprehensive methodology from the Arcadis IBI Group study.
 * 
 * This pipeline:
 * 1. Ingests GTFS-RT Vehicle Position and APC data
 * 2. Processes and filters data according to study methodology
 * 3. Joins the datasets on common keys
 * 4. Calculates accuracy metrics by overall, date, and route
 * 5. Generates distribution and overlap analysis
 * 6. Produces comprehensive reports
 */
public class RTDOccupancyAccuracyPipeline {
    private static final Logger logger = LoggerFactory.getLogger(RTDOccupancyAccuracyPipeline.class);
    
    public static void main(String[] args) throws Exception {
        // Set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // Configure environment for occupancy analysis
        env.setParallelism(2);
        env.getConfig().setAutoWatermarkInterval(1000);
        
        logger.info("Starting RTD Occupancy Accuracy Analysis Pipeline");
        
        try {
            // Initialize services
            OccupancyAnalyzer occupancyAnalyzer = new OccupancyAnalyzer();
            VehicleCapacityService capacityService = new VehicleCapacityService();
            DataJoiningService joiningService = new DataJoiningService();
            AccuracyCalculator accuracyCalculator = new AccuracyCalculator();
            ReportGenerator reportGenerator = new ReportGenerator();
            DistributionAnalyzer distributionAnalyzer = new DistributionAnalyzer();
            
            // Load vehicle capacity data (this would typically come from a database or file)
            loadSampleVehicleCapacityData(capacityService);
            
            // Create data processors
            GTFSRTVPProcessor vpProcessor = new GTFSRTVPProcessor();
            APCDataProcessor apcProcessor = new APCDataProcessor(occupancyAnalyzer, capacityService);
            
            // Create mock data sources for demonstration
            // In production, these would be real GTFS-RT and APC data sources
            DataStream<GTFSRTVehiclePosition> gtfsrtStream = createMockGTFSRTStream(env);
            DataStream<APCData> apcStream = createMockAPCStream(env);
            
            // Configure watermarks for event time processing
            DataStream<GTFSRTVehiclePosition> gtfsrtWithWatermarks = gtfsrtStream
                .assignTimestampsAndWatermarks(
                    WatermarkStrategy.<GTFSRTVehiclePosition>forBoundedOutOfOrderness(Duration.ofMinutes(5))
                        .withTimestampAssigner((event, timestamp) -> 
                            event.getVehicleTimestamp() != null ? 
                                event.getVehicleTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 
                                System.currentTimeMillis())
                );
                
            DataStream<APCData> apcWithWatermarks = apcStream
                .assignTimestampsAndWatermarks(
                    WatermarkStrategy.<APCData>forBoundedOutOfOrderness(Duration.ofMinutes(5))
                        .withTimestampAssigner((event, timestamp) -> 
                            event.getTimestamp() != null ? 
                                event.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 
                                System.currentTimeMillis())
                );
            
            // Process GTFS-RT VP feed
            Set<String> validRoutes = getValidRoutes();
            Set<String> excludedPatterns = getExcludedPatterns();
            DataStream<GTFSRTVehiclePosition> processedVP = vpProcessor.processVPFeed(
                gtfsrtWithWatermarks, validRoutes, excludedPatterns);
            
            // Process APC data
            DataStream<APCData> processedAPC = apcProcessor.processAPCData(
                apcWithWatermarks, "2023-08-15", "2023-08-18", true);
            
            // Join the datasets
            DataStream<OccupancyComparisonRecord> joinedData = joiningService.joinOccupancyData(
                processedVP, processedAPC, 10); // 10-minute window
            
            // Calculate accuracy metrics
            DataStream<AccuracyMetrics> overallAccuracy = accuracyCalculator.calculateOverallAccuracy(joinedData);
            DataStream<AccuracyMetrics> dateAccuracy = accuracyCalculator.calculateDateAccuracy(joinedData);
            DataStream<AccuracyMetrics> routeAccuracy = accuracyCalculator.calculateRouteAccuracy(joinedData);
            
            // Generate distribution analysis
            DataStream<OccupancyDistribution> occupancyDistributions = reportGenerator.generateOccupancyDistribution(joinedData);
            DataStream<VehicleTypeAnalysis> vehicleTypeAnalysis = distributionAnalyzer.analyzeVehicleTypeDistribution(joinedData);
            
            // Generate reports
            DataStream<String> accuracyReports = reportGenerator.generateAccuracyReport(
                overallAccuracy.union(dateAccuracy, routeAccuracy));
            DataStream<String> distributionReports = reportGenerator.generateDistributionReport(occupancyDistributions);
            DataStream<String> passengerExperienceAnalysis = distributionAnalyzer.generatePassengerExperienceAnalysis(joinedData);
            
            // Output results
            overallAccuracy.print("Overall Accuracy");
            dateAccuracy.print("Date Accuracy");
            routeAccuracy.print("Route Accuracy");
            occupancyDistributions.print("Occupancy Distribution");
            vehicleTypeAnalysis.print("Vehicle Type Analysis");
            accuracyReports.print("Accuracy Reports");
            distributionReports.print("Distribution Reports");
            passengerExperienceAnalysis.print("Passenger Experience");
            
            // Output joined data for detailed analysis
            joinedData.print("Joined Data");
            
            // Execute the pipeline
            logger.info("Executing RTD Occupancy Accuracy Analysis Pipeline");
            env.execute("RTD Occupancy Accuracy Analysis Pipeline");
            
        } catch (Exception e) {
            logger.error("Error in RTD Occupancy Accuracy Analysis Pipeline", e);
            throw e;
        }
    }
    
    /**
     * Loads sample vehicle capacity data for demonstration.
     * In production, this would load from bus_info database table.
     */
    private static void loadSampleVehicleCapacityData(VehicleCapacityService capacityService) {
        logger.info("Loading sample vehicle capacity data");
        
        // Standard 40ft buses (36 seats, 8 standing)
        for (int i = 1; i <= 50; i++) {
            VehicleInfo standardBus = new VehicleInfo(
                "V" + String.format("%03d", i),
                "BUS" + String.format("%03d", i),
                VehicleType.STANDARD_40FT,
                36, 8
            );
            capacityService.registerVehicle(standardBus);
        }
        
        // Coach buses (37 seats, 36 standing)
        for (int i = 51; i <= 80; i++) {
            VehicleInfo coachBus = new VehicleInfo(
                "V" + String.format("%03d", i),
                "BUS" + String.format("%03d", i),
                VehicleType.COACH,
                37, 36
            );
            capacityService.registerVehicle(coachBus);
        }
        
        // Articulated buses (57 seats, 23 standing)
        for (int i = 81; i <= 100; i++) {
            VehicleInfo articulatedBus = new VehicleInfo(
                "V" + String.format("%03d", i),
                "BUS" + String.format("%03d", i),
                VehicleType.ARTICULATED,
                57, 23
            );
            capacityService.registerVehicle(articulatedBus);
        }
        
        logger.info("Loaded capacity data for {} vehicles", capacityService.getRegisteredVehicleCount());
    }
    
    /**
     * Creates a mock GTFS-RT vehicle position stream for demonstration.
     * In production, this would connect to actual GTFS-RT feed.
     */
    private static DataStream<GTFSRTVehiclePosition> createMockGTFSRTStream(StreamExecutionEnvironment env) {
        // This is a placeholder - in production, use actual GTFS-RT source
        return env.fromElements(
            createSampleGTFSRTRecord("V001", "TRIP001", "15", "STOP001", OccupancyStatus.MANY_SEATS_AVAILABLE, 0.45),
            createSampleGTFSRTRecord("V002", "TRIP002", "121", "STOP002", OccupancyStatus.FEW_SEATS_AVAILABLE, 0.68),
            createSampleGTFSRTRecord("V051", "TRIP003", "0", "STOP003", OccupancyStatus.STANDING_ROOM_ONLY, 0.82),
            createSampleGTFSRTRecord("V081", "TRIP004", "105", "STOP004", OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY, 0.91),
            createSampleGTFSRTRecord("V025", "TRIP005", "FF1", "STOP005", OccupancyStatus.EMPTY, 0.12)
        );
    }
    
    /**
     * Creates a mock APC data stream for demonstration.
     * In production, this would connect to actual APC data source.
     */
    private static DataStream<APCData> createMockAPCStream(StreamExecutionEnvironment env) {
        // This is a placeholder - in production, use actual APC data source
        return env.fromElements(
            createSampleAPCRecord("BUS001", "TRIP001", "STOP001", 16), // Should match GTFS-RT MANY_SEATS_AVAILABLE
            createSampleAPCRecord("BUS002", "TRIP002", "STOP002", 20), // Different from GTFS-RT (should be MANY_SEATS_AVAILABLE)
            createSampleAPCRecord("BUS051", "TRIP003", "STOP003", 55), // Should match GTFS-RT STANDING_ROOM_ONLY
            createSampleAPCRecord("BUS081", "TRIP004", "STOP004", 75), // Should match GTFS-RT CRUSHED_STANDING_ROOM_ONLY
            createSampleAPCRecord("BUS025", "TRIP005", "STOP005", 3)   // Should match GTFS-RT EMPTY
        );
    }
    
    private static GTFSRTVehiclePosition createSampleGTFSRTRecord(String vehicleId, String tripId, 
                                                                 String routeId, String stopId, 
                                                                 OccupancyStatus status, double percentage) {
        GTFSRTVehiclePosition vp = new GTFSRTVehiclePosition(vehicleId, tripId, routeId, stopId);
        vp.setCurrentStatus("IN_TRANSIT_TO");
        vp.setOccupancyStatus(status);
        vp.setOccupancyPercentage(percentage);
        vp.setVehicleTimestamp(java.time.LocalDateTime.of(2023, 8, 15, 10, 30));
        vp.setServiceDate(java.time.LocalDateTime.of(2023, 8, 15, 10, 30));
        vp.setLatitude(39.7392);
        vp.setLongitude(-104.9903);
        return vp;
    }
    
    private static APCData createSampleAPCRecord(String vehicleCode, String tripCode, String stopCode, int passengerLoad) {
        APCData apc = new APCData(vehicleCode, tripCode, stopCode, 
                                 java.time.LocalDateTime.of(2023, 8, 15, 10, 30), passengerLoad);
        apc.setTimestamp(java.time.LocalDateTime.of(2023, 8, 15, 10, 30));
        return apc;
    }
    
    /**
     * Returns set of valid route IDs for filtering.
     * Based on routes present in APC data from the study.
     */
    private static Set<String> getValidRoutes() {
        Set<String> validRoutes = new HashSet<>();
        // Add sample routes from the Arcadis study
        validRoutes.add("0");
        validRoutes.add("15");
        validRoutes.add("121");
        validRoutes.add("105");
        validRoutes.add("FF1");
        validRoutes.add("FF2");
        validRoutes.add("SKIP");
        validRoutes.add("BOLT");
        validRoutes.add("DASH");
        return validRoutes;
    }
    
    /**
     * Returns set of route patterns to exclude (mainly light rail).
     */
    private static Set<String> getExcludedPatterns() {
        Set<String> excludedPatterns = new HashSet<>();
        excludedPatterns.add("RAIL");
        excludedPatterns.add("TRAIN");
        excludedPatterns.add("LINE"); // A-LINE, B-LINE, etc.
        return excludedPatterns;
    }
}