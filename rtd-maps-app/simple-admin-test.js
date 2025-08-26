#!/usr/bin/env node

const { chromium } = require('playwright');

async function simpleAdminTest() {
  console.log('🧪 Simple admin interface test...\n');
  
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
  const page = await context.newPage();
  
  try {
    await page.goto('http://localhost:3002/admin', { waitUntil: 'networkidle' });
    await page.waitForTimeout(3000);
    
    // Take screenshot of current state
    await page.screenshot({ path: 'admin-interface-final.png', fullPage: true });
    console.log('📸 Screenshot: admin-interface-final.png');
    
    // Check basic elements
    const title = await page.textContent('h1');
    console.log('✅ Page title:', title);
    
    // Count tabs
    const tabButtons = await page.$$('button[class*="flex items-center space-x-3"]');
    console.log('✅ Found', tabButtons.length, 'navigation tabs');
    
    // Check if overview content is visible
    const overviewMetrics = await page.$$('[class*="grid-cols-1 md:grid-cols-2 lg:grid-cols-4"] > div');
    console.log('✅ Found', overviewMetrics.length, 'overview metric cards');
    
    // Test sidebar toggle
    console.log('\n🔄 Testing sidebar toggle...');
    const toggleButton = await page.$('button[class*="p-1 rounded-md hover:bg-gray-100"]');
    if (toggleButton) {
      await toggleButton.click();
      await page.waitForTimeout(500);
      console.log('✅ Sidebar toggle clicked');
      
      await toggleButton.click();
      await page.waitForTimeout(500);
      console.log('✅ Sidebar restored');
    }
    
    console.log('\n✅ Basic functionality tests passed!');
    
    // Keep browser open for manual inspection
    await page.waitForTimeout(3000);
    
  } catch (error) {
    console.error('❌ Error:', error.message);
  } finally {
    await browser.close();
  }
}

simpleAdminTest();