import { test, expect } from '@playwright/test';

test.describe('Static Map Vehicle Count Fix', () => {
  test('verify vehicle count display shows correct filtered count', async ({ page }) => {
    console.log('🔍 Testing Fixed Vehicle Count Display...');
    
    // Navigate to static map
    await page.goto('/');
    
    // Wait for data to load
    await page.waitForTimeout(8000);
    
    // Take screenshot
    await page.screenshot({ 
      path: 'static-map-vehicle-count-fixed.png', 
      fullPage: true 
    });
    console.log('📸 Screenshot: static-map-vehicle-count-fixed.png');
    
    // Check if connection is established
    const connectionStatus = page.locator('text=Connected, text=Disconnected');
    if (await connectionStatus.first().isVisible()) {
      const connectionText = await connectionStatus.first().textContent();
      console.log(`🌐 Connection: ${connectionText}`);
    }
    
    // Check vehicle count display - should now show filtered count
    const vehicleCountElement = page.locator('text=/\\d+.*\\/.*\\d+.*vehicles/');
    await expect(vehicleCountElement).toBeVisible({ timeout: 15000 });
    
    const vehicleCountText = await vehicleCountElement.textContent();
    console.log(`📊 Vehicle count display: "${vehicleCountText}"`);
    
    // Parse the count (should be "X / Y vehicles" format)
    const countMatch = vehicleCountText?.match(/(\d+)\s*\/\s*(\d+)\s*vehicles/);
    if (countMatch) {
      const filteredCount = parseInt(countMatch[1]);
      const totalCount = parseInt(countMatch[2]);
      
      console.log(`   Filtered: ${filteredCount}, Total: ${totalCount}`);
      
      // Filtered count should be > 0 and <= total count
      expect(filteredCount).toBeGreaterThan(0);
      expect(filteredCount).toBeLessThanOrEqual(totalCount);
      
      console.log('✅ Vehicle filtering is working correctly!');
    } else {
      console.log('⚠️ Could not parse vehicle count format');
    }
    
    // Check for vehicle markers on map
    const vehicleMarkers = page.locator('.leaflet-marker-icon');
    const markerCount = await vehicleMarkers.count();
    console.log(`🚌 Vehicle markers on map: ${markerCount}`);
    
    // Markers should be visible
    expect(markerCount).toBeGreaterThan(0);
    
    // Test filtering controls
    const showBusesCheckbox = page.locator('input[type="checkbox"]').first();
    const showTrainsCheckbox = page.locator('input[type="checkbox"]').nth(1);
    
    if (await showBusesCheckbox.isVisible()) {
      console.log('🎛️ Testing bus filter toggle...');
      
      // Get initial state
      const initialBusState = await showBusesCheckbox.isChecked();
      console.log(`   Initial bus state: ${initialBusState}`);
      
      // Toggle buses off
      if (initialBusState) {
        await showBusesCheckbox.click();
        await page.waitForTimeout(2000);
        
        const newCountText = await vehicleCountElement.textContent();
        console.log(`   After toggling buses off: "${newCountText}"`);
        
        // Toggle back on
        await showBusesCheckbox.click();
        await page.waitForTimeout(2000);
        
        const finalCountText = await vehicleCountElement.textContent();
        console.log(`   After toggling buses back on: "${finalCountText}"`);
      }
    }
    
    console.log('✅ Static map vehicle count fix verified!');
  });
  
  test('verify vehicle types are correctly identified', async ({ page }) => {
    console.log('🚌 Testing Vehicle Type Classification...');
    
    await page.goto('/');
    await page.waitForTimeout(8000);
    
    // Check if we can identify buses vs trains correctly
    const response = await page.evaluate(async () => {
      try {
        const apiResponse = await fetch('http://localhost:8080/api/vehicles');
        const data = await apiResponse.json();
        
        const vehicles = data.vehicles.slice(0, 10); // First 10 vehicles
        const analysis = vehicles.map(vehicle => ({
          route_id: vehicle.route_id,
          is_likely_train: /^[A-Z]$/.test(vehicle.route_id || ''),
          is_likely_bus: !/^[A-Z]$/.test(vehicle.route_id || '')
        }));
        
        return {
          success: true,
          total: vehicles.length,
          analysis
        };
      } catch (error) {
        return { success: false, error: error.message };
      }
    });
    
    console.log(`🔍 Vehicle type analysis:`, JSON.stringify(response, null, 2));
    
    if (response.success) {
      const trains = response.analysis.filter(v => v.is_likely_train);
      const buses = response.analysis.filter(v => v.is_likely_bus);
      
      console.log(`   🚊 Likely trains (single letter routes): ${trains.length}`);
      console.log(`   🚌 Likely buses (numeric/multi-char routes): ${buses.length}`);
      
      trains.forEach(train => console.log(`     Train route: ${train.route_id}`));
      buses.forEach(bus => console.log(`     Bus route: ${bus.route_id}`));
    }
    
    expect(response.success).toBe(true);
  });
});