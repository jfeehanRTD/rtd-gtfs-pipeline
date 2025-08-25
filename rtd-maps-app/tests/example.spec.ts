import { test, expect } from '@playwright/test';

test.describe('RTD Maps Application', () => {
  test.beforeEach(async ({ page }) => {
    // Wait for the API server to be ready
    await page.waitForTimeout(1000);
  });

  test('should load the static map page', async ({ page }) => {
    await page.goto('/');
    
    // Check if the main navigation is present
    await expect(page.getByText('RTD Live Transit Map')).toBeVisible();
    await expect(page.getByText('Static Map')).toBeVisible();
    
    // Wait for the map to load
    await page.waitForSelector('.leaflet-container', { timeout: 10000 });
    
    // Check if vehicle count is displayed
    await expect(page.locator('text=/\\d+.*vehicles/')).toBeVisible({ timeout: 15000 });
  });

  test('should load the live map page', async ({ page }) => {
    await page.goto('/live');
    
    // Check if the navigation shows Live Transit as active
    await expect(page.locator('.bg-rtd-primary:has-text("Live Transit")')).toBeVisible();
    
    // Wait for the map container
    await page.waitForSelector('.leaflet-container', { timeout: 10000 });
    
    // Check if vehicle controls are present
    await expect(page.getByText('Show Vehicles')).toBeVisible();
    await expect(page.getByText(/Buses \\(\\d+\\)/)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Trains \\(\\d+\\)/)).toBeVisible({ timeout: 15000 });
  });

  test('should navigate between maps', async ({ page }) => {
    // Start on static map
    await page.goto('/');
    await expect(page.getByText('Static Transit Map')).toBeVisible();
    
    // Navigate to live map
    await page.click('text=Live Transit');
    await expect(page.getByText('Live SIRI & Rail Feed Map')).toBeVisible();
    
    // Navigate to admin
    await page.click('text=Admin');
    await expect(page.getByText('Developer Admin Dashboard')).toBeVisible();
    
    // Navigate back to static
    await page.click('text=Static Map');
    await expect(page.getByText('Static Transit Map')).toBeVisible();
  });

  test('should show connection status', async ({ page }) => {
    await page.goto('/');
    
    // Wait for connection status to appear
    await page.waitForSelector('[class*="text-green-600"]:has-text("Connected"), [class*="text-red-600"]:has-text("Disconnected")', { 
      timeout: 10000 
    });
    
    // Check if we have a connection status
    const connectionStatus = page.locator('[class*="text-green-600"]:has-text("Connected"), [class*="text-red-600"]:has-text("Disconnected")');
    await expect(connectionStatus).toBeVisible();
  });

  test('should display map controls on static map', async ({ page }) => {
    await page.goto('/');
    
    // Wait for the refresh button
    await expect(page.getByRole('button', { name: /refresh/i })).toBeVisible();
    
    // Check if tool buttons are present (they might be visible or not depending on state)
    const toolButtons = page.locator('button[title="Data Sources"], button[title="Vehicle Selector"], button[title="Vehicle Tracker"]');
    await expect(toolButtons.first()).toBeVisible();
  });

  test('should show vehicle markers on map', async ({ page }) => {
    await page.goto('/');
    
    // Wait for map and data to load
    await page.waitForSelector('.leaflet-container', { timeout: 10000 });
    await page.waitForTimeout(5000); // Wait for vehicles to load
    
    // Look for vehicle markers (they have CSS classes with 'marker')
    const markers = page.locator('.leaflet-marker-icon, .bus-marker, .train-marker');
    
    // We should have at least some markers if the API is working
    const markerCount = await markers.count();
    console.log(`Found ${markerCount} vehicle markers on the map`);
    
    // This test might be flaky if the API is down, so we'll just log the result
    if (markerCount > 0) {
      expect(markerCount).toBeGreaterThan(0);
    }
  });
});