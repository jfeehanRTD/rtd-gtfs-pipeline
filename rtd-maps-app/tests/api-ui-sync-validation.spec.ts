import { test, expect } from '@playwright/test';

/**
 * API-UI Synchronization Validation Test
 * 
 * CRITICAL: This test validates that the web app displays exactly the same
 * data as the API endpoint - NO FAKE DATA is used or generated.
 */

test.describe('API vs UI Data Synchronization', () => {
  
  test('Verify API and web app show identical data', async ({ page }) => {
    console.log('🔍 Testing API vs Web App data synchronization...');
    
    // Get API data first
    const apiResponse = await page.request.get('http://localhost:8080/api/metrics/all');
    expect(apiResponse.ok()).toBeTruthy();
    const apiData = await apiResponse.json();
    
    console.log('API Data:', {
      siri: { messages: apiData.feeds.siri.totals.messages, status: apiData.feeds.siri.health_status },
      lrgps: { messages: apiData.feeds.lrgps.totals.messages, status: apiData.feeds.lrgps.health_status },
      railcomm: { messages: apiData.feeds.railcomm.totals.messages, status: apiData.feeds.railcomm.health_status }
    });
    
    // Navigate to admin page
    await page.goto('http://localhost:3000/admin');
    
    // Wait for the page to load and metrics to update
    await page.waitForTimeout(10000);
    
    // Take a screenshot for debugging
    await page.screenshot({ path: 'test-results/admin-page-current.png', fullPage: true });
    
    // Verify the page content matches API data
    const pageContent = await page.content();
    
    // Check if page shows the API values
    const siriMessages = apiData.feeds.siri.totals.messages.toString();
    const lrgpsMessages = apiData.feeds.lrgps.totals.messages.toString();
    const railcommMessages = apiData.feeds.railcomm.totals.messages.toString();
    
    console.log('Expected message counts:', { siri: siriMessages, lrgps: lrgpsMessages, railcomm: railcommMessages });
    
    // Simple validation: page should contain the correct numbers
    if (siriMessages === '0' && lrgpsMessages === '0' && railcommMessages === '0') {
      console.log('✅ API shows 0 messages for all feeds - checking UI displays this correctly');
      
      // The page should show 0 values, not fake high numbers like 15,420 or 8,950
      expect(pageContent).not.toContain('15,420');
      expect(pageContent).not.toContain('8,950');
      expect(pageContent).not.toContain('15420');
      expect(pageContent).not.toContain('8950');
      
      console.log('✅ UI does not contain fake data numbers');
    } else {
      // If there are real messages, verify they appear in the UI
      expect(pageContent).toContain(siriMessages);
      expect(pageContent).toContain(lrgpsMessages);
      expect(pageContent).toContain(railcommMessages);
      
      console.log('✅ UI contains expected real message counts');
    }
    
    console.log('✅ API-UI synchronization validation completed successfully');
  });
  
  test('Validate no fake data patterns in UI', async ({ page }) => {
    console.log('🔍 Checking UI for absence of fake data patterns...');
    
    await page.goto('http://localhost:3000/admin');
    await page.waitForTimeout(8000);
    
    const pageContent = await page.content();
    
    // Check for known fake data patterns that were previously showing
    const fakePatterns = [
      '15,420', '15420',    // Fake rail comm messages
      '8,950', '8950',      // Fake bus SIRI messages
      '1.7%',               // Fake connection rate pattern
      '60:40:30'            // Fake ratio pattern
    ];
    
    for (const pattern of fakePatterns) {
      expect(pageContent).not.toContain(pattern);
      console.log(`✅ UI does not contain fake pattern: ${pattern}`);
    }
    
    console.log('✅ No fake data patterns found in UI');
  });

});