package com.rtd.pipeline.occupancy;

import com.rtd.pipeline.occupancy.AccuracyCalculator.AccuracyMetrics;
import com.rtd.pipeline.occupancy.model.OccupancyComparisonRecord;
import com.rtd.pipeline.occupancy.model.OccupancyStatus;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.datastream.DataStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Service for generating comprehensive reports on occupancy accuracy analysis.
 * Produces reports matching the format of the Arcadis IBI Group study.
 */
public class ReportGenerator implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * Model for occupancy distribution analysis.
     */
    public static class OccupancyDistribution implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String category; // GTFS-RT or APC
        private Map<OccupancyStatus, Long> statusCounts = new HashMap<>();
        private long totalRecords;
        
        public OccupancyDistribution() {}
        
        public OccupancyDistribution(String category) {
            this.category = category;
        }
        
        public String getCategory() {
            return category;
        }
        
        public void setCategory(String category) {
            this.category = category;
        }
        
        public Map<OccupancyStatus, Long> getStatusCounts() {
            return statusCounts;
        }
        
        public void setStatusCounts(Map<OccupancyStatus, Long> statusCounts) {
            this.statusCounts = statusCounts;
        }
        
        public long getTotalRecords() {
            return totalRecords;
        }
        
        public void setTotalRecords(long totalRecords) {
            this.totalRecords = totalRecords;
        }
        
        public void incrementStatus(OccupancyStatus status) {
            statusCounts.put(status, statusCounts.getOrDefault(status, 0L) + 1);
            totalRecords++;
        }
        
        public double getStatusPercentage(OccupancyStatus status) {
            if (totalRecords == 0) return 0.0;
            return (double) statusCounts.getOrDefault(status, 0L) / totalRecords * 100.0;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("OccupancyDistribution{category='").append(category).append("', totalRecords=").append(totalRecords);
            sb.append(", distribution=[");
            for (Map.Entry<OccupancyStatus, Long> entry : statusCounts.entrySet()) {
                double percentage = getStatusPercentage(entry.getKey());
                sb.append(entry.getKey()).append(": ").append(entry.getValue())
                  .append(" (").append(String.format("%.1f", percentage)).append("%), ");
            }
            sb.append("]}");
            return sb.toString();
        }
    }
    
    /**
     * Model for passenger load analysis by occupancy status.
     */
    public static class PassengerLoadAnalysis implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private int passengerLoad;
        private Map<OccupancyStatus, Long> statusCounts = new HashMap<>();
        private long totalEvents;
        
        public PassengerLoadAnalysis() {}
        
        public PassengerLoadAnalysis(int passengerLoad) {
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
        
        public long getTotalEvents() {
            return totalEvents;
        }
        
        public void setTotalEvents(long totalEvents) {
            this.totalEvents = totalEvents;
        }
        
        public void incrementStatus(OccupancyStatus status) {
            statusCounts.put(status, statusCounts.getOrDefault(status, 0L) + 1);
            totalEvents++;
        }
        
        @Override
        public String toString() {
            return "PassengerLoadAnalysis{" +
                    "passengerLoad=" + passengerLoad +
                    ", statusCounts=" + statusCounts +
                    ", totalEvents=" + totalEvents +
                    '}';
        }
    }
    
    /**
     * Maps comparison records to GTFS-RT occupancy distribution.
     */
    public static class GTFSRTDistributionMapper implements MapFunction<OccupancyComparisonRecord, OccupancyDistribution> {
        @Override
        public OccupancyDistribution map(OccupancyComparisonRecord record) {
            OccupancyDistribution dist = new OccupancyDistribution("GTFS-RT");
            dist.incrementStatus(record.getGtfsrtOccupancyStatus());
            return dist;
        }
    }
    
    /**
     * Maps comparison records to APC occupancy distribution.
     */
    public static class APCDistributionMapper implements MapFunction<OccupancyComparisonRecord, OccupancyDistribution> {
        @Override
        public OccupancyDistribution map(OccupancyComparisonRecord record) {
            OccupancyDistribution dist = new OccupancyDistribution("APC");
            dist.incrementStatus(record.getApcOccupancyStatus());
            return dist;
        }
    }
    
    /**
     * Maps comparison records to passenger load analysis.
     */
    public static class PassengerLoadMapper implements MapFunction<OccupancyComparisonRecord, PassengerLoadAnalysis> {
        @Override
        public PassengerLoadAnalysis map(OccupancyComparisonRecord record) {
            PassengerLoadAnalysis analysis = new PassengerLoadAnalysis(record.getPassengerLoad());
            analysis.incrementStatus(record.getApcOccupancyStatus());
            return analysis;
        }
    }
    
    /**
     * Key selector for occupancy distribution grouping.
     */
    public static class DistributionKeySelector implements KeySelector<OccupancyDistribution, String> {
        @Override
        public String getKey(OccupancyDistribution dist) {
            return dist.getCategory();
        }
    }
    
    /**
     * Key selector for passenger load grouping.
     */
    public static class PassengerLoadKeySelector implements KeySelector<PassengerLoadAnalysis, Integer> {
        @Override
        public Integer getKey(PassengerLoadAnalysis analysis) {
            return analysis.getPassengerLoad();
        }
    }
    
    /**
     * Reduces occupancy distributions to aggregate counts.
     */
    public static class DistributionReducer implements ReduceFunction<OccupancyDistribution> {
        @Override
        public OccupancyDistribution reduce(OccupancyDistribution dist1, OccupancyDistribution dist2) {
            OccupancyDistribution result = new OccupancyDistribution(dist1.getCategory());
            
            // Combine status counts from both distributions
            Map<OccupancyStatus, Long> combinedCounts = new HashMap<>(dist1.getStatusCounts());
            for (Map.Entry<OccupancyStatus, Long> entry : dist2.getStatusCounts().entrySet()) {
                combinedCounts.put(entry.getKey(), 
                    combinedCounts.getOrDefault(entry.getKey(), 0L) + entry.getValue());
            }
            
            result.setStatusCounts(combinedCounts);
            result.setTotalRecords(dist1.getTotalRecords() + dist2.getTotalRecords());
            return result;
        }
    }
    
    /**
     * Reduces passenger load analysis to aggregate counts.
     */
    public static class PassengerLoadReducer implements ReduceFunction<PassengerLoadAnalysis> {
        @Override
        public PassengerLoadAnalysis reduce(PassengerLoadAnalysis analysis1, PassengerLoadAnalysis analysis2) {
            PassengerLoadAnalysis result = new PassengerLoadAnalysis(analysis1.getPassengerLoad());
            
            // Combine status counts from both analyses
            Map<OccupancyStatus, Long> combinedCounts = new HashMap<>(analysis1.getStatusCounts());
            for (Map.Entry<OccupancyStatus, Long> entry : analysis2.getStatusCounts().entrySet()) {
                combinedCounts.put(entry.getKey(), 
                    combinedCounts.getOrDefault(entry.getKey(), 0L) + entry.getValue());
            }
            
            result.setStatusCounts(combinedCounts);
            result.setTotalEvents(analysis1.getTotalEvents() + analysis2.getTotalEvents());
            return result;
        }
    }
    
    /**
     * Generates occupancy distribution comparison between GTFS-RT and APC data.
     * 
     * @param comparisonStream Stream of occupancy comparison records
     * @return Stream of occupancy distributions
     */
    public DataStream<OccupancyDistribution> generateOccupancyDistribution(DataStream<OccupancyComparisonRecord> comparisonStream) {
        DataStream<OccupancyDistribution> gtfsrtDist = comparisonStream
            .map(new GTFSRTDistributionMapper())
            .keyBy(new DistributionKeySelector())
            .reduce(new DistributionReducer());
            
        DataStream<OccupancyDistribution> apcDist = comparisonStream
            .map(new APCDistributionMapper())
            .keyBy(new DistributionKeySelector())
            .reduce(new DistributionReducer());
            
        return gtfsrtDist.union(apcDist);
    }
    
    /**
     * Generates passenger load analysis showing distribution by load level.
     * 
     * @param comparisonStream Stream of occupancy comparison records
     * @return Stream of passenger load analyses
     */
    public DataStream<PassengerLoadAnalysis> generatePassengerLoadAnalysis(DataStream<OccupancyComparisonRecord> comparisonStream) {
        return comparisonStream
            .map(new PassengerLoadMapper())
            .keyBy(new PassengerLoadKeySelector())
            .reduce(new PassengerLoadReducer());
    }
    
    /**
     * Generates a comprehensive accuracy report summary.
     * 
     * @param accuracyMetrics Stream of accuracy metrics
     * @return Formatted report string stream
     */
    public DataStream<String> generateAccuracyReport(DataStream<AccuracyMetrics> accuracyMetrics) {
        return accuracyMetrics.map(metrics -> {
            StringBuilder report = new StringBuilder();
            report.append("=== RTD Real-Time Vehicle Occupancy Accuracy Report ===\n");
            report.append("Category: ").append(metrics.getCategory()).append("\n");
            if (metrics.getSubcategory() != null && !metrics.getSubcategory().isEmpty()) {
                report.append("Subcategory: ").append(metrics.getSubcategory()).append("\n");
            }
            report.append("Total VP Feed Records: ").append(metrics.getTotalVPRecords()).append("\n");
            report.append("Total Joined APC Records: ").append(metrics.getTotalJoinedRecords()).append("\n");
            report.append("Joined Percentage: ").append(String.format("%.1f", metrics.getJoinedPercentage())).append("%\n");
            report.append("Matched Occupancy Status Records: ").append(metrics.getMatchedOccupancyRecords()).append("\n");
            report.append("Accuracy Percentage: ").append(String.format("%.1f", metrics.getAccuracyPercentage())).append("%\n");
            report.append("========================================\n");
            return report.toString();
        });
    }
    
    /**
     * Generates distribution report summary.
     * 
     * @param distributions Stream of occupancy distributions
     * @return Formatted distribution report stream
     */
    public DataStream<String> generateDistributionReport(DataStream<OccupancyDistribution> distributions) {
        return distributions.map(dist -> {
            StringBuilder report = new StringBuilder();
            report.append("=== Occupancy Distribution Analysis ===\n");
            report.append("Feed Type: ").append(dist.getCategory()).append("\n");
            report.append("Total Records: ").append(dist.getTotalRecords()).append("\n");
            report.append("Distribution:\n");
            
            for (OccupancyStatus status : OccupancyStatus.values()) {
                long count = dist.getStatusCounts().getOrDefault(status, 0L);
                double percentage = dist.getStatusPercentage(status);
                if (count > 0) {
                    report.append("  ").append(status).append(": ")
                          .append(count).append(" (")
                          .append(String.format("%.1f", percentage)).append("%)\n");
                }
            }
            report.append("=====================================\n");
            return report.toString();
        });
    }
}