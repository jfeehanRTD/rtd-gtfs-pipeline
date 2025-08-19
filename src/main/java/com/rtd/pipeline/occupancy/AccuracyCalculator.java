package com.rtd.pipeline.occupancy;

import com.rtd.pipeline.occupancy.model.OccupancyComparisonRecord;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.datastream.DataStream;
import java.io.Serializable;
import java.util.Objects;

/**
 * Service for calculating occupancy accuracy metrics between GTFS-RT and APC data.
 * Implements the accuracy analysis methodology from the Arcadis IBI Group study.
 */
public class AccuracyCalculator implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * Model for accuracy metrics aggregation.
     */
    public static class AccuracyMetrics implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String category; // Overall, date, route, etc.
        private String subcategory; // Specific date or route ID
        private long totalVPRecords;
        private long totalJoinedRecords;
        private long matchedOccupancyRecords;
        private double joinedPercentage;
        private double accuracyPercentage;
        
        public AccuracyMetrics() {}
        
        public AccuracyMetrics(String category, String subcategory) {
            this.category = category;
            this.subcategory = subcategory;
        }
        
        // Getters and setters
        public String getCategory() {
            return category;
        }
        
        public void setCategory(String category) {
            this.category = category;
        }
        
        public String getSubcategory() {
            return subcategory;
        }
        
        public void setSubcategory(String subcategory) {
            this.subcategory = subcategory;
        }
        
        public long getTotalVPRecords() {
            return totalVPRecords;
        }
        
        public void setTotalVPRecords(long totalVPRecords) {
            this.totalVPRecords = totalVPRecords;
        }
        
        public long getTotalJoinedRecords() {
            return totalJoinedRecords;
        }
        
        public void setTotalJoinedRecords(long totalJoinedRecords) {
            this.totalJoinedRecords = totalJoinedRecords;
        }
        
        public long getMatchedOccupancyRecords() {
            return matchedOccupancyRecords;
        }
        
        public void setMatchedOccupancyRecords(long matchedOccupancyRecords) {
            this.matchedOccupancyRecords = matchedOccupancyRecords;
        }
        
        public double getJoinedPercentage() {
            return joinedPercentage;
        }
        
        public void setJoinedPercentage(double joinedPercentage) {
            this.joinedPercentage = joinedPercentage;
        }
        
        public double getAccuracyPercentage() {
            return accuracyPercentage;
        }
        
        public void setAccuracyPercentage(double accuracyPercentage) {
            this.accuracyPercentage = accuracyPercentage;
        }
        
        /**
         * Calculates derived percentages based on counts.
         */
        public void calculatePercentages() {
            if (totalVPRecords > 0) {
                joinedPercentage = (double) totalJoinedRecords / totalVPRecords * 100.0;
            }
            if (totalJoinedRecords > 0) {
                accuracyPercentage = (double) matchedOccupancyRecords / totalJoinedRecords * 100.0;
            }
        }
        
        @Override
        public String toString() {
            return "AccuracyMetrics{" +
                    "category='" + category + '\'' +
                    ", subcategory='" + subcategory + '\'' +
                    ", totalVPRecords=" + totalVPRecords +
                    ", totalJoinedRecords=" + totalJoinedRecords +
                    ", matchedOccupancyRecords=" + matchedOccupancyRecords +
                    ", joinedPercentage=" + String.format("%.1f", joinedPercentage) + "%" +
                    ", accuracyPercentage=" + String.format("%.1f", accuracyPercentage) + "%" +
                    '}';
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AccuracyMetrics that = (AccuracyMetrics) o;
            return Objects.equals(category, that.category) &&
                   Objects.equals(subcategory, that.subcategory);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(category, subcategory);
        }
    }
    
    /**
     * Filters only valid comparison records for accuracy calculation.
     */
    public static class ValidComparisonFilter implements FilterFunction<OccupancyComparisonRecord> {
        @Override
        public boolean filter(OccupancyComparisonRecord record) {
            return record != null && record.isValid();
        }
    }
    
    /**
     * Maps comparison records to accuracy metrics for overall analysis.
     */
    public static class OverallAccuracyMapper implements MapFunction<OccupancyComparisonRecord, AccuracyMetrics> {
        @Override
        public AccuracyMetrics map(OccupancyComparisonRecord record) {
            AccuracyMetrics metrics = new AccuracyMetrics("Full Dataset", "");
            metrics.setTotalJoinedRecords(1);
            metrics.setMatchedOccupancyRecords(record.isOccupancyStatusMatch() ? 1 : 0);
            return metrics;
        }
    }
    
    /**
     * Maps comparison records to accuracy metrics by date.
     */
    public static class DateAccuracyMapper implements MapFunction<OccupancyComparisonRecord, AccuracyMetrics> {
        @Override
        public AccuracyMetrics map(OccupancyComparisonRecord record) {
            String date = record.getServiceDate() != null ? 
                record.getServiceDate().toLocalDate().toString() : "Unknown";
            AccuracyMetrics metrics = new AccuracyMetrics("By Date", date);
            metrics.setTotalJoinedRecords(1);
            metrics.setMatchedOccupancyRecords(record.isOccupancyStatusMatch() ? 1 : 0);
            return metrics;
        }
    }
    
    /**
     * Maps comparison records to accuracy metrics by route.
     */
    public static class RouteAccuracyMapper implements MapFunction<OccupancyComparisonRecord, AccuracyMetrics> {
        @Override
        public AccuracyMetrics map(OccupancyComparisonRecord record) {
            String route = record.getRouteId() != null ? record.getRouteId() : "Unknown";
            AccuracyMetrics metrics = new AccuracyMetrics("By Route", route);
            metrics.setTotalJoinedRecords(1);
            metrics.setMatchedOccupancyRecords(record.isOccupancyStatusMatch() ? 1 : 0);
            return metrics;
        }
    }
    
    /**
     * Key selector for grouping accuracy metrics.
     */
    public static class AccuracyMetricsKeySelector implements KeySelector<AccuracyMetrics, String> {
        @Override
        public String getKey(AccuracyMetrics metrics) {
            return metrics.getCategory() + "|" + (metrics.getSubcategory() != null ? metrics.getSubcategory() : "");
        }
    }
    
    /**
     * Reduces accuracy metrics to aggregate counts.
     */
    public static class AccuracyMetricsReducer implements ReduceFunction<AccuracyMetrics> {
        @Override
        public AccuracyMetrics reduce(AccuracyMetrics metrics1, AccuracyMetrics metrics2) {
            AccuracyMetrics result = new AccuracyMetrics(metrics1.getCategory(), metrics1.getSubcategory());
            result.setTotalVPRecords(metrics1.getTotalVPRecords() + metrics2.getTotalVPRecords());
            result.setTotalJoinedRecords(metrics1.getTotalJoinedRecords() + metrics2.getTotalJoinedRecords());
            result.setMatchedOccupancyRecords(metrics1.getMatchedOccupancyRecords() + metrics2.getMatchedOccupancyRecords());
            result.calculatePercentages();
            return result;
        }
    }
    
    /**
     * Calculates overall accuracy metrics from comparison records.
     * 
     * @param comparisonStream Stream of occupancy comparison records
     * @return Stream of overall accuracy metrics
     */
    public DataStream<AccuracyMetrics> calculateOverallAccuracy(DataStream<OccupancyComparisonRecord> comparisonStream) {
        return comparisonStream
            .filter(new ValidComparisonFilter())
            .map(new OverallAccuracyMapper())
            .keyBy(new AccuracyMetricsKeySelector())
            .reduce(new AccuracyMetricsReducer())
            .map(metrics -> {
                metrics.calculatePercentages();
                return metrics;
            });
    }
    
    /**
     * Calculates accuracy metrics grouped by date.
     * 
     * @param comparisonStream Stream of occupancy comparison records
     * @return Stream of date-based accuracy metrics
     */
    public DataStream<AccuracyMetrics> calculateDateAccuracy(DataStream<OccupancyComparisonRecord> comparisonStream) {
        return comparisonStream
            .filter(new ValidComparisonFilter())
            .map(new DateAccuracyMapper())
            .keyBy(new AccuracyMetricsKeySelector())
            .reduce(new AccuracyMetricsReducer())
            .map(metrics -> {
                metrics.calculatePercentages();
                return metrics;
            });
    }
    
    /**
     * Calculates accuracy metrics grouped by route.
     * 
     * @param comparisonStream Stream of occupancy comparison records
     * @return Stream of route-based accuracy metrics
     */
    public DataStream<AccuracyMetrics> calculateRouteAccuracy(DataStream<OccupancyComparisonRecord> comparisonStream) {
        return comparisonStream
            .filter(new ValidComparisonFilter())
            .map(new RouteAccuracyMapper())
            .keyBy(new AccuracyMetricsKeySelector())
            .reduce(new AccuracyMetricsReducer())
            .map(metrics -> {
                metrics.calculatePercentages();
                return metrics;
            });
    }
    
    /**
     * Calculates comprehensive accuracy metrics (overall, by date, by route).
     * 
     * @param comparisonStream Stream of occupancy comparison records
     * @return Stream of all accuracy metrics
     */
    public DataStream<AccuracyMetrics> calculateComprehensiveAccuracy(DataStream<OccupancyComparisonRecord> comparisonStream) {
        DataStream<AccuracyMetrics> overallMetrics = calculateOverallAccuracy(comparisonStream);
        DataStream<AccuracyMetrics> dateMetrics = calculateDateAccuracy(comparisonStream);
        DataStream<AccuracyMetrics> routeMetrics = calculateRouteAccuracy(comparisonStream);
        
        return overallMetrics.union(dateMetrics, routeMetrics);
    }
}