package com.rtd.pipeline.source;

import com.rtd.pipeline.model.GTFSScheduleData;
import org.apache.flink.streaming.api.functions.source.legacy.SourceFunction;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Custom Flink source function that periodically downloads GTFS schedule feeds
 * from RTD and emits parsed CSV data as structured objects.
 */
public class GTFSScheduleSource implements SourceFunction<GTFSScheduleData> {
    
    private static final Logger LOG = LoggerFactory.getLogger(GTFSScheduleSource.class);
    
    private final String gtfsUrl;
    private final long fetchIntervalSeconds;
    private volatile boolean running = true;
    
    public GTFSScheduleSource(String gtfsUrl, long fetchIntervalSeconds) {
        this.gtfsUrl = gtfsUrl;
        this.fetchIntervalSeconds = fetchIntervalSeconds;
    }
    
    @Override
    public void run(SourceContext<GTFSScheduleData> ctx) throws Exception {
        
        LOG.info("Starting GTFS Schedule source for URL: {} with interval: {} seconds", 
                gtfsUrl, fetchIntervalSeconds);
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            
            while (running) {
                try {
                    LOG.debug("Fetching GTFS schedule data from: {}", gtfsUrl);
                    
                    HttpGet request = new HttpGet(gtfsUrl);
                    request.setHeader("User-Agent", "RTD-GTFS-Pipeline/1.0");
                    
                    HttpResponse response = httpClient.execute(request);
                    
                    if (response.getStatusLine().getStatusCode() == 200) {
                        byte[] zipData = EntityUtils.toByteArray(response.getEntity());
                        
                        LOG.info("Successfully downloaded GTFS schedule zip file ({} bytes) from {}", 
                                zipData.length, gtfsUrl);
                        
                        processGTFSZip(zipData, ctx);
                        
                    } else {
                        LOG.error("Failed to download GTFS schedule from {}. HTTP Status: {}", 
                                gtfsUrl, response.getStatusLine().getStatusCode());
                    }
                    
                } catch (Exception e) {
                    LOG.error("Error processing GTFS schedule from {}: {}", gtfsUrl, e.getMessage(), e);
                }
                
                if (running) {
                    Thread.sleep(fetchIntervalSeconds * 1000);
                }
            }
            
        } catch (Exception e) {
            LOG.error("Fatal error in GTFS Schedule source: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    public void cancel() {
        LOG.info("Cancelling GTFS Schedule source for URL: {}", gtfsUrl);
        running = false;
    }
    
    private void processGTFSZip(byte[] zipData, SourceContext<GTFSScheduleData> ctx) throws IOException {
        
        long downloadTimestamp = Instant.now().toEpochMilli();
        String feedVersion = null;
        String agencyName = null;
        String feedStartDate = null;
        String feedEndDate = null;
        
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null && running) {
                
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
                
                ctx.collect(scheduleData);
                
                LOG.debug("Emitted GTFS schedule data for file: {} ({} bytes)", 
                         fileName, content.length());
            }
        }
        
        LOG.info("Completed processing GTFS schedule zip file");
    }
}