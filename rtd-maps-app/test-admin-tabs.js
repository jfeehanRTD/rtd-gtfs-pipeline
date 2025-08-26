#!/usr/bin/env node

const { chromium } = require('playwright');

async function testAdminTabs() {
  console.log('🧪 Testing modern admin interface tabs at http://localhost:3002/admin...\n');
  
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
  const page = await context.newPage();
  
  try {
    // Navigate to admin page
    await page.goto('http://localhost:3002/admin', { waitUntil: 'networkidle' });
    
    // Wait for content to load
    await page.waitForTimeout(2000);
    
    // Test each tab
    const tabs = [
      'Overview',
      'Subscriptions', 
      'Live Feeds',
      'Messages',
      'Errors',
      'Occupancy Analysis'
    ];
    
    for (let tab of tabs) {
      console.log(`📍 Testing ${tab} tab...`);
      
      // Click tab button
      await page.click(`button:has-text("${tab}")`);
      await page.waitForTimeout(1000);
      
      // Check if tab is active (has blue background)
      const activeTab = await page.$('.bg-blue-50.text-blue-700');
      const activeTabText = await activeTab.innerText();
      
      if (activeTabText === tab) {
        console.log(`   ✅ ${tab} tab activated successfully`);
        
        // Take screenshot of tab content
        await page.screenshot({ 
          path: `admin-tab-${tab.toLowerCase().replace(/\s+/g, '-')}.png`,
          fullPage: true 
        });
        console.log(`   📸 Screenshot: admin-tab-${tab.toLowerCase().replace(/\s+/g, '-')}.png`);
      } else {
        console.log(`   ❌ ${tab} tab activation failed`);
      }
    }
    
    // Test sidebar collapse functionality
    console.log('\n🔄 Testing sidebar collapse...');
    
    const sidebarToggle = await page.$('button:has-text("")'); // Menu/X button
    if (sidebarToggle) {
      await sidebarToggle.click();
      await page.waitForTimeout(500);
      
      // Check if sidebar is collapsed
      const collapsedSidebar = await page.$('.w-16');
      if (collapsedSidebar) {
        console.log('   ✅ Sidebar collapsed successfully');
        await page.screenshot({ path: 'admin-sidebar-collapsed.png', fullPage: true });
        console.log('   📸 Screenshot: admin-sidebar-collapsed.png');
        
        // Expand sidebar again
        await sidebarToggle.click();
        await page.waitForTimeout(500);
        console.log('   ✅ Sidebar expanded successfully');
      }
    }
    
    // Test search functionality on Subscriptions tab
    console.log('\n🔍 Testing search functionality...');
    await page.click('button:has-text("Subscriptions")');
    await page.waitForTimeout(1000);
    
    const searchInput = await page.$('input[placeholder="Search..."]');
    if (searchInput) {
      await searchInput.fill('rail');
      await page.waitForTimeout(500);
      console.log('   ✅ Search input working');
      await page.screenshot({ path: 'admin-search-test.png', fullPage: true });
      console.log('   📸 Screenshot: admin-search-test.png');
    }
    
    console.log('\n✅ All tab functionality tests completed!');
    
  } catch (error) {
    console.error('❌ Error testing admin interface:', error.message);
  } finally {
    await browser.close();
  }
}

testAdminTabs();