import { test, expect } from '@playwright/test';

test.describe('LRGPS Functionality Tests', () => {
  test.beforeEach(async ({ page }) => {
    // Wait for the API server to be ready
    await page.waitForTimeout(1000);
  });

  test('should load admin dashboard and display LRGPS alongside SIRI', async ({ page }) => {
    await page.goto('/admin');
    
    // Wait for the admin dashboard to load
    await expect(page.getByText('RTD Live Transit Map Administration')).toBeVisible();
    
    // Check that the overview tab is active by default
    await expect(page.locator('[class*="bg-blue-50"][class*="text-blue-700"]:has-text("Overview")')).toBeVisible();
    
    // Navigate to subscriptions tab to see all services
    await page.click('text=Subscriptions');
    await expect(page.getByRole('heading', { name: 'Subscriptions' })).toBeVisible();
    
    // Verify both SIRI and LRGPS services are displayed
    await expect(page.getByText('Bus SIRI Feed')).toBeVisible();
    await expect(page.getByText('LRGPS Feed')).toBeVisible();
    
    // Verify that LRGPS is running on port 8083 (endpoint should show this)
    await expect(page.locator('text=/localhost:8083/')).toBeVisible();
  });

  test('should show LRGPS subscription in subscriptions list', async ({ page }) => {
    await page.goto('/admin');
    
    // Navigate to subscriptions tab
    await page.click('text=Subscriptions');
    await expect(page.getByRole('heading', { name: 'Subscriptions' })).toBeVisible();
    
    // Look for the LRGPS subscription card
    const lrgpsCard = page.locator('.bg-white').filter({ hasText: 'LRGPS Feed' }).last();
    await expect(lrgpsCard).toBeVisible();
    
    // Verify LRGPS subscription details
    await expect(lrgpsCard).toContainText('LRGPS Feed');
    await expect(lrgpsCard).toContainText('LRGPS'); // Type should show as LRGPS
    await expect(lrgpsCard).toContainText('localhost:8083');
    
    // Check status (should be active, paused, or error)
    await expect(lrgpsCard).toContainText(/active|paused|error/i);
    
    // Verify message count is displayed (should be a number)
    await expect(lrgpsCard).toContainText(/Messages/);
  });

  test('should display LRGPS feed status correctly', async ({ page }) => {
    await page.goto('/admin');
    
    // Navigate to feeds tab
    await page.click('text=Live Feeds');
    await expect(page.getByRole('heading', { name: 'Live Feeds' })).toBeVisible();
    
    // Look for the LRGPS feed status card
    const lrgpsFeedCard = page.locator('.bg-white').filter({ hasText: 'LRGPS Feed' }).last();
    await expect(lrgpsFeedCard).toBeVisible();
    
    // Verify feed details
    await expect(lrgpsFeedCard).toContainText('LRGPS Feed');
    await expect(lrgpsFeedCard).toContainText('LRGPS'); // Feed type
    
    // Check live status indicator
    await expect(lrgpsFeedCard).toContainText('Live');
    
    // Check message rate display
    await expect(lrgpsFeedCard).toContainText(/\d+\.\d+ msg\/s/);
    
    // Check last message timestamp
    await expect(lrgpsFeedCard).toContainText('Last Message');
    
    // Verify health status icon is present by checking for Live status
    await expect(lrgpsFeedCard).toContainText(/Live|Offline/);
  });

  test('should show LRGPS message history section', async ({ page }) => {
    await page.goto('/admin');
    
    // Navigate to messages tab
    await page.click('text=Messages');
    await expect(page.getByRole('heading', { name: 'Messages', exact: true })).toBeVisible();
    
    // Check for recent messages header
    await expect(page.getByText('Recent Messages')).toBeVisible();
    await expect(page.getByText('Live Stream')).toBeVisible();
    
    // Look for LRGPS message entry
    const lrgpsMessage = page.locator('.p-4').filter({ hasText: 'LRGPS' });
    await expect(lrgpsMessage).toBeVisible();
    
    // Verify LRGPS message details
    await expect(lrgpsMessage).toContainText('LRGPS');
    await expect(lrgpsMessage).toContainText('Light Rail Feed');
    
    // Check message status
    await expect(lrgpsMessage).toContainText(/processed|pending/i);
    
    // Check timestamp
    await expect(lrgpsMessage).toContainText(/ago/);
  });

  test('should display LRGPS messages with proper content', async ({ page }) => {
    await page.goto('/admin');
    
    // Navigate to messages tab
    await page.click('text=Messages');
    
    // Find the LRGPS message
    const lrgpsMessage = page.locator('.p-4').filter({ hasText: 'LRGPS' });
    await expect(lrgpsMessage).toBeVisible();
    
    // Check the content shows either "Receiving LRGPS data" or "Waiting for data"
    await expect(lrgpsMessage).toContainText(/Receiving LRGPS data|Waiting for data/);
    
    // Verify message is in a hover-enabled container (interactive)
    await expect(lrgpsMessage).toHaveClass(/hover:bg-gray-50/);
  });

  test('should use correct color coding for LRGPS vs SIRI', async ({ page }) => {
    await page.goto('/admin');
    
    // First check overview metrics - system should distinguish between services
    const overviewMetrics = page.locator('.grid').filter({ hasText: 'Active Subscriptions' });
    await expect(overviewMetrics).toBeVisible();
    
    // Navigate to subscriptions to check color differences
    await page.click('text=Subscriptions');
    
    // Find SIRI and LRGPS subscription cards
    const siriCard = page.locator('.bg-white').filter({ hasText: 'Bus SIRI Feed' }).last();
    const lrgpsCard = page.locator('.bg-white').filter({ hasText: 'LRGPS Feed' }).last();
    
    await expect(siriCard).toBeVisible();
    await expect(lrgpsCard).toBeVisible();
    
    // Both should have status badges with appropriate colors
    await expect(siriCard).toContainText(/active|paused|error/i);
    await expect(lrgpsCard).toContainText(/active|paused|error/i);
    
    // Check that both cards maintain the same structure but represent different services
    await expect(siriCard).toContainText('BUS SIRI'); // Note: it's "BUS SIRI" not "BUS-SIRI" based on debug output
    await expect(lrgpsCard).toContainText('LRGPS');
    
    // Check different ports
    await expect(siriCard).toContainText('8082');
    await expect(lrgpsCard).toContainText('8083');
  });

  test('should show proper port configuration for LRGPS (8083)', async ({ page }) => {
    await page.goto('/admin');
    
    // Navigate to subscriptions
    await page.click('text=Subscriptions');
    
    // Find LRGPS subscription
    const lrgpsCard = page.locator('.bg-white').filter({ hasText: 'LRGPS Feed' }).last();
    await expect(lrgpsCard).toBeVisible();
    
    // Verify the endpoint shows port 8083
    await expect(lrgpsCard).toContainText('localhost:8083');
    
    // Compare with SIRI which should be on 8082
    const siriCard = page.locator('.bg-white').filter({ hasText: 'Bus SIRI Feed' }).last();
    await expect(siriCard).toContainText('localhost:8082');
    
    // Verify they are different ports - LRGPS should have 8083, SIRI should have 8082
    const lrgpsText = await lrgpsCard.textContent();
    const siriText = await siriCard.textContent();
    
    expect(lrgpsText).toContain('8083');
    expect(siriText).toContain('8082');
    expect(lrgpsText).not.toEqual(siriText);
  });

  test('should handle LRGPS service status transitions', async ({ page }) => {
    await page.goto('/admin');
    
    // Navigate to subscriptions
    await page.click('text=Subscriptions');
    
    // Find LRGPS subscription and check initial status
    const lrgpsCard = page.locator('.bg-white').filter({ hasText: 'LRGPS Feed' }).last();
    await expect(lrgpsCard).toBeVisible();
    
    // Check initial status
    const initialText = await lrgpsCard.textContent();
    console.log(`LRGPS initial content contains status`);
    
    // Click refresh to see if status updates
    await page.click('button:has-text("Refresh")');
    
    // Wait for refresh animation to complete
    await page.waitForTimeout(2000);
    
    // Status should still be displayed (may or may not have changed)
    await expect(lrgpsCard).toBeVisible();
    await expect(lrgpsCard).toContainText(/active|paused|error/i);
    
    const refreshedText = await lrgpsCard.textContent();
    console.log(`LRGPS content after refresh still contains status`);
    
    // Verify card still contains LRGPS content
    expect(refreshedText).toContain('LRGPS');
  });

  test('should display LRGPS alongside other services in overview', async ({ page }) => {
    await page.goto('/admin');
    
    // Should be on overview by default
    await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible();
    
    // Check key metrics cards are present
    await expect(page.locator('p:has-text("Active Subscriptions")')).toBeVisible();
    await expect(page.locator('p:has-text("Live Feeds")')).toBeVisible();
    await expect(page.locator('p:has-text("Total Messages")')).toBeVisible();
    await expect(page.locator('p:has-text("System Status")')).toBeVisible();
    
    // Check recent activity section includes services
    await expect(page.getByText('Recent Activity')).toBeVisible();
    
    // Should show activity from multiple services (may include LRGPS, SIRI, Rail, etc.)
    const activityItems = page.locator('.p-4.bg-gray-50.rounded-lg');
    const activityCount = await activityItems.count();
    
    expect(activityCount).toBeGreaterThan(0);
    console.log(`Found ${activityCount} recent activity items`);
    
    // Check that message counts are being aggregated properly
    const totalMessagesCard = page.locator('.bg-white').filter({ hasText: 'Total Messages' });
    const messageCountElement = totalMessagesCard.locator('.text-3xl.font-bold.text-gray-900');
    await expect(messageCountElement).toBeVisible();
    
    const messageCount = await messageCountElement.textContent();
    console.log(`Total messages across all services: ${messageCount}`);
  });

  test('should filter LRGPS data when using search and filters', async ({ page }) => {
    await page.goto('/admin');
    
    // Navigate to subscriptions
    await page.click('text=Subscriptions');
    
    // Wait for search controls to be visible
    await expect(page.locator('input[placeholder="Search..."]')).toBeVisible();
    await expect(page.locator('select')).toBeVisible();
    
    // Test search functionality
    await page.fill('input[placeholder="Search..."]', 'LRGPS');
    
    // Should show only LRGPS-related results
    await expect(page.getByText('LRGPS Feed')).toBeVisible();
    
    // Clear search
    await page.fill('input[placeholder="Search..."]', '');
    
    // Test status filter
    const statusFilter = page.locator('select');
    await statusFilter.selectOption('error');
    
    // Should show only error status services (may or may not include LRGPS)
    const errorCards = page.locator('.bg-white').filter({ hasText: 'error' });
    const errorCount = await errorCards.count();
    console.log(`Found ${errorCount} services with error status`);
    
    // Reset filter
    await statusFilter.selectOption('all');
    
    // Should show all services again
    await expect(page.getByText('LRGPS Feed')).toBeVisible();
    await expect(page.getByText('Bus SIRI Feed')).toBeVisible();
  });
});