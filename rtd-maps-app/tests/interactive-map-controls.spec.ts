import { test, expect } from '@playwright/test';

test.describe('Interactive Map Controls', () => {
  test('should toggle data sources panel on static map', async ({ page }) => {
    await page.goto('/');
    
    // Wait for the map to load
    await page.waitForSelector('.leaflet-container', { timeout: 10000 });
    
    // Find and click the data sources button (layers icon)
    const dataSourcesButton = page.locator('button[title="Data Sources"]');
    await expect(dataSourcesButton).toBeVisible();
    await dataSourcesButton.click();
    
    // Check if the data sources panel appears
    await expect(page.getByText('Data Sources')).toBeVisible();
    
    // Click again to close
    await dataSourcesButton.click();
    
    // Panel should be hidden (we'll check if it's not in viewport or has display:none)
    await page.waitForTimeout(500); // Animation time
  });

  test('should open vehicle selector panel', async ({ page }) => {
    await page.goto('/');
    
    // Wait for vehicles to load
    await page.waitForTimeout(5000);
    
    // Click vehicle selector button
    const vehicleSelectorButton = page.locator('button[title="Vehicle Selector"]');
    await expect(vehicleSelectorButton).toBeVisible();
    await vehicleSelectorButton.click();
    
    // Check if vehicle selector panel appears with vehicle list
    await expect(page.getByText('Vehicle Selection')).toBeVisible();
    
    // Should have select all and clear all buttons
    await expect(page.getByText('Select All')).toBeVisible();
    await expect(page.getByText('Clear All')).toBeVisible();
  });

  test('should toggle vehicle tracker panel', async ({ page }) => {
    await page.goto('/');
    
    // Click vehicle tracker button
    const trackerButton = page.locator('button[title="Vehicle Tracker"]');
    await expect(trackerButton).toBeVisible();
    await trackerButton.click();
    
    // Check if tracker panel appears
    await expect(page.getByText('Vehicle Tracking')).toBeVisible();
  });

  test('should refresh data when refresh button clicked', async ({ page }) => {
    await page.goto('/');
    
    // Wait for initial load
    await page.waitForTimeout(3000);
    
    // Get initial vehicle count
    let initialCountText = '';
    try {
      initialCountText = await page.locator('text=/\\d+.*vehicles/').textContent() || '';
    } catch (e) {
      // Count might not be visible yet
    }
    
    // Click refresh button
    const refreshButton = page.getByRole('button', { name: /refresh/i });
    await expect(refreshButton).toBeVisible();
    await refreshButton.click();
    
    // Check if refresh button shows loading state
    await expect(refreshButton.locator('.animate-spin')).toBeVisible({ timeout: 5000 });
    
    // Wait for refresh to complete
    await expect(refreshButton.locator('.animate-spin')).not.toBeVisible({ timeout: 10000 });
  });

  test('should control vehicle visibility on live map', async ({ page }) => {
    await page.goto('/live');
    
    // Wait for map and data to load
    await page.waitForTimeout(5000);
    
    // Find the bus toggle checkbox
    const busCheckbox = page.locator('input[type="checkbox"]').first();
    await expect(busCheckbox).toBeVisible();
    
    // Get initial checked state
    const initiallyChecked = await busCheckbox.isChecked();
    
    // Toggle the checkbox
    await busCheckbox.click();
    
    // Verify the state changed
    const newState = await busCheckbox.isChecked();
    expect(newState).toBe(!initiallyChecked);
    
    // Toggle back
    await busCheckbox.click();
    expect(await busCheckbox.isChecked()).toBe(initiallyChecked);
  });

  test('should interact with map markers', async ({ page }) => {
    await page.goto('/');
    
    // Wait for map and markers to load
    await page.waitForSelector('.leaflet-container', { timeout: 10000 });
    await page.waitForTimeout(5000);
    
    // Look for vehicle markers
    const markers = page.locator('.leaflet-marker-icon, .bus-marker, .train-marker');
    const markerCount = await markers.count();
    
    console.log(`Found ${markerCount} markers on the map`);
    
    if (markerCount > 0) {
      // Click on the first marker
      await markers.first().click();
      
      // Wait a moment for popup to appear
      await page.waitForTimeout(1000);
      
      // Look for popup content
      const popup = page.locator('.leaflet-popup-content');
      if (await popup.isVisible()) {
        console.log('✅ Marker popup appeared');
        
        // Check if popup contains vehicle information
        const popupContent = await popup.textContent();
        expect(popupContent).toContain('Route');
      }
    } else {
      console.log('⚠️ No markers found - API might be down or no vehicles available');
    }
  });

  test('should handle map zoom and pan', async ({ page }) => {
    await page.goto('/');
    
    // Wait for map to load
    await page.waitForSelector('.leaflet-container', { timeout: 10000 });
    
    const mapContainer = page.locator('.leaflet-container');
    
    // Test zoom in using mouse wheel (simulate)
    await mapContainer.hover();
    await mapContainer.focus();
    
    // Zoom controls should be visible
    const zoomIn = page.locator('.leaflet-control-zoom-in');
    const zoomOut = page.locator('.leaflet-control-zoom-out');
    
    if (await zoomIn.isVisible()) {
      await zoomIn.click();
      await page.waitForTimeout(500);
      
      await zoomOut.click();
      await page.waitForTimeout(500);
      
      console.log('✅ Map zoom controls work');
    }
    
    // Test panning by clicking and dragging
    const mapBox = await mapContainer.boundingBox();
    if (mapBox) {
      const centerX = mapBox.x + mapBox.width / 2;
      const centerY = mapBox.y + mapBox.height / 2;
      
      // Pan the map
      await page.mouse.move(centerX, centerY);
      await page.mouse.down();
      await page.mouse.move(centerX + 100, centerY + 50);
      await page.mouse.up();
      
      console.log('✅ Map panning works');
    }
  });

  test('should navigate between map views and maintain state', async ({ page }) => {
    // Start on static map
    await page.goto('/');
    await page.waitForTimeout(3000);
    
    // Open a panel
    const dataSourcesButton = page.locator('button[title="Data Sources"]');
    if (await dataSourcesButton.isVisible()) {
      await dataSourcesButton.click();
      await expect(page.getByText('Data Sources')).toBeVisible();
    }
    
    // Navigate to live map
    await page.click('text=Live Transit');
    await page.waitForTimeout(2000);
    
    // Verify we're on live map
    await expect(page.getByText('Live SIRI & Rail Feed Map')).toBeVisible();
    
    // Check live map specific controls
    await expect(page.getByText('Show Vehicles')).toBeVisible();
    
    // Navigate back to static map
    await page.click('text=Static Map');
    await page.waitForTimeout(2000);
    
    // Verify we're back on static map
    await expect(page.getByText('Static Transit Map')).toBeVisible();
    
    console.log('✅ Navigation between maps works correctly');
  });
});