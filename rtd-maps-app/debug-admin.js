#!/usr/bin/env node

const { chromium } = require('playwright');

async function debugAdmin() {
  console.log('🐛 Debugging admin interface...\n');
  
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
  const page = await context.newPage();
  
  // Listen to console messages
  page.on('console', msg => {
    if (msg.type() === 'error') {
      console.log('❌ Console Error:', msg.text());
    }
  });
  
  // Listen to page errors
  page.on('pageerror', error => {
    console.log('❌ Page Error:', error.message);
  });
  
  try {
    await page.goto('http://localhost:3002/admin', { waitUntil: 'networkidle' });
    await page.waitForTimeout(3000);
    
    console.log('🔍 Checking for React errors...');
    
    // Check if there are any visible error boundaries or React errors
    const errorElements = await page.$$('[data-testid*="error"], .error, [class*="error"]');
    if (errorElements.length > 0) {
      console.log('❌ Found error elements on page');
      for (let elem of errorElements) {
        const text = await elem.textContent();
        console.log('   Error text:', text);
      }
    } else {
      console.log('✅ No visible error elements found');
    }
    
    // Test clicking tabs individually with better error handling
    const tabs = ['Subscriptions', 'Live Feeds', 'Messages'];
    
    for (let tab of tabs) {
      console.log(`\n📍 Testing ${tab} tab...`);
      
      try {
        const tabButton = await page.waitForSelector(`button:has-text("${tab}")`, { timeout: 5000 });
        await tabButton.click();
        await page.waitForTimeout(1000);
        
        // Check if content changed
        const content = await page.textContent('main') || await page.textContent('[class*="flex-1"]');
        console.log(`   ✅ ${tab} tab clicked, content loaded`);
        console.log(`   Content preview: ${content?.substring(0, 100)}...`);
        
      } catch (error) {
        console.log(`   ❌ Error with ${tab} tab:`, error.message);
      }
    }
    
    await page.waitForTimeout(5000); // Keep browser open for manual inspection
    
  } catch (error) {
    console.error('❌ Error:', error.message);
  } finally {
    await browser.close();
  }
}

debugAdmin();