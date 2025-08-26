import { test, expect } from '@playwright/test';

test.describe('Static Map Debug', () => {
  test('debug static map loading and vehicle display', async ({ page }) => {
    console.log('🔍 Debugging Static Map Loading...');
    
    // Navigate to static map
    await page.goto('/');
    
    // Wait longer for initial load
    await page.waitForTimeout(5000);
    
    // Take screenshot of current state
    await page.screenshot({ 
      path: 'static-map-debug.png', 
      fullPage: true 
    });
    console.log('📸 Screenshot: static-map-debug.png');
    
    // Check if loading screen is still visible
    const loadingScreen = page.locator('#loading-screen');
    const isLoadingVisible = await loadingScreen.isVisible();
    console.log(`📊 Loading screen visible: ${isLoadingVisible}`);
    
    if (isLoadingVisible) {
      console.log('⏳ Still loading, waiting longer...');
      await page.waitForTimeout(10000);
      
      const stillLoading = await loadingScreen.isVisible();
      console.log(`📊 Still loading after 10s: ${stillLoading}`);
    }
    
    // Check connection state
    const connectionIndicator = page.locator('text=Connected, text=Disconnected');
    if (await connectionIndicator.first().isVisible()) {
      const connectionText = await connectionIndicator.first().textContent();
      console.log(`🌐 Connection status: ${connectionText}`);
    }
    
    // Check for any error messages
    const errorMessages = page.locator('[class*="error"], [class*="alert"], .text-red-');
    const errorCount = await errorMessages.count();
    console.log(`❌ Error elements found: ${errorCount}`);
    
    if (errorCount > 0) {
      for (let i = 0; i < errorCount; i++) {
        const errorText = await errorMessages.nth(i).textContent();
        console.log(`   Error ${i + 1}: ${errorText}`);
      }
    }
    
    // Check for map container
    const mapContainer = page.locator('.leaflet-container');
    const mapVisible = await mapContainer.isVisible();
    console.log(`🗺️  Map container visible: ${mapVisible}`);
    
    if (mapVisible) {
      // Check for vehicle markers
      const vehicleMarkers = page.locator('.leaflet-marker-icon, [class*="marker"]');
      const markerCount = await vehicleMarkers.count();
      console.log(`🚌 Vehicle markers found: ${markerCount}`);
      
      // Check if markers have coordinates/are positioned
      if (markerCount > 0) {
        const firstMarker = vehicleMarkers.first();
        const markerBox = await firstMarker.boundingBox();
        if (markerBox) {
          console.log(`   First marker position: (${markerBox.x}, ${markerBox.y})`);
        }
      }
    }
    
    // Check vehicle count display
    const vehicleCount = page.locator('text=/\\d+.*vehicles/');
    if (await vehicleCount.first().isVisible()) {
      const countText = await vehicleCount.first().textContent();
      console.log(`📊 Vehicle count display: ${countText}`);
    }
    
    // Check JavaScript console for errors
    const logs = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        logs.push(msg.text());
      }
    });
    
    // Wait a bit more and check final state
    await page.waitForTimeout(5000);
    
    if (logs.length > 0) {
      console.log(`🐛 JavaScript errors found: ${logs.length}`);
      logs.forEach((log, index) => {
        console.log(`   JS Error ${index + 1}: ${log}`);
      });
    }
    
    // Final screenshot
    await page.screenshot({ 
      path: 'static-map-debug-final.png', 
      fullPage: true 
    });
    console.log('📸 Final screenshot: static-map-debug-final.png');
    
    // Test passes if we can analyze the state
    expect(true).toBe(true);
  });
  
  test('check API connectivity from browser', async ({ page }) => {
    console.log('🔌 Testing API Connectivity from Browser...');
    
    await page.goto('/');
    
    // Check if the browser can reach the API
    const apiResponse = await page.evaluate(async () => {
      try {
        const response = await fetch('http://localhost:8080/api/health');
        const data = await response.json();
        return { success: true, data, status: response.status };
      } catch (error) {
        return { success: false, error: error.message };
      }
    });
    
    console.log(`🌐 API Health Check: ${JSON.stringify(apiResponse, null, 2)}`);
    
    // Check vehicles endpoint
    const vehiclesResponse = await page.evaluate(async () => {
      try {
        const response = await fetch('http://localhost:8080/api/vehicles');
        const data = await response.json();
        return { 
          success: true, 
          vehicleCount: data.vehicles?.length || 0,
          metadata: data.metadata,
          status: response.status 
        };
      } catch (error) {
        return { success: false, error: error.message };
      }
    });
    
    console.log(`🚌 API Vehicles Check: ${JSON.stringify(vehiclesResponse, null, 2)}`);
    
    expect(apiResponse.success || vehiclesResponse.success).toBe(true);
  });
});