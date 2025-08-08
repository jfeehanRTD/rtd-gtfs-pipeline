package com.rtd.pipeline.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data model representing a GTFS-RT Alert.
 * Contains information about service alerts and disruptions.
 */
public class Alert {
    
    @JsonProperty("alert_id")
    private String alertId;
    
    @JsonProperty("cause")
    private String cause;
    
    @JsonProperty("effect")
    private String effect;
    
    @JsonProperty("header_text")
    private String headerText;
    
    @JsonProperty("description_text")
    private String descriptionText;
    
    @JsonProperty("url")
    private String url;
    
    @JsonProperty("active_period_start")
    private Long activePeriodStart;
    
    @JsonProperty("active_period_end")
    private Long activePeriodEnd;
    
    @JsonProperty("timestamp_ms")
    private Long timestamp_ms;
    
    // Default constructor
    public Alert() {}
    
    // Builder constructor
    private Alert(Builder builder) {
        this.alertId = builder.alertId;
        this.cause = builder.cause;
        this.effect = builder.effect;
        this.headerText = builder.headerText;
        this.descriptionText = builder.descriptionText;
        this.url = builder.url;
        this.activePeriodStart = builder.activePeriodStart;
        this.activePeriodEnd = builder.activePeriodEnd;
        this.timestamp_ms = builder.timestamp_ms;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters and Setters
    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }
    
    public String getCause() { return cause; }
    public void setCause(String cause) { this.cause = cause; }
    
    public String getEffect() { return effect; }
    public void setEffect(String effect) { this.effect = effect; }
    
    public String getHeaderText() { return headerText; }
    public void setHeaderText(String headerText) { this.headerText = headerText; }
    
    public String getDescriptionText() { return descriptionText; }
    public void setDescriptionText(String descriptionText) { this.descriptionText = descriptionText; }
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public Long getActivePeriodStart() { return activePeriodStart; }
    public void setActivePeriodStart(Long activePeriodStart) { this.activePeriodStart = activePeriodStart; }
    
    public Long getActivePeriodEnd() { return activePeriodEnd; }
    public void setActivePeriodEnd(Long activePeriodEnd) { this.activePeriodEnd = activePeriodEnd; }
    
    public Long getTimestamp() { return timestamp_ms; }
    public void setTimestamp_ms(Long timestamp_ms) { this.timestamp_ms = timestamp_ms; }
    
    @Override
    public String toString() {
        return "Alert{" +
                "alertId='" + alertId + '\'' +
                ", cause='" + cause + '\'' +
                ", effect='" + effect + '\'' +
                ", headerText='" + headerText + '\'' +
                ", descriptionText='" + descriptionText + '\'' +
                ", url='" + url + '\'' +
                ", activePeriodStart=" + activePeriodStart +
                ", activePeriodEnd=" + activePeriodEnd +
                ", timestamp_ms=" + timestamp_ms +
                '}';
    }
    
    // Builder pattern
    public static class Builder {
        private String alertId;
        private String cause;
        private String effect;
        private String headerText;
        private String descriptionText;
        private String url;
        private Long activePeriodStart;
        private Long activePeriodEnd;
        private Long timestamp_ms;
        
        public Builder alertId(String alertId) { this.alertId = alertId; return this; }
        public Builder cause(String cause) { this.cause = cause; return this; }
        public Builder effect(String effect) { this.effect = effect; return this; }
        public Builder headerText(String headerText) { this.headerText = headerText; return this; }
        public Builder descriptionText(String descriptionText) { this.descriptionText = descriptionText; return this; }
        public Builder url(String url) { this.url = url; return this; }
        public Builder activePeriodStart(Long activePeriodStart) { this.activePeriodStart = activePeriodStart; return this; }
        public Builder activePeriodEnd(Long activePeriodEnd) { this.activePeriodEnd = activePeriodEnd; return this; }
        public Builder timestamp_ms(Long timestamp_ms) { this.timestamp_ms = timestamp_ms; return this; }
        
        public Alert build() {
            return new Alert(this);
        }
    }
}