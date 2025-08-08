package com.rtd.pipeline.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data model representing GTFS schedule data.
 * Contains static transit schedule information from GTFS feeds.
 */
public class GTFSScheduleData {
    
    @JsonProperty("file_type")
    private String fileType;
    
    @JsonProperty("file_content")
    private String fileContent;
    
    @JsonProperty("download_timestamp")
    private Long downloadTimestamp;
    
    @JsonProperty("feed_version")
    private String feedVersion;
    
    @JsonProperty("agency_name")
    private String agencyName;
    
    @JsonProperty("feed_start_date")
    private String feedStartDate;
    
    @JsonProperty("feed_end_date")
    private String feedEndDate;
    
    public GTFSScheduleData() {}
    
    private GTFSScheduleData(Builder builder) {
        this.fileType = builder.fileType;
        this.fileContent = builder.fileContent;
        this.downloadTimestamp = builder.downloadTimestamp;
        this.feedVersion = builder.feedVersion;
        this.agencyName = builder.agencyName;
        this.feedStartDate = builder.feedStartDate;
        this.feedEndDate = builder.feedEndDate;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    
    public String getFileContent() { return fileContent; }
    public void setFileContent(String fileContent) { this.fileContent = fileContent; }
    
    public Long getDownloadTimestamp() { return downloadTimestamp; }
    public void setDownloadTimestamp(Long downloadTimestamp) { this.downloadTimestamp = downloadTimestamp; }
    
    public String getFeedVersion() { return feedVersion; }
    public void setFeedVersion(String feedVersion) { this.feedVersion = feedVersion; }
    
    public String getAgencyName() { return agencyName; }
    public void setAgencyName(String agencyName) { this.agencyName = agencyName; }
    
    public String getFeedStartDate() { return feedStartDate; }
    public void setFeedStartDate(String feedStartDate) { this.feedStartDate = feedStartDate; }
    
    public String getFeedEndDate() { return feedEndDate; }
    public void setFeedEndDate(String feedEndDate) { this.feedEndDate = feedEndDate; }
    
    public Long getTimestamp() { return downloadTimestamp; }
    
    @Override
    public String toString() {
        return "GTFSScheduleData{" +
                "fileType='" + fileType + '\'' +
                ", downloadTimestamp=" + downloadTimestamp +
                ", feedVersion='" + feedVersion + '\'' +
                ", agencyName='" + agencyName + '\'' +
                ", feedStartDate='" + feedStartDate + '\'' +
                ", feedEndDate='" + feedEndDate + '\'' +
                ", contentLength=" + (fileContent != null ? fileContent.length() : 0) +
                '}';
    }
    
    public static class Builder {
        private String fileType;
        private String fileContent;
        private Long downloadTimestamp;
        private String feedVersion;
        private String agencyName;
        private String feedStartDate;
        private String feedEndDate;
        
        public Builder fileType(String fileType) { this.fileType = fileType; return this; }
        public Builder fileContent(String fileContent) { this.fileContent = fileContent; return this; }
        public Builder downloadTimestamp(Long downloadTimestamp) { this.downloadTimestamp = downloadTimestamp; return this; }
        public Builder feedVersion(String feedVersion) { this.feedVersion = feedVersion; return this; }
        public Builder agencyName(String agencyName) { this.agencyName = agencyName; return this; }
        public Builder feedStartDate(String feedStartDate) { this.feedStartDate = feedStartDate; return this; }
        public Builder feedEndDate(String feedEndDate) { this.feedEndDate = feedEndDate; return this; }
        
        public GTFSScheduleData build() {
            return new GTFSScheduleData(this);
        }
    }
}