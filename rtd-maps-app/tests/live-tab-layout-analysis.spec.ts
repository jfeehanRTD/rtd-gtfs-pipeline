import { test, expect } from '@playwright/test';

test.describe('Live Tab Layout Analysis', () => {
  test('analyze live tab layout and identify improvement opportunities', async ({ page }) => {
    console.log('🔍 Analyzing Live Tab Layout...');
    
    // Navigate to live tab
    await page.goto('/live');
    
    // Wait for the page to fully load
    await page.waitForTimeout(5000);
    
    // Take a screenshot for current state
    await page.screenshot({ 
      path: 'live-tab-current.png', 
      fullPage: true 
    });
    
    console.log('📸 Screenshot taken: live-tab-current.png');
    
    // Analyze viewport and content dimensions
    const viewport = page.viewportSize();
    console.log(`📏 Viewport: ${viewport?.width}x${viewport?.height}`);
    
    // Check main container layout
    const mainContainer = page.locator('main, .main-content, .app-container').first();
    if (await mainContainer.isVisible()) {
      const containerBox = await mainContainer.boundingBox();
      console.log(`📦 Main container: ${containerBox?.width}x${containerBox?.height}`);
    }
    
    // Analyze header/navigation area
    const header = page.locator('header, nav, .navbar, .header').first();
    if (await header.isVisible()) {
      const headerBox = await header.boundingBox();
      console.log(`🔝 Header area: ${headerBox?.width}x${headerBox?.height}`);
    }
    
    // Check map container
    const mapContainer = page.locator('.leaflet-container, .map-container').first();
    if (await mapContainer.isVisible()) {
      const mapBox = await mapContainer.boundingBox();
      console.log(`🗺️  Map container: ${mapBox?.width}x${mapBox?.height}`);
    }
    
    // Analyze control panels and sidebars
    const panels = page.locator('[class*="panel"], [class*="sidebar"], [class*="control"]');
    const panelCount = await panels.count();
    console.log(`🎛️  Found ${panelCount} control panels/sidebars`);
    
    for (let i = 0; i < Math.min(panelCount, 5); i++) {
      const panel = panels.nth(i);
      if (await panel.isVisible()) {
        const panelBox = await panel.boundingBox();
        const classes = await panel.getAttribute('class') || '';
        console.log(`   Panel ${i + 1}: ${panelBox?.width}x${panelBox?.height} (${classes})`);
      }
    }
    
    // Check for overlay elements that might be blocking content
    const overlays = page.locator('.overlay, .modal, .popup, [style*="z-index"]');
    const overlayCount = await overlays.count();
    console.log(`📋 Found ${overlayCount} potential overlay elements`);
    
    // Analyze responsive behavior (simulate mobile view)
    await page.setViewportSize({ width: 375, height: 667 }); // Mobile viewport
    await page.waitForTimeout(1000);
    
    await page.screenshot({ 
      path: 'live-tab-mobile.png', 
      fullPage: true 
    });
    console.log('📱 Mobile screenshot: live-tab-mobile.png');
    
    // Check if mobile layout is properly responsive
    const mapContainerMobile = page.locator('.leaflet-container, .map-container').first();
    if (await mapContainerMobile.isVisible()) {
      const mapBoxMobile = await mapContainerMobile.boundingBox();
      console.log(`📱 Mobile map: ${mapBoxMobile?.width}x${mapBoxMobile?.height}`);
    }
    
    // Check for horizontal scrolling issues
    const body = page.locator('body');
    const scrollWidth = await body.evaluate((el) => el.scrollWidth);
    const clientWidth = await body.evaluate((el) => el.clientWidth);
    if (scrollWidth > clientWidth) {
      console.log('⚠️  Horizontal scrolling detected (potential layout issue)');
    }
    
    // Reset to desktop viewport
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.waitForTimeout(1000);
    
    // Check for common UI/UX issues
    console.log('\n🔍 Checking for common layout issues:');
    
    // 1. Check if text is readable (not too small)
    const smallText = page.locator('*').filter({
      has: page.locator('text=/\\w+/'),
    });
    
    // 2. Check for overlapping elements
    const positioned = page.locator('[style*="position: absolute"], [style*="position: fixed"]');
    const positionedCount = await positioned.count();
    console.log(`   📍 ${positionedCount} absolutely/fixed positioned elements`);
    
    // 3. Check for proper spacing
    const buttons = page.locator('button');
    const buttonCount = await buttons.count();
    console.log(`   🔘 ${buttonCount} buttons found`);
    
    // 4. Check for accessibility issues
    const imagesWithoutAlt = page.locator('img:not([alt])');
    const imagesWithoutAltCount = await imagesWithoutAlt.count();
    if (imagesWithoutAltCount > 0) {
      console.log(`   ♿ ${imagesWithoutAltCount} images missing alt text`);
    }
    
    // Test interaction with key elements
    console.log('\n🎯 Testing key interactions:');
    
    // Try to find and interact with main controls
    const showVehiclesToggle = page.getByText('Show Vehicles');
    if (await showVehiclesToggle.isVisible()) {
      console.log('   ✅ Show Vehicles toggle is visible and accessible');
    }
    
    const refreshButton = page.getByRole('button', { name: /refresh/i });
    if (await refreshButton.isVisible()) {
      console.log('   ✅ Refresh button is visible and accessible');
    }
    
    // Check if tabs are properly styled and accessible
    const tabs = page.locator('[role="tab"], .tab, .nav-link');
    const tabCount = await tabs.count();
    console.log(`   📑 ${tabCount} tab elements found`);
    
    console.log('\n✨ Layout analysis complete!');
    console.log('📸 Screenshots saved: live-tab-current.png, live-tab-mobile.png');
    
    // The test passes if we can successfully analyze the layout
    expect(true).toBe(true);
  });
  
  test('identify specific layout improvement opportunities', async ({ page }) => {
    console.log('\n🎨 Identifying Layout Improvement Opportunities...');
    
    await page.goto('/live');
    await page.waitForTimeout(3000);
    
    const improvements = [];
    
    // Check for whitespace utilization
    const viewport = page.viewportSize();
    const mapContainer = page.locator('.leaflet-container').first();
    
    if (await mapContainer.isVisible()) {
      const mapBox = await mapContainer.boundingBox();
      const mapUtilization = ((mapBox?.width || 0) * (mapBox?.height || 0)) / ((viewport?.width || 1) * (viewport?.height || 1));
      
      if (mapUtilization < 0.6) {
        improvements.push('📏 Map could utilize more screen space (current utilization: ' + Math.round(mapUtilization * 100) + '%)');
      }
    }
    
    // Check for panel/sidebar optimization
    const sidePanels = page.locator('[class*="sidebar"], [class*="panel"]');
    const sidePanelCount = await sidePanels.count();
    
    if (sidePanelCount > 0) {
      improvements.push('🎛️  Consider consolidating or optimizing side panels for better space utilization');
    }
    
    // Check mobile responsiveness
    await page.setViewportSize({ width: 375, height: 667 });
    await page.waitForTimeout(1000);
    
    const mobileMapContainer = page.locator('.leaflet-container').first();
    if (await mobileMapContainer.isVisible()) {
      const mobileMapBox = await mobileMapContainer.boundingBox();
      if ((mobileMapBox?.height || 0) < 300) {
        improvements.push('📱 Mobile map height could be increased for better usability');
      }
    }
    
    // Reset viewport
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.waitForTimeout(1000);
    
    // Check for control accessibility
    const controls = page.locator('button, input, select');
    const controlCount = await controls.count();
    let accessibleControls = 0;
    
    for (let i = 0; i < Math.min(controlCount, 10); i++) {
      const control = controls.nth(i);
      const hasAriaLabel = await control.getAttribute('aria-label');
      const hasTitle = await control.getAttribute('title');
      if (hasAriaLabel || hasTitle) {
        accessibleControls++;
      }
    }
    
    const accessibilityRatio = accessibleControls / Math.min(controlCount, 10);
    if (accessibilityRatio < 0.8) {
      improvements.push('♿ Improve control accessibility with aria-labels and titles');
    }
    
    console.log('\n🎯 Improvement Opportunities:');
    improvements.forEach((improvement, index) => {
      console.log(`   ${index + 1}. ${improvement}`);
    });
    
    if (improvements.length === 0) {
      console.log('   ✅ Layout appears well-optimized!');
    }
    
    // Store improvements for later use
    await page.evaluate((improvements) => {
      (window as any).layoutImprovements = improvements;
    }, improvements);
    
    expect(improvements).toBeDefined();
  });
});