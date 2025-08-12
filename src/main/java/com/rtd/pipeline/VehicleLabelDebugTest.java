package com.rtd.pipeline;

import com.google.transit.realtime.GtfsRealtime.*;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import java.io.ByteArrayInputStream;
import java.util.*;

/**
 * Debug vehicle labels to understand why some have commas
 */
public class VehicleLabelDebugTest {
    
    private static final String VEHICLE_POSITIONS_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb";
    
    public static void main(String[] args) {
        System.out.println("=== RTD Vehicle Label Debug Analysis ===\n");
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(VEHICLE_POSITIONS_URL);
            request.setHeader("User-Agent", "RTD-GTFS-Pipeline/1.0");
            
            HttpResponse response = httpClient.execute(request);
            
            if (response.getStatusLine().getStatusCode() == 200) {
                byte[] feedData = EntityUtils.toByteArray(response.getEntity());
                FeedMessage feed = FeedMessage.parseFrom(new ByteArrayInputStream(feedData));
                
                System.out.println("=== VEHICLE LABEL ANALYSIS ===");
                System.out.println("Total vehicles: " + feed.getEntityCount());
                System.out.println();
                
                List<String> allLabels = new ArrayList<>();
                List<String> commaLabels = new ArrayList<>();
                Map<String, String> labelToRoute = new HashMap<>();
                Map<String, String> labelToId = new HashMap<>();
                
                for (FeedEntity entity : feed.getEntityList()) {
                    if (entity.hasVehicle()) {
                        VehiclePosition vp = entity.getVehicle();
                        String vehicleId = vp.getVehicle().getId();
                        String label = vp.getVehicle().hasLabel() ? vp.getVehicle().getLabel() : null;
                        String routeId = vp.hasTrip() && vp.getTrip().hasRouteId() ? 
                            vp.getTrip().getRouteId() : "NO_ROUTE";
                        
                        if (label != null) {
                            allLabels.add(label);
                            labelToRoute.put(label, routeId);
                            labelToId.put(label, vehicleId);
                            
                            // Check for labels containing commas
                            if (label.contains(",")) {
                                commaLabels.add(label);
                            }
                        }
                    }
                }
                
                System.out.println("SUMMARY:");
                System.out.println("- Total vehicles with labels: " + allLabels.size());
                System.out.println("- Vehicles with comma in label: " + commaLabels.size());
                System.out.println();
                
                if (!commaLabels.isEmpty()) {
                    System.out.println("VEHICLES WITH COMMA IN LABEL:");
                    System.out.println("Label        | Route | Vehicle ID");
                    System.out.println("-".repeat(50));
                    
                    for (String label : commaLabels) {
                        String route = labelToRoute.get(label);
                        String id = labelToId.get(label);
                        String shortId = id.length() > 8 ? "..." + id.substring(id.length() - 4) : id;
                        System.out.printf("%-12s | %-5s | %s\n", label, route, shortId);
                    }
                    System.out.println();
                }
                
                // Check for unusual label patterns
                System.out.println("LABEL PATTERN ANALYSIS:");
                Map<String, Integer> labelPatterns = new HashMap<>();
                
                for (String label : allLabels) {
                    if (label.matches("\\d+")) {
                        labelPatterns.put("numeric_only", labelPatterns.getOrDefault("numeric_only", 0) + 1);
                    } else if (label.matches("\\d+,\\d+")) {
                        labelPatterns.put("comma_separated_numbers", labelPatterns.getOrDefault("comma_separated_numbers", 0) + 1);
                    } else if (label.matches("[A-Z]+")) {
                        labelPatterns.put("letters_only", labelPatterns.getOrDefault("letters_only", 0) + 1);
                    } else if (label.matches("\\d+[A-Z]+")) {
                        labelPatterns.put("number_letter_combo", labelPatterns.getOrDefault("number_letter_combo", 0) + 1);
                    } else {
                        labelPatterns.put("other_pattern", labelPatterns.getOrDefault("other_pattern", 0) + 1);
                    }
                }
                
                labelPatterns.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> 
                        System.out.println("- " + entry.getKey() + ": " + entry.getValue() + " vehicles"));
                
                System.out.println();
                
                // Show some examples of different label types
                System.out.println("SAMPLE LABELS BY TYPE:");
                Set<String> shownLabels = new HashSet<>();
                
                for (String label : allLabels) {
                    if (shownLabels.size() >= 10) break;
                    
                    if (label.contains(",") && !shownLabels.contains("comma_example")) {
                        System.out.println("- Comma example: \"" + label + "\" (Route: " + labelToRoute.get(label) + ")");
                        shownLabels.add("comma_example");
                    } else if (label.matches("\\d+") && !shownLabels.contains("numeric_example")) {
                        System.out.println("- Numeric example: \"" + label + "\" (Route: " + labelToRoute.get(label) + ")");
                        shownLabels.add("numeric_example");
                    } else if (label.matches("[A-Z]+") && !shownLabels.contains("letter_example")) {
                        System.out.println("- Letter example: \"" + label + "\" (Route: " + labelToRoute.get(label) + ")");
                        shownLabels.add("letter_example");
                    }
                }
                
                System.out.println("\n✅ Debug analysis complete!");
                
            } else {
                System.err.println("Failed to fetch data: " + response.getStatusLine());
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}