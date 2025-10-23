# Oracle TIES Data Extraction - Quick Reference

## 🎯 Two Simple Strategies

### Strategy 1: Use RTD Public Feed (RECOMMENDED ⭐)

**Why:** RTD already publishes GTFS data publicly. No Oracle extraction needed!

```java
// That's it! The processor downloads from RTD automatically
GTFSDataProcessor processor = new GTFSDataProcessor();
processor.loadGTFSData("RTD");

// Data is always current, no Oracle dependency
List<GTFSRoute> routes = processor.getGTFSRoutes("RTD");
List<GTFSStop> stops = processor.getGTFSStops("RTD");
```

**Benefits:**
- ✅ Zero database dependency
- ✅ Always up-to-date (RTD maintains it)
- ✅ No extraction scripts needed
- ✅ Immediate cost savings: $147,950/year

---

### Strategy 2: Extract from Oracle (if needed)

**Only if:** Oracle has proprietary data not in public feeds

#### Quick Extraction (One-Time)

```bash
# Build the project
mvn clean package

# Run extraction
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
  com.rtd.pipeline.gtfs.TIESDataExtractor \
  jdbc:oracle:thin:@hostname:1521:sid \
  username \
  password \
  RTD \
  /output/directory

# Results:
#   /output/directory/agency.txt
#   /output/directory/routes.txt
#   /output/directory/stops.txt
#   ... all GTFS files
#   /output/gtfs_rtd_TIMESTAMP.zip
```

#### Scheduled Extraction (Ongoing)

**Linux/Mac:**
```bash
# Create sync script
cat > /opt/rtd/bin/sync-ties.sh << 'EOF'
#!/bin/bash
java -cp /opt/rtd/lib/rtd-gtfs-pipeline.jar \
  com.rtd.pipeline.gtfs.TIESDataExtractor \
  "$DB_URL" "$DB_USER" "$DB_PASSWORD" "RTD" "/data/gtfs/latest"
EOF

chmod +x /opt/rtd/bin/sync-ties.sh

# Schedule hourly (cron)
crontab -e
# Add: 0 * * * * /opt/rtd/bin/sync-ties.sh
```

**Windows:**
```powershell
# Create sync-ties.ps1
java -cp "C:\rtd\lib\rtd-gtfs-pipeline.jar" `
  com.rtd.pipeline.gtfs.TIESDataExtractor `
  "$env:DB_URL" "$env:DB_USER" "$env:DB_PASSWORD" `
  "RTD" "C:\data\gtfs\latest"

# Schedule with Task Scheduler
# Trigger: Daily at 1:00 AM
```

---

## 📊 Compare Oracle vs Public Feed

### Step 1: Extract from Oracle
```bash
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
  com.rtd.pipeline.gtfs.TIESDataExtractor \
  jdbc:oracle:thin:@host:1521:sid \
  user pass RTD /tmp/oracle_export
```

### Step 2: Download Public Feed
```bash
curl -o /tmp/public_feed.zip \
  "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=google_transit.zip"

unzip /tmp/public_feed.zip -d /tmp/public_export
```

### Step 3: Compare
```bash
# Compare record counts
echo "Oracle routes:" $(wc -l < /tmp/oracle_export/routes.txt)
echo "Public routes:" $(wc -l < /tmp/public_export/routes.txt)

echo "Oracle stops:" $(wc -l < /tmp/oracle_export/stops.txt)
echo "Public stops:" $(wc -l < /tmp/public_export/stops.txt)

# Compare file timestamps
ls -l /tmp/oracle_export/*.txt
ls -l /tmp/public_export/*.txt
```

---

## 🚀 Quick Start Recommendations

### For 90% of Use Cases:
```java
// Just use the public feed - it's that simple!
GTFSDataProcessor processor = new GTFSDataProcessor();
processor.loadGTFSData("RTD");

// Done! You have all RTD data with zero Oracle dependency
```

### Only Extract from Oracle If:
1. You find gaps in public feed data
2. You need internal-only routes
3. You need draft/upcoming schedules
4. You have proprietary fare rules

---

## 📁 What Gets Extracted

When you run `TIESDataExtractor`, you get complete GTFS data:

### Standard GTFS Files
- ✅ `agency.txt` - RTD/CDOT agency info
- ✅ `routes.txt` - All bus/rail routes
- ✅ `stops.txt` - All stop locations
- ✅ `trips.txt` - All scheduled trips
- ✅ `stop_times.txt` - Stop schedules (largest file)
- ✅ `shapes.txt` - Route geometry paths
- ✅ `calendar.txt` - Service schedules
- ✅ `calendar_dates.txt` - Service exceptions

### Fare Data (GTFS v2)
- ✅ `fare_products.txt` - Fare product definitions
- ✅ `fare_media.txt` - Payment methods
- ✅ `fare_leg_rules.txt` - Fare calculation rules
- ✅ `fare_transfer_rules.txt` - Transfer pricing

### Custom Extensions
- ✅ `stop_areas.txt` - Stop to fare zone mapping
- ✅ `areas.txt` - Fare zone definitions
- ✅ `networks.txt` - Fare network types
- ✅ `route_networks.txt` - Route to network mapping

**Plus:** ZIP file with all data combined

---

## ⏱️ Performance Expectations

| Data Volume | Extraction Time | ZIP Size |
|------------|-----------------|----------|
| Small agency (few routes) | 30 seconds | <5 MB |
| RTD (medium) | 2-5 minutes | 20-50 MB |
| Large agency | 5-15 minutes | 100+ MB |

**Note:** `stop_times.txt` takes longest (millions of records)

---

## 🔄 Keeping Data Up-to-Date

### Recommended: Auto-Update from Public Feed

The `GTFSDataProcessor` can reload data automatically:

```java
// Create scheduled task
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

scheduler.scheduleAtFixedRate(() -> {
    try {
        LOG.info("Refreshing GTFS data...");
        processor.clearData();
        processor.loadGTFSData("RTD");
        LOG.info("GTFS data refreshed successfully");
    } catch (Exception e) {
        LOG.error("Failed to refresh GTFS data", e);
    }
}, 0, 24, TimeUnit.HOURS);  // Reload daily
```

### Alternative: Schedule Oracle Extraction

If using Oracle, run extraction on schedule:
- **Daily**: For route/schedule updates
- **Weekly**: For fare changes
- **Hourly**: Only if real-time accuracy critical

---

## 💡 Pro Tips

### 1. Test Before Switching
```bash
# Extract from Oracle
java TIESDataExtractor ... /tmp/oracle

# Compare with your current production data
diff -r /current/production /tmp/oracle
```

### 2. Validate After Extraction
```java
// Load extracted data
GTFSDataProcessor validator = new GTFSDataProcessor();
validator.loadGTFSData("RTD");

// Check statistics
Map<String, Object> stats = validator.getStatistics();
System.out.println("Routes: " + stats.get("routes"));
System.out.println("Stops: " + stats.get("stops"));

// Should match Oracle counts
```

### 3. Monitor Data Freshness
```java
public boolean isDataStale() {
    LocalDateTime lastLoad = processor.getLastLoadTime();
    Duration age = Duration.between(lastLoad, LocalDateTime.now());
    return age.toHours() > 24;
}
```

---

## 🔧 Troubleshooting

### "Table or view does not exist"
**Problem:** PL/SQL functions not found

**Solution:** Ensure connected to correct Oracle schema
```sql
-- Check if functions exist
SELECT object_name FROM user_objects
WHERE object_type = 'PACKAGE'
AND object_name = 'TIES_GTFS_VW_PKG';
```

### "Out of Memory" during extraction
**Problem:** Too many stop_times records

**Solution:** Increase JVM heap
```bash
java -Xmx4g -cp rtd-gtfs-pipeline.jar TIESDataExtractor ...
```

### "Slow extraction"
**Problem:** Large dataset, network latency

**Solutions:**
- Run extraction on same network as Oracle
- Use fetch size tuning (already set to 10,000)
- Run during off-peak hours

---

## 📋 Decision Matrix

| Your Situation | Recommendation |
|----------------|----------------|
| Standard RTD routes/schedules | ✅ Use public feed |
| Need latest data daily | ✅ Use public feed |
| Cloud deployment (AKS/EKS) | ✅ Use public feed |
| Want zero Oracle cost | ✅ Use public feed |
| Have proprietary routes | ⚠️ Extract from Oracle |
| Need internal-only data | ⚠️ Extract from Oracle |
| Draft schedules not public yet | ⚠️ Extract from Oracle |

**90% of use cases:** Just use the public feed! 🎉

---

## 📞 Need Help?

1. **Try public feed first** - See if it has everything you need
2. **Review comparison** - `docs/DATA_SYNC_STRATEGY.md`
3. **Check extraction code** - `src/main/java/com/rtd/pipeline/gtfs/TIESDataExtractor.java`
4. **Test small first** - Extract one table to verify connectivity

---

## ✅ Success Criteria

After extraction, you should have:
- ✅ All `.txt` files in output directory
- ✅ ZIP file created with timestamp
- ✅ Log showing record counts
- ✅ Files are valid GTFS format
- ✅ Can load with `GTFSDataProcessor`

---

## 🎯 Bottom Line

**Most users:** Use `GTFSDataProcessor` with public RTD feed
- Zero Oracle dependency
- Always up-to-date
- Free
- Simple

**Special cases only:** Extract from Oracle if you absolutely need proprietary data

**The new Java system works either way!**
