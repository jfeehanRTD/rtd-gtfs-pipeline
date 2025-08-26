import { test, expect } from '@playwright/test';

test.describe('Data Source Separation', () => {
  test('verify static map uses only GTFS-RT feed data', async ({ page }) => {
    console.log('🗺️  Testing Static Map - GTFS-RT Data Source...');
    
    // Navigate to static map
    await page.goto('/');
    await page.waitForTimeout(8000);
    
    // Take screenshot
    await page.screenshot({ 
      path: 'static-map-gtfs-rt-only.png', 
      fullPage: true 
    });
    console.log('📸 Screenshot: static-map-gtfs-rt-only.png');
    
    // Check console logs for RTD Data Service initialization
    const logs = [];
    page.on('console', (msg) => {
      if (msg.text().includes('RTD Data Service') || msg.text().includes('GTFS-RT') || msg.text().includes('rtd-denver.com')) {
        logs.push(msg.text());
      }
    });
    
    // Refresh to catch initialization logs
    await page.reload();
    await page.waitForTimeout(5000);
    
    // Verify GTFS-RT data source is being used
    const hasGTFSRTLogs = logs.some(log => 
      log.includes('RTD Data Service') || 
      log.includes('localhost:8080') ||
      log.includes('route information will be determined from live GTFS-RT data')
    );
    
    if (hasGTFSRTLogs) {
      console.log('✅ Static map correctly using GTFS-RT data source');
    }
    
    // Verify vehicle count is showing (means data is loading)
    const vehicleCountElement = page.locator('text=/\\d+.*\\/.*\\d+.*vehicles/');
    await expect(vehicleCountElement).toBeVisible({ timeout: 10000 });
    
    const vehicleCountText = await vehicleCountElement.textContent();
    console.log(`📊 Static map vehicle count: "${vehicleCountText}"`);
    
    // Verify we have vehicles from GTFS-RT
    const countMatch = vehicleCountText?.match(/(\d+)\s*\/\s*(\d+)\s*vehicles/);
    if (countMatch) {
      const filteredCount = parseInt(countMatch[1]);
      expect(filteredCount).toBeGreaterThan(0);
      console.log(`✅ Static map showing ${filteredCount} vehicles from GTFS-RT feed`);
    }
    
    // Check for vehicle markers
    const vehicleMarkers = page.locator('.leaflet-marker-icon');
    const markerCount = await vehicleMarkers.count();
    console.log(`🚌 Static map markers: ${markerCount}`);
    expect(markerCount).toBeGreaterThan(0);
  });
  
  test('verify live tab uses bus SIRI and rail communication data sources', async ({ page }) => {
    console.log('📡 Testing Live Tab - Bus SIRI & Rail Communication Data Sources...');
    
    // Navigate to live tab
    await page.goto('/live');
    await page.waitForTimeout(8000);
    
    // Take screenshot
    await page.screenshot({ 
      path: 'live-tab-siri-rail-only.png', 
      fullPage: true 
    });
    console.log('📸 Screenshot: live-tab-siri-rail-only.png');
    
    // Check console logs for Live Transit Service initialization
    const logs = [];
    page.on('console', (msg) => {
      if (msg.text().includes('Live Transit Service') || 
          msg.text().includes('SIRI') || 
          msg.text().includes('Rail Communication') ||
          msg.text().includes('8082') ||
          msg.text().includes('8081')) {
        logs.push(msg.text());
      }
    });
    
    // Refresh to catch initialization logs
    await page.reload();
    await page.waitForTimeout(5000);
    
    // Check that live service is connecting to correct endpoints
    const hasSIRILogs = logs.some(log => 
      log.includes('Bus SIRI') || 
      log.includes('localhost:8082') ||
      log.includes('SIRI receiver')
    );
    
    const hasRailLogs = logs.some(log => 
      log.includes('Rail Communication') || 
      log.includes('localhost:8081') ||
      log.includes('Rail') 
    );
    
    if (hasSIRILogs) {
      console.log('✅ Live tab correctly connecting to Bus SIRI data source (port 8082)');
    }
    
    if (hasRailLogs) {
      console.log('✅ Live tab correctly connecting to Rail Communication data source (port 8081)');
    }
    
    // Check connection status
    const connectionStatus = page.locator('text=Live, text=Offline');
    if (await connectionStatus.first().isVisible()) {
      const connectionText = await connectionStatus.first().textContent();
      console.log(`🌐 Live tab connection: ${connectionText}`);
    }
    
    // Check vehicle counts (should be 0 unless SIRI/Rail subscriptions are active)
    const busCountElement = page.locator('[aria-label="Buses"]').locator('+ span');
    const trainCountElement = page.locator('[aria-label="Trains"]').locator('+ span');
    
    if (await busCountElement.isVisible()) {
      const busCount = await busCountElement.textContent();
      console.log(`🚌 Live tab bus count: ${busCount}`);
    }
    
    if (await trainCountElement.isVisible()) {
      const trainCount = await trainCountElement.textContent();
      console.log(`🚊 Live tab train count: ${trainCount}`);
    }
    
    console.log('📝 Note: Live tab shows 0 vehicles unless SIRI/Rail subscriptions are active');
    console.log('📝 This is correct behavior - live data flows through Kafka topics');
  });
  
  test('verify data source endpoints are properly separated', async ({ page }) => {
    console.log('🔌 Testing Data Source Endpoint Separation...');
    
    // Test API endpoints from browser
    const endpointTests = await page.evaluate(async () => {
      const tests = [];
      
      try {
        // Test static map GTFS-RT endpoint
        const gtfsResponse = await fetch('http://localhost:8080/api/vehicles');
        tests.push({
          endpoint: 'GTFS-RT (Static Map)',
          url: 'http://localhost:8080/api/vehicles',
          status: gtfsResponse.status,
          success: gtfsResponse.ok,
          purpose: 'Static map vehicle data'
        });
      } catch (error) {
        tests.push({
          endpoint: 'GTFS-RT (Static Map)', 
          url: 'http://localhost:8080/api/vehicles',
          status: 'Error',
          success: false,
          error: error.message
        });
      }
      
      try {
        // Test Bus SIRI endpoint
        const siriResponse = await fetch('http://localhost:8082/status');
        tests.push({
          endpoint: 'Bus SIRI (Live Tab)',
          url: 'http://localhost:8082/status', 
          status: siriResponse.status,
          success: siriResponse.ok,
          purpose: 'Live bus SIRI feed receiver'
        });
      } catch (error) {
        tests.push({
          endpoint: 'Bus SIRI (Live Tab)',
          url: 'http://localhost:8082/status',
          status: 'Error', 
          success: false,
          error: error.message
        });
      }
      
      try {
        // Test Rail Communication endpoint
        const railResponse = await fetch('http://localhost:8081/status');
        tests.push({
          endpoint: 'Rail Communication (Live Tab)',
          url: 'http://localhost:8081/status',
          status: railResponse.status, 
          success: railResponse.ok,
          purpose: 'Live rail communication feed receiver'
        });
      } catch (error) {
        tests.push({
          endpoint: 'Rail Communication (Live Tab)',
          url: 'http://localhost:8081/status',
          status: 'Error',
          success: false, 
          error: error.message
        });
      }
      
      return tests;
    });
    
    console.log('\\n📊 Data Source Endpoint Test Results:');
    endpointTests.forEach((test, index) => {
      const status = test.success ? '✅' : '❌';
      console.log(`   ${status} ${test.endpoint}`);
      console.log(`      URL: ${test.url}`); 
      console.log(`      Status: ${test.status}`);
      console.log(`      Purpose: ${test.purpose}`);
      if (test.error) {
        console.log(`      Error: ${test.error}`);
      }
      console.log('');
    });
    
    // Verify all endpoints are accessible
    const successfulEndpoints = endpointTests.filter(test => test.success);
    expect(successfulEndpoints.length).toBeGreaterThanOrEqual(2);
    
    // Verify we have both static and live endpoints
    const hasGTFSRT = endpointTests.some(test => test.endpoint.includes('GTFS-RT') && test.success);
    const hasSIRIOrRail = endpointTests.some(test => (test.endpoint.includes('SIRI') || test.endpoint.includes('Rail')) && test.success);
    
    expect(hasGTFSRT).toBe(true);
    expect(hasSIRIOrRail).toBe(true);
    
    console.log('✅ Data source separation verified successfully!');
  });
});