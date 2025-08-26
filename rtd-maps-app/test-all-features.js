#!/usr/bin/env node

const { chromium } = require('playwright');

async function testAllFeatures() {
  console.log('🚀 Testing all RTD Maps App features on http://localhost:3000\n');
  console.log('=' .repeat(60));
  
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
  const page = await context.newPage();
  
  const results = {
    passed: [],
    failed: []
  };
  
  try {
    // Test 1: Static Map View
    console.log('\n📍 Test 1: Static Map View');
    console.log('-'.repeat(40));
    
    await page.goto('http://localhost:3000/', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);
    
    const staticMapTitle = await page.textContent('h1');
    if (staticMapTitle.includes('RTD Live Transit Map')) {
      console.log('✅ Static map page loaded');
      results.passed.push('Static map page load');
    } else {
      console.log('❌ Static map page failed to load');
      results.failed.push('Static map page load');
    }
    
    // Check for vehicle count
    const vehicleCountElement = await page.$('text=/\\d+\\s*\\/\\s*\\d+\\s*vehicles/');
    if (vehicleCountElement) {
      const vehicleText = await vehicleCountElement.textContent();
      console.log(`✅ Vehicle count displayed: ${vehicleText}`);
      results.passed.push('Static map vehicle count');
    } else {
      console.log('❌ Vehicle count not found');
      results.failed.push('Static map vehicle count');
    }
    
    await page.screenshot({ path: 'test-static-map.png', fullPage: true });
    console.log('📸 Screenshot: test-static-map.png');
    
    // Test 2: Live Transit Map
    console.log('\n📍 Test 2: Live Transit Map');
    console.log('-'.repeat(40));
    
    await page.click('a:has-text("Live Transit")');
    await page.waitForTimeout(2000);
    
    const liveMapHeader = await page.textContent('.text-gray-600');
    if (liveMapHeader && liveMapHeader.includes('Live SIRI')) {
      console.log('✅ Live transit map loaded');
      results.passed.push('Live transit map load');
    } else {
      console.log('❌ Live transit map failed to load');
      results.failed.push('Live transit map load');
    }
    
    // Check for buses and trains sections
    const busesSection = await page.$('text=/Buses\\s*\\(\\d+\\)/');
    const trainsSection = await page.$('text=/Trains\\s*\\(\\d+\\)/');
    
    if (busesSection) {
      const busText = await busesSection.textContent();
      console.log(`✅ Bus section found: ${busText}`);
      results.passed.push('Live map bus section');
    } else {
      console.log('❌ Bus section not found');
      results.failed.push('Live map bus section');
    }
    
    if (trainsSection) {
      const trainText = await trainsSection.textContent();
      console.log(`✅ Train section found: ${trainText}`);
      results.passed.push('Live map train section');
    } else {
      console.log('❌ Train section not found');
      results.failed.push('Live map train section');
    }
    
    await page.screenshot({ path: 'test-live-map.png', fullPage: true });
    console.log('📸 Screenshot: test-live-map.png');
    
    // Test 3: Admin Dashboard
    console.log('\n📍 Test 3: Admin Dashboard');
    console.log('-'.repeat(40));
    
    await page.goto('http://localhost:3000/admin', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);
    
    // Check for admin title
    const adminTitle = await page.$('h1:has-text("Admin")');
    if (adminTitle) {
      console.log('✅ Admin dashboard loaded');
      results.passed.push('Admin dashboard load');
    } else {
      console.log('❌ Admin dashboard failed to load');
      results.failed.push('Admin dashboard load');
    }
    
    // Test navigation tabs
    const tabs = ['Overview', 'Subscriptions', 'Live Feeds', 'Messages', 'Errors', 'Occupancy Analysis'];
    console.log('\n🔄 Testing navigation tabs:');
    
    for (let tab of tabs) {
      try {
        const tabButton = await page.$(`button:has-text("${tab}")`);
        if (tabButton) {
          await tabButton.click();
          await page.waitForTimeout(1000);
          
          // Check if tab content changed
          const activeTab = await page.$('.bg-blue-50.text-blue-700');
          const activeTabText = await activeTab?.innerText();
          
          if (activeTabText === tab) {
            console.log(`  ✅ ${tab} tab works`);
            results.passed.push(`Admin ${tab} tab`);
          } else {
            console.log(`  ❌ ${tab} tab failed`);
            results.failed.push(`Admin ${tab} tab`);
          }
        }
      } catch (error) {
        console.log(`  ❌ ${tab} tab error: ${error.message}`);
        results.failed.push(`Admin ${tab} tab`);
      }
    }
    
    await page.screenshot({ path: 'test-admin-dashboard.png', fullPage: true });
    console.log('\n📸 Screenshot: test-admin-dashboard.png');
    
    // Test 4: Sidebar Toggle
    console.log('\n📍 Test 4: Sidebar Toggle');
    console.log('-'.repeat(40));
    
    const toggleButton = await page.$('button[class*="p-1 rounded-md hover:bg-gray-100"]');
    if (toggleButton) {
      await toggleButton.click();
      await page.waitForTimeout(500);
      
      const collapsedSidebar = await page.$('.w-16');
      if (collapsedSidebar) {
        console.log('✅ Sidebar collapse works');
        results.passed.push('Sidebar collapse');
        
        await toggleButton.click();
        await page.waitForTimeout(500);
        
        const expandedSidebar = await page.$('.w-64');
        if (expandedSidebar) {
          console.log('✅ Sidebar expand works');
          results.passed.push('Sidebar expand');
        }
      } else {
        console.log('❌ Sidebar toggle failed');
        results.failed.push('Sidebar toggle');
      }
    }
    
    // Test 5: Search Functionality
    console.log('\n📍 Test 5: Search Functionality');
    console.log('-'.repeat(40));
    
    await page.click('button:has-text("Subscriptions")');
    await page.waitForTimeout(1000);
    
    const searchInput = await page.$('input[placeholder="Search..."]');
    if (searchInput) {
      await searchInput.fill('rail');
      await page.waitForTimeout(500);
      console.log('✅ Search input works');
      results.passed.push('Search functionality');
      
      await searchInput.fill('');
    } else {
      console.log('❌ Search input not found');
      results.failed.push('Search functionality');
    }
    
    // Test 6: Navigation Links
    console.log('\n📍 Test 6: Navigation Links');
    console.log('-'.repeat(40));
    
    // Test "Back to Maps" link
    const backToMapsLink = await page.$('a:has-text("Back to Maps")');
    if (backToMapsLink) {
      await backToMapsLink.click();
      await page.waitForTimeout(1500);
      
      const currentUrl = page.url();
      if (currentUrl === 'http://localhost:3000/') {
        console.log('✅ "Back to Maps" navigation works');
        results.passed.push('Back to Maps navigation');
      } else {
        console.log('❌ "Back to Maps" navigation failed');
        results.failed.push('Back to Maps navigation');
      }
    }
    
    // Test navigation between all main views
    const navTests = [
      { link: 'Static Map', expectedPath: '/' },
      { link: 'Live Transit', expectedPath: '/live' },
      { link: 'Admin', expectedPath: '/admin' }
    ];
    
    for (let navTest of navTests) {
      await page.click(`a:has-text("${navTest.link}")`);
      await page.waitForTimeout(1000);
      
      const currentPath = new URL(page.url()).pathname;
      if (currentPath === navTest.expectedPath) {
        console.log(`✅ Navigation to ${navTest.link} works`);
        results.passed.push(`Navigation to ${navTest.link}`);
      } else {
        console.log(`❌ Navigation to ${navTest.link} failed`);
        results.failed.push(`Navigation to ${navTest.link}`);
      }
    }
    
    // Final Summary
    console.log('\n' + '='.repeat(60));
    console.log('📊 TEST RESULTS SUMMARY');
    console.log('='.repeat(60));
    console.log(`✅ Passed: ${results.passed.length} tests`);
    console.log(`❌ Failed: ${results.failed.length} tests`);
    console.log(`📈 Success Rate: ${Math.round((results.passed.length / (results.passed.length + results.failed.length)) * 100)}%`);
    
    if (results.failed.length > 0) {
      console.log('\n❌ Failed Tests:');
      results.failed.forEach(test => console.log(`  - ${test}`));
    }
    
    if (results.passed.length > 0) {
      console.log('\n✅ Passed Tests:');
      results.passed.forEach(test => console.log(`  - ${test}`));
    }
    
    console.log('\n✨ All tests completed!');
    
  } catch (error) {
    console.error('\n❌ Critical test error:', error.message);
    results.failed.push('Critical error: ' + error.message);
  } finally {
    await browser.close();
  }
}

testAllFeatures();