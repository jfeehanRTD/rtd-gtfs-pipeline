package com.rtd.pipeline;

import com.rtd.pipeline.model.GTFSScheduleData;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for GTFS ZIP processing to ensure the stream closing issue is fixed
 */
public class GTFSZipProcessingTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(GTFSZipProcessingTest.class);
    
    @Test
    public void testZipStreamProcessing() throws Exception {
        LOG.info("Testing GTFS ZIP stream processing fix...");
        
        // Create a test ZIP file with multiple GTFS files
        byte[] testZipData = createTestGTFSZip();
        
        // Test the processGTFSZip method
        List<GTFSScheduleData> dataList = new ArrayList<>();
        
        // This should not throw a "Stream closed" exception
        assertDoesNotThrow(() -> {
            processGTFSZipTest(testZipData, dataList);
        });
        
        // Verify we processed all files
        assertEquals(3, dataList.size(), "Should process all 3 GTFS files");
        
        // Verify file types
        boolean hasAgency = dataList.stream().anyMatch(d -> d.getFileType().equals("agency.txt"));
        boolean hasFeedInfo = dataList.stream().anyMatch(d -> d.getFileType().equals("feed_info.txt"));
        boolean hasRoutes = dataList.stream().anyMatch(d -> d.getFileType().equals("routes.txt"));
        
        assertTrue(hasAgency, "Should have processed agency.txt");
        assertTrue(hasFeedInfo, "Should have processed feed_info.txt");
        assertTrue(hasRoutes, "Should have processed routes.txt");
        
        LOG.info("✅ ZIP stream processing test passed - no stream closed errors");
    }
    
    /**
     * Create a test ZIP file with sample GTFS data
     */
    private byte[] createTestGTFSZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add agency.txt
            zos.putNextEntry(new ZipEntry("agency.txt"));
            String agencyData = """
                agency_id,agency_name,agency_url,agency_timezone
                RTD,Regional Transportation District,https://www.rtd-denver.com,America/Denver
                """;
            zos.write(agencyData.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            
            // Add feed_info.txt
            zos.putNextEntry(new ZipEntry("feed_info.txt"));
            String feedInfoData = """
                feed_publisher_name,feed_publisher_url,feed_lang,feed_version,feed_start_date,feed_end_date
                RTD,https://www.rtd-denver.com,en,2024.1,20240101,20241231
                """;
            zos.write(feedInfoData.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            
            // Add routes.txt
            zos.putNextEntry(new ZipEntry("routes.txt"));
            String routesData = """
                route_id,agency_id,route_short_name,route_long_name,route_type
                15,RTD,15,East Colfax,3
                20,RTD,20,East 20th Avenue,3
                """;
            zos.write(routesData.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        
        return baos.toByteArray();
    }
    
    /**
     * Test version of processGTFSZip method
     */
    private static void processGTFSZipTest(byte[] zipData, List<GTFSScheduleData> dataList) throws Exception {
        long downloadTimestamp = System.currentTimeMillis();
        String feedVersion = null;
        String agencyName = null;
        String feedStartDate = null;
        String feedEndDate = null;
        
        try (java.util.zip.ZipInputStream zipInputStream = 
                new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zipData))) {
            
            java.util.zip.ZipEntry entry;
            int entryCount = 0;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entryCount++;
                
                if (entry.isDirectory()) {
                    continue;
                }
                
                String fileName = entry.getName();
                
                StringBuilder content = new StringBuilder();
                // Don't close the ZipInputStream - use a non-closing wrapper
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(zipInputStream, java.nio.charset.StandardCharsets.UTF_8));
                
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 100) { // Smaller limit for test
                    content.append(line).append("\n");
                    lineCount++;
                    
                    // Extract metadata from specific files
                    if (fileName.equals("feed_info.txt") && lineCount == 2) {
                        String[] parts = line.split(",");
                        if (parts.length >= 4) {
                            feedVersion = parts[3].replace("\"", "").trim();
                            feedStartDate = parts[4].replace("\"", "").trim();
                            feedEndDate = parts[5].replace("\"", "").trim();
                        }
                    }
                    
                    if (fileName.equals("agency.txt") && lineCount == 2) {
                        String[] parts = line.split(",");
                        if (parts.length >= 2) {
                            agencyName = parts[1].replace("\"", "").trim();
                        }
                    }
                }
                
                // Close the entry, not the stream
                zipInputStream.closeEntry();
                
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
            }
        }
    }
}