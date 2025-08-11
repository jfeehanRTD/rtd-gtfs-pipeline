package com.rtd.pipeline.source;

import com.rtd.pipeline.model.GTFSScheduleData;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Custom Flink source that periodically downloads GTFS schedule feeds
 * from RTD and emits parsed CSV data as structured objects using DataGeneratorSource.
 */
public class GTFSScheduleSource {
    
    private static final Logger LOG = LoggerFactory.getLogger(GTFSScheduleSource.class);
    
    public static DataGeneratorSource<GTFSScheduleData> create(String gtfsUrl, long fetchIntervalSeconds) {
        return new DataGeneratorSource<GTFSScheduleData>(
                new GTFSScheduleGeneratorFunction(gtfsUrl, fetchIntervalSeconds),
                Long.MAX_VALUE, // Generate indefinitely
                RateLimiterStrategy.perSecond(1.0 / fetchIntervalSeconds), // Rate limit based on fetch interval
                TypeInformation.of(GTFSScheduleData.class) // Type information for GTFSScheduleData
        );
    }
    
    /**
     * Generator function that fetches and parses GTFS schedule data.
     */
    private static class GTFSScheduleGeneratorFunction implements GeneratorFunction<Long, GTFSScheduleData> {
        
        private final String gtfsUrl;
        private final long fetchIntervalSeconds;
        private transient AtomicLong lastFetchTime;
        private transient List<GTFSScheduleData> currentData;
        private transient int currentIndex;
        private transient Logger LOG;
        
        public GTFSScheduleGeneratorFunction(String gtfsUrl, long fetchIntervalSeconds) {
            this.gtfsUrl = gtfsUrl;
            this.fetchIntervalSeconds = fetchIntervalSeconds;
        }
        
        private void initializeTransientFields() {
            if (lastFetchTime == null) {
                lastFetchTime = new AtomicLong(0);
            }
            if (currentData == null) {
                currentData = new ArrayList<>();
            }
            currentIndex = 0;
            if (LOG == null) {
                LOG = LoggerFactory.getLogger(GTFSScheduleGeneratorFunction.class);
            }
        }
        
        @Override
        public GTFSScheduleData map(Long value) throws Exception {
            initializeTransientFields();
            
            long currentTime = System.currentTimeMillis();
            
            // Check if we need to fetch new data
            if (currentTime - lastFetchTime.get() > fetchIntervalSeconds * 1000) {
                if (lastFetchTime.compareAndSet(lastFetchTime.get(), currentTime)) {
                    fetchNewData();
                }
            }
            
            // Return next item from current data
            synchronized (this) {
                if (currentData.isEmpty()) {
                    return null; // Will cause the generator to skip this emission
                }
                
                if (currentIndex >= currentData.size()) {
                    currentIndex = 0; // Reset to beginning
                }
                
                return currentData.get(currentIndex++);
            }
        }
        
        private void fetchNewData() {
            try {
                LOG.debug("Fetching GTFS schedule data from: {}", gtfsUrl);
                
                List<GTFSScheduleData> newData = new ArrayList<>();
                
                try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                    HttpGet request = new HttpGet(gtfsUrl);
                    request.setHeader("User-Agent", "RTD-GTFS-Pipeline/1.0");
                    
                    HttpResponse response = httpClient.execute(request);
                    
                    if (response.getStatusLine().getStatusCode() == 200) {
                        byte[] zipData = EntityUtils.toByteArray(response.getEntity());
                        
                        LOG.info("Successfully downloaded GTFS schedule zip file ({} bytes) from {}", 
                                zipData.length, gtfsUrl);
                        
                        processGTFSZip(zipData, newData);
                        
                        synchronized (this) {
                            currentData = newData;
                            currentIndex = 0;
                        }
                        
                    } else {
                        LOG.error("Failed to download GTFS schedule from {}. HTTP Status: {}", 
                                gtfsUrl, response.getStatusLine().getStatusCode());
                    }
                }
                
            } catch (Exception e) {
                LOG.error("Error fetching GTFS schedule data from {}: {}", gtfsUrl, e.getMessage(), e);
            }
        }
        
        private void processGTFSZip(byte[] zipData, List<GTFSScheduleData> dataList) throws Exception {
            
            long downloadTimestamp = Instant.now().toEpochMilli();
            String feedVersion = null;
            String agencyName = null;
            String feedStartDate = null;
            String feedEndDate = null;
            
            try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipData))) {
                
                ZipEntry entry;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    
                    if (entry.isDirectory()) {
                        continue;
                    }
                    
                    String fileName = entry.getName();
                    LOG.debug("Processing GTFS file: {}", fileName);
                    
                    StringBuilder content = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(zipInputStream, StandardCharsets.UTF_8))) {
                        
                        String line;
                        int lineCount = 0;
                        while ((line = reader.readLine()) != null && lineCount < 10000) {
                            content.append(line).append("\n");
                            lineCount++;
                            
                            if (fileName.equals("feed_info.txt") && lineCount == 2) {
                                String[] parts = line.split(",");
                                if (parts.length >= 4) {
                                    feedVersion = parts[1].replace("\"", "").trim();
                                    feedStartDate = parts[2].replace("\"", "").trim();
                                    feedEndDate = parts[3].replace("\"", "").trim();
                                }
                            }
                            
                            if (fileName.equals("agency.txt") && lineCount == 2) {
                                String[] parts = line.split(",");
                                if (parts.length >= 2) {
                                    agencyName = parts[1].replace("\"", "").trim();
                                }
                            }
                        }
                        
                        if (lineCount >= 10000) {
                            LOG.warn("File {} was truncated at {} lines to prevent memory issues", fileName, lineCount);
                        }
                    }
                    
                    GTFSScheduleData scheduleData = GTFSScheduleData.builder()
                            .fileType(fileName)
                            .fileContent(content.toString())
                            .downloadTimestamp(downloadTimestamp)
                            .feedVersion(feedVersion)
                            .agencyName(agencyName)
                            .feedStartDate(feedStartDate)
                            .feedEndDate(feedEndDate)
                            .build();
                    
                    dataList.add(scheduleData);
                    
                    LOG.debug("Processed GTFS schedule data for file: {} ({} bytes)", 
                             fileName, content.length());
                }
            }
            
            LOG.info("Completed processing GTFS schedule zip file with {} files", dataList.size());
        }
    }
}