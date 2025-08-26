import { test, expect } from '@playwright/test';

test.describe('Live Tab Improved Layout', () => {
  test('verify improved desktop layout', async ({ page }) => {
    console.log('🔍 Testing Improved Desktop Layout...');
    
    // Set desktop viewport
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto('/live');
    await page.waitForTimeout(3000);
    
    // Take screenshot of improved layout
    await page.screenshot({ 
      path: 'live-tab-improved-desktop.png', 
      fullPage: true 
    });
    console.log('📸 Desktop screenshot: live-tab-improved-desktop.png');
    
    // Verify desktop status bar is visible
    const desktopStatusBar = page.locator('.hidden.sm\\:flex').first();
    await expect(desktopStatusBar).toBeVisible();
    console.log('✅ Desktop status bar is visible');
    
    // Verify desktop vehicle controls are visible  
    const desktopControls = page.locator('.hidden.sm\\:block').first();
    await expect(desktopControls).toBeVisible();
    console.log('✅ Desktop vehicle controls are visible');
    
    // Verify accessibility improvements - check for aria-labels
    const refreshButton = page.getByRole('button', { name: /refresh live transit data/i });
    await expect(refreshButton).toBeVisible();
    console.log('✅ Refresh button has proper aria-label');
    
    // Check vehicle toggle accessibility
    const busToggle = page.getByRole('checkbox', { name: /Toggle bus visibility/i });
    if (await busToggle.isVisible()) {
      console.log('✅ Bus toggle has proper aria-label');
    }
    
    const trainToggle = page.getByRole('checkbox', { name: /Toggle train visibility/i });
    if (await trainToggle.isVisible()) {
      console.log('✅ Train toggle has proper aria-label');
    }
  });
  
  test('verify improved mobile layout', async ({ page }) => {
    console.log('📱 Testing Improved Mobile Layout...');
    
    // Set mobile viewport
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/live');
    await page.waitForTimeout(3000);
    
    // Take screenshot of improved mobile layout
    await page.screenshot({ 
      path: 'live-tab-improved-mobile.png', 
      fullPage: true 
    });
    console.log('📸 Mobile screenshot: live-tab-improved-mobile.png');
    
    // Verify desktop status bar is hidden on mobile
    const desktopStatusBar = page.locator('.hidden.sm\\:flex').first();
    await expect(desktopStatusBar).toBeHidden();
    console.log('✅ Desktop status bar is hidden on mobile');
    
    // Verify mobile status bar is visible
    const mobileStatusBar = page.locator('.sm\\:hidden').first();
    await expect(mobileStatusBar).toBeVisible();
    console.log('✅ Mobile status bar is visible');
    
    // Verify mobile controls are visible
    const mobileControls = page.locator('.sm\\:hidden').nth(1);
    if (await mobileControls.isVisible()) {
      console.log('✅ Mobile vehicle controls are visible');
    }
    
    // Test mobile interactions
    const mobileBusToggle = page.getByRole('checkbox', { name: /Show buses/i });
    if (await mobileBusToggle.isVisible()) {
      const initialState = await mobileBusToggle.isChecked();
      await mobileBusToggle.click();
      const newState = await mobileBusToggle.isChecked();
      expect(newState).toBe(!initialState);
      console.log('✅ Mobile bus toggle works correctly');
    }
  });
  
  test('verify responsive behavior', async ({ page }) => {
    console.log('📐 Testing Responsive Behavior...');
    
    await page.goto('/live');
    await page.waitForTimeout(2000);
    
    // Test different breakpoints
    const viewports = [
      { width: 1920, height: 1080, name: 'Desktop Large' },
      { width: 1280, height: 720, name: 'Desktop Medium' },
      { width: 768, height: 1024, name: 'Tablet' },
      { width: 375, height: 667, name: 'Mobile' }
    ];
    
    for (const viewport of viewports) {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      await page.waitForTimeout(1000);
      
      console.log(`📏 Testing ${viewport.name}: ${viewport.width}x${viewport.height}`);
      
      // Check if map is still visible and properly sized
      const mapContainer = page.locator('.leaflet-container').first();
      await expect(mapContainer).toBeVisible();
      
      const mapBox = await mapContainer.boundingBox();
      if (mapBox) {
        const mapUtilization = (mapBox.width * mapBox.height) / (viewport.width * viewport.height);
        console.log(`   Map utilization: ${Math.round(mapUtilization * 100)}%`);
        
        // Map should utilize at least 50% of screen space
        expect(mapUtilization).toBeGreaterThan(0.5);
      }
      
      // Check for horizontal scrolling
      const body = page.locator('body');
      const scrollWidth = await body.evaluate((el) => el.scrollWidth);
      const clientWidth = await body.evaluate((el) => el.clientWidth);
      
      if (scrollWidth > clientWidth) {
        console.log(`   ⚠️  Horizontal scrolling detected at ${viewport.name}`);
      } else {
        console.log(`   ✅ No horizontal scrolling at ${viewport.name}`);
      }
    }
  });
  
  test('verify accessibility improvements', async ({ page }) => {
    console.log('♿ Testing Accessibility Improvements...');
    
    await page.goto('/live');
    await page.waitForTimeout(3000);
    
    // Check for proper heading structure
    const headings = page.locator('h1, h2, h3, h4, h5, h6');
    const headingCount = await headings.count();
    console.log(`📝 Found ${headingCount} headings`);
    
    // Check for proper form labels
    const inputs = page.locator('input[type=\"checkbox\"]');
    const inputCount = await inputs.count();
    let labeledInputs = 0;
    
    for (let i = 0; i < inputCount; i++) {
      const input = inputs.nth(i);
      const ariaLabel = await input.getAttribute('aria-label');
      const associatedLabel = await input.locator('xpath=//label[@for=\"' + await input.getAttribute('id') + '\"]').count();
      
      if (ariaLabel || associatedLabel > 0) {
        labeledInputs++;
      }
    }
    
    const accessibilityRatio = labeledInputs / Math.max(inputCount, 1);
    console.log(`♿ Input accessibility: ${Math.round(accessibilityRatio * 100)}% (${labeledInputs}/${inputCount})`);
    expect(accessibilityRatio).toBeGreaterThan(0.8); // At least 80% should have proper labels
    
    // Check for focus management
    const focusableElements = page.locator('button, input, a[href]');
    const focusableCount = await focusableElements.count();
    console.log(`🎯 Found ${focusableCount} focusable elements`);
    
    // Test keyboard navigation
    if (focusableCount > 0) {
      await focusableElements.first().focus();
      const focusedElement = await page.evaluate(() => document.activeElement?.tagName);
      console.log(`⌨️  Keyboard focus works: ${focusedElement}`);
    }
  });
  
  test('performance and layout stability', async ({ page }) => {
    console.log('⚡ Testing Performance and Layout Stability...');
    
    await page.goto('/live');
    
    // Measure page load time
    const startTime = Date.now();
    await page.waitForSelector('.leaflet-container', { timeout: 10000 });
    const loadTime = Date.now() - startTime;
    
    console.log(`⏱️  Map load time: ${loadTime}ms`);
    expect(loadTime).toBeLessThan(5000); // Should load within 5 seconds
    
    // Check for layout shift
    await page.waitForTimeout(3000);
    
    // Take measurements at different times to detect shifts
    const mapContainer = page.locator('.leaflet-container').first();
    const initialBox = await mapContainer.boundingBox();
    
    await page.waitForTimeout(2000);
    
    const finalBox = await mapContainer.boundingBox();
    
    if (initialBox && finalBox) {
      const xShift = Math.abs(initialBox.x - finalBox.x);
      const yShift = Math.abs(initialBox.y - finalBox.y);
      const sizeChangeX = Math.abs(initialBox.width - finalBox.width);
      const sizeChangeY = Math.abs(initialBox.height - finalBox.height);
      
      console.log(`📐 Layout stability - Position shift: (${xShift}, ${yShift}), Size change: (${sizeChangeX}, ${sizeChangeY})`);
      
      // Minimal layout shift is acceptable
      expect(xShift + yShift + sizeChangeX + sizeChangeY).toBeLessThan(10);
    }
    
    console.log('✅ Layout improvements verified successfully!');
  });
});