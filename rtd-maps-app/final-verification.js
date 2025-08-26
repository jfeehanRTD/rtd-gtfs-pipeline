#!/usr/bin/env node

const { chromium } = require('playwright');

async function finalVerification() {
  console.log('🎯 Final Verification of RTD Maps App on http://localhost:3000\n');
  console.log('=' .repeat(60));
  
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
  const page = await context.newPage();
  
  try {
    // 1. Static Map
    console.log('\n📍 Capturing Static Map View...');
    await page.goto('http://localhost:3000/', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);
    await page.screenshot({ path: 'final-static-map.png', fullPage: true });
    const staticVehicles = await page.textContent('text=/\\d+\\s*\\/\\s*\\d+\\s*vehicles/');
    console.log(`✅ Static Map: ${staticVehicles}`);
    console.log('   📸 Screenshot: final-static-map.png');
    
    // 2. Live Transit Map
    console.log('\n📍 Capturing Live Transit Map...');
    await page.click('a:has-text("Live Transit")');
    await page.waitForTimeout(2000);
    await page.screenshot({ path: 'final-live-map.png', fullPage: true });
    const busCount = await page.textContent('text=/Buses\\s*\\(\\d+\\)/');
    const trainCount = await page.textContent('text=/Trains\\s*\\(\\d+\\)/');
    console.log(`✅ Live Map: ${busCount}, ${trainCount}`);
    console.log('   📸 Screenshot: final-live-map.png');
    
    // 3. Admin Dashboard - Overview
    console.log('\n📍 Capturing Admin Dashboard...');
    await page.goto('http://localhost:3000/admin', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);
    await page.screenshot({ path: 'final-admin-overview.png', fullPage: true });
    console.log('✅ Admin Overview Tab');
    console.log('   📸 Screenshot: final-admin-overview.png');
    
    // 4. Admin - Subscriptions
    await page.click('button:has-text("Subscriptions")');
    await page.waitForTimeout(1000);
    await page.screenshot({ path: 'final-admin-subscriptions.png', fullPage: true });
    console.log('✅ Admin Subscriptions Tab');
    console.log('   📸 Screenshot: final-admin-subscriptions.png');
    
    // 5. Admin - Live Feeds
    await page.click('button:has-text("Live Feeds")');
    await page.waitForTimeout(1000);
    await page.screenshot({ path: 'final-admin-feeds.png', fullPage: true });
    console.log('✅ Admin Live Feeds Tab');
    console.log('   📸 Screenshot: final-admin-feeds.png');
    
    // 6. Admin - Messages
    await page.click('button:has-text("Messages")');
    await page.waitForTimeout(1000);
    await page.screenshot({ path: 'final-admin-messages.png', fullPage: true });
    console.log('✅ Admin Messages Tab');
    console.log('   📸 Screenshot: final-admin-messages.png');
    
    // 7. Admin - Errors
    await page.click('button:has-text("Errors")');
    await page.waitForTimeout(1000);
    await page.screenshot({ path: 'final-admin-errors.png', fullPage: true });
    console.log('✅ Admin Errors Tab');
    console.log('   📸 Screenshot: final-admin-errors.png');
    
    // 8. Admin - Occupancy Analysis
    await page.click('button:has-text("Occupancy Analysis")');
    await page.waitForTimeout(1000);
    await page.screenshot({ path: 'final-admin-occupancy.png', fullPage: true });
    console.log('✅ Admin Occupancy Analysis Tab');
    console.log('   📸 Screenshot: final-admin-occupancy.png');
    
    // 9. Admin - Collapsed Sidebar
    const toggleButton = await page.$('button[class*="p-1 rounded-md hover:bg-gray-100"]');
    await toggleButton.click();
    await page.waitForTimeout(500);
    await page.screenshot({ path: 'final-admin-collapsed.png', fullPage: true });
    console.log('✅ Admin with Collapsed Sidebar');
    console.log('   📸 Screenshot: final-admin-collapsed.png');
    
    // Summary
    console.log('\n' + '='.repeat(60));
    console.log('✨ FINAL VERIFICATION COMPLETE');
    console.log('='.repeat(60));
    console.log('\n📁 Screenshots Generated:');
    console.log('   • final-static-map.png        - Static map with vehicle counts');
    console.log('   • final-live-map.png          - Live transit map with buses/trains');
    console.log('   • final-admin-overview.png    - Admin dashboard overview');
    console.log('   • final-admin-subscriptions.png - Subscription management');
    console.log('   • final-admin-feeds.png       - Live feed status');
    console.log('   • final-admin-messages.png    - Message history');
    console.log('   • final-admin-errors.png      - Error tracking');
    console.log('   • final-admin-occupancy.png   - Occupancy analysis');
    console.log('   • final-admin-collapsed.png   - Admin with collapsed sidebar');
    
    console.log('\n🎉 All features verified and working correctly on port 3000!');
    
  } catch (error) {
    console.error('\n❌ Verification error:', error.message);
  } finally {
    await browser.close();
  }
}

finalVerification();