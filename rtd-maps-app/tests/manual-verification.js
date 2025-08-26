// Manual verification script for data source separation
// This can be run in browser console to verify data source separation

console.log('🔍 Manual Data Source Verification');

// Check static map data source (should use GTFS-RT on port 8080)
fetch('http://localhost:8080/api/vehicles')
  .then(response => response.json())
  .then(data => {
    console.log('✅ Static Map Data Source (GTFS-RT):', {
      endpoint: 'http://localhost:8080/api/vehicles',
      vehicleCount: data.vehicles ? data.vehicles.length : 0,
      source: data.metadata ? data.metadata.source : 'Unknown',
      lastUpdate: data.metadata ? data.metadata.last_update : 'Unknown'
    });
  })
  .catch(error => console.log('❌ Static Map Data Source Error:', error));

// Check live tab bus SIRI data source (should use port 8082)
fetch('http://localhost:8082/status')
  .then(response => response.json())
  .then(data => {
    console.log('✅ Live Tab Bus SIRI Data Source:', {
      endpoint: 'http://localhost:8082/status',
      service: data.service,
      kafkaTopic: data.kafka_topic,
      subscriptionActive: data.subscription_active,
      endpoints: data.endpoints
    });
  })
  .catch(error => console.log('❌ Bus SIRI Data Source Error:', error));

// Check live tab rail communication data source (should use port 8081)
fetch('http://localhost:8081/status')
  .then(response => response.json())
  .then(data => {
    console.log('✅ Live Tab Rail Communication Data Source:', {
      endpoint: 'http://localhost:8081/status',
      service: data.service,
      kafkaTopic: data.kafka_topic,
      subscriptionActive: data.subscription_active,
      port: data.http_port
    });
  })
  .catch(error => console.log('❌ Rail Communication Data Source Error:', error));

console.log('📝 Expected Results:');
console.log('   - Static Map: Uses GTFS-RT data from localhost:8080 with ~200+ vehicles');
console.log('   - Live Tab: Uses Bus SIRI (8082) and Rail Communication (8081) endpoints'); 
console.log('   - Live Tab shows 0 vehicles unless SIRI/Rail subscriptions are active');
console.log('   - This separation ensures no hardcoded data and proper live feed usage');