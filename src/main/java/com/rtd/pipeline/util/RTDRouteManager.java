package com.rtd.pipeline.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for managing RTD route information and mapping GTFS/GTFS-RT data.
 * Provides comprehensive route details for all RTD services including light rail and bus routes.
 */
public class RTDRouteManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDRouteManager.class);
    
    // RTD Light Rail Routes
    public enum LightRailRoute {
        A_LINE("A", "A-Line", "A-Line to Denver International Airport", "#0073E6", "Blue Line"),
        B_LINE("B", "B-Line", "B-Line to Westminster", "#00A84F", "Green Line"),
        C_LINE("C", "C-Line", "C-Line to Littleton", "#F7931E", "Orange Line"),
        D_LINE("D", "D-Line", "D-Line to Littleton-Mineral", "#FFD700", "Gold Line"),
        E_LINE("E", "E-Line", "E-Line to RidgeGate", "#8B4513", "Brown Line"),
        F_LINE("F", "F-Line", "F-Line to 18th & California", "#FF69B4", "Pink Line"),
        G_LINE("G", "G-Line", "G-Line to Wheat Ridge", "#32CD32", "Lime Green Line"),
        H_LINE("H", "H-Line", "H-Line to Nine Mile", "#FF4500", "Red Orange Line"),
        N_LINE("N", "N-Line", "N-Line to Eastlake-124th", "#9932CC", "Purple Line"),
        R_LINE("R", "R-Line", "R-Line to Peoria", "#DC143C", "Crimson Line"),
        W_LINE("W", "W-Line", "W-Line to Westminster", "#20B2AA", "Light Sea Green Line");
        
        private final String routeId;
        private final String shortName;
        private final String longName;
        private final String color;
        private final String brandName;
        
        LightRailRoute(String routeId, String shortName, String longName, String color, String brandName) {
            this.routeId = routeId;
            this.shortName = shortName;
            this.longName = longName;
            this.color = color;
            this.brandName = brandName;
        }
        
        public String getRouteId() { return routeId; }
        public String getShortName() { return shortName; }
        public String getLongName() { return longName; }
        public String getColor() { return color; }
        public String getBrandName() { return brandName; }
    }
    
    // Route information cache
    private static final Map<String, RouteInfo> ROUTE_CACHE = new ConcurrentHashMap<>();
    
    // Route stops mapping (simplified - would normally come from GTFS data)
    private static final Map<String, List<String>> ROUTE_STOPS = new HashMap<>();
    
    static {
        initializeRouteStops();
    }
    
    /**
     * Route information container
     */
    public static class RouteInfo {
        private final String routeId;
        private final String shortName;
        private final String longName;
        private final String routeType; // 1 = Light Rail, 3 = Bus
        private final String color;
        private final String textColor;
        private final List<String> stops;
        private final String agency;
        
        public RouteInfo(String routeId, String shortName, String longName, String routeType, 
                        String color, String textColor, List<String> stops, String agency) {
            this.routeId = routeId;
            this.shortName = shortName;
            this.longName = longName;
            this.routeType = routeType;
            this.color = color;
            this.textColor = textColor;
            this.stops = stops != null ? new ArrayList<>(stops) : new ArrayList<>();
            this.agency = agency;
        }
        
        // Getters
        public String getRouteId() { return routeId; }
        public String getShortName() { return shortName; }
        public String getLongName() { return longName; }
        public String getRouteType() { return routeType; }
        public String getColor() { return color; }
        public String getTextColor() { return textColor; }
        public List<String> getStops() { return new ArrayList<>(stops); }
        public String getAgency() { return agency; }
    }
    
    /**
     * Get comprehensive route information for a given route ID
     */
    public static RouteInfo getRouteInfo(String routeId) {
        if (routeId == null) return null;
        
        return ROUTE_CACHE.computeIfAbsent(routeId, RTDRouteManager::createRouteInfo);
    }
    
    /**
     * Get all RTD light rail routes
     */
    public static Map<String, RouteInfo> getAllLightRailRoutes() {
        Map<String, RouteInfo> lightRailRoutes = new HashMap<>();
        
        for (LightRailRoute route : LightRailRoute.values()) {
            lightRailRoutes.put(route.getRouteId(), getRouteInfo(route.getRouteId()));
        }
        
        return lightRailRoutes;
    }
    
    /**
     * Get all routes for all vehicles (including bus routes)
     */
    public static Map<String, RouteInfo> getAllRoutes() {
        Map<String, RouteInfo> allRoutes = new HashMap<>(getAllLightRailRoutes());
        
        // Add common bus routes (this would normally come from GTFS data)
        String[] busRoutes = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", 
                             "15", "16", "17", "18", "19", "20", "24", "25", "28", "31", 
                             "32", "36", "38", "40", "41", "42", "44", "51", "52", "83", 
                             "100", "120", "121", "122", "124", "125"};
        
        for (String busRoute : busRoutes) {
            allRoutes.put(busRoute, getRouteInfo(busRoute));
        }
        
        return allRoutes;
    }
    
    /**
     * Check if a route is a light rail route
     */
    public static boolean isLightRailRoute(String routeId) {
        if (routeId == null) return false;
        
        for (LightRailRoute route : LightRailRoute.values()) {
            if (route.getRouteId().equals(routeId) || routeId.startsWith(route.getRouteId())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get the trip headsign based on route and direction
     */
    public static String getTripHeadsign(String routeId, int directionId) {
        for (LightRailRoute route : LightRailRoute.values()) {
            if (routeId != null && routeId.startsWith(route.getRouteId())) {
                return directionId == 0 ? 
                    route.getLongName().replace(route.getShortName() + " to ", "To ") : 
                    "To Union Station";
            }
        }
        
        // For bus routes, use generic headsigns
        return directionId == 0 ? "Outbound" : "Inbound";
    }
    
    /**
     * Initialize route stops (simplified mapping)
     */
    private static void initializeRouteStops() {
        // A-Line stops
        ROUTE_STOPS.put("A", Arrays.asList(
            "Union Station", "38th & Blake", "40th & Colorado", "Central Park Blvd", 
            "40th & Airport Blvd", "Airport Terminal"
        ));
        
        // B-Line stops  
        ROUTE_STOPS.put("B", Arrays.asList(
            "Union Station", "25th & Welton", "29th & Welton", "38th & Blake",
            "40th & Colorado", "Peoria", "61st & Peña", "Westminster"
        ));
        
        // C-Line stops
        ROUTE_STOPS.put("C", Arrays.asList(
            "Union Station", "10th & Osage", "Auraria West", "Decatur-Federal",
            "Lakewood-Wadsworth", "Belmar", "Littleton-Downtown"
        ));
        
        // D-Line stops
        ROUTE_STOPS.put("D", Arrays.asList(
            "Union Station", "10th & Osage", "Auraria West", "Sports Authority Field",
            "W 25th & Federal", "Sheridan", "Littleton-Mineral"
        ));
        
        // E-Line stops
        ROUTE_STOPS.put("E", Arrays.asList(
            "Union Station", "Theatre District", "16th & Stout", "16th & California",
            "Pepsi Center", "10th & Osage", "Lincoln", "Southmoor", 
            "Arapahoe at Village Center", "Dry Creek", "RidgeGate Parkway"
        ));
        
        // Add other routes as needed...
    }
    
    /**
     * Create route information for a given route ID
     */
    private static RouteInfo createRouteInfo(String routeId) {
        // Check if it's a light rail route
        for (LightRailRoute route : LightRailRoute.values()) {
            if (routeId.equals(route.getRouteId()) || routeId.startsWith(route.getRouteId())) {
                return new RouteInfo(
                    routeId,
                    route.getShortName(),
                    route.getLongName(),
                    "1", // Light rail
                    route.getColor(),
                    "#FFFFFF", // White text
                    ROUTE_STOPS.getOrDefault(route.getRouteId(), new ArrayList<>()),
                    "Regional Transportation District"
                );
            }
        }
        
        // Default to bus route
        return new RouteInfo(
            routeId,
            "Route " + routeId,
            "Route " + routeId + " Bus Service",
            "3", // Bus
            "#666666", // Gray
            "#FFFFFF", // White text
            new ArrayList<>(), // No predefined stops for bus routes
            "Regional Transportation District"
        );
    }
    
    /**
     * Get route statistics
     */
    public static Map<String, Object> getRouteStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_light_rail_routes", LightRailRoute.values().length);
        stats.put("total_cached_routes", ROUTE_CACHE.size());
        stats.put("light_rail_route_ids", 
                 Arrays.stream(LightRailRoute.values())
                       .map(LightRailRoute::getRouteId)
                       .toArray(String[]::new));
        return stats;
    }
}