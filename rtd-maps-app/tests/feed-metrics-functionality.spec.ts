import { test, expect } from '@playwright/test';

test.describe('Feed Metrics Functionality', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to the admin dashboard
    await page.goto('http://localhost:3000/admin');
    
    // Wait for the page to load
    await page.waitForLoadState('networkidle');
  });

  test('should display feed metrics panel with all three feeds', async ({ page }) => {
    // Wait for Feed Metrics Panel to be visible
    await expect(page.locator('text=Feed Metrics')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('text=(Updates every 5s)')).toBeVisible();

    // Verify all three feed cards are present
    await expect(page.locator('text=SIRI Bus Feed')).toBeVisible();
    await expect(page.locator('text=LRGPS Light Rail Feed')).toBeVisible();
    await expect(page.locator('text=RailComm SCADA Feed')).toBeVisible();

    // Verify feed icons are displayed
    await expect(page.locator('text=🚌')).toBeVisible(); // SIRI Bus
    await expect(page.locator('text=🚊')).toBeVisible(); // LRGPS Light Rail
    await expect(page.locator('text=🛤️')).toBeVisible(); // RailComm
  });

  test('should display initial metrics structure', async ({ page }) => {
    // Wait for metrics panel
    await page.waitForSelector('text=Feed Metrics', { timeout: 10000 });

    // Check for 5-second interval sections
    await expect(page.locator('text=Last 5 Seconds')).toHaveCount(3); // One for each feed

    // Check for running totals sections
    await expect(page.locator('text=Running Totals')).toBeVisible();

    // Verify metric labels are present
    await expect(page.locator('text=Messages')).toBeVisible();
    await expect(page.locator('text=Connections')).toBeVisible();
    await expect(page.locator('text=Errors')).toBeVisible();
  });

  test('should show waiting status initially', async ({ page }) => {
    // Wait for feed metrics to load
    await page.waitForSelector('text=Feed Metrics', { timeout: 10000 });

    // All feeds should show "WAITING" status initially
    await expect(page.locator('text=WAITING')).toHaveCount(3);
  });

  test('should display system uptime and last update time', async ({ page }) => {
    // Wait for metrics panel
    await page.waitForSelector('text=Feed Metrics', { timeout: 10000 });

    // Check for system info section
    await expect(page.locator('text=System Uptime:')).toBeVisible();
    await expect(page.locator('text=Last System Update:')).toBeVisible();
    await expect(page.locator('text=Last Update:')).toBeVisible();
  });

  test('should have reset buttons for each feed', async ({ page }) => {
    // Wait for metrics panel
    await page.waitForSelector('text=Feed Metrics', { timeout: 10000 });

    // Count reset buttons (should be 3, one for each feed)
    const resetButtons = page.locator('button[title="Reset metrics"]');
    await expect(resetButtons).toHaveCount(3);
  });

  test('should update metrics automatically every 5 seconds', async ({ page }) => {
    // Wait for initial load
    await page.waitForSelector('text=Feed Metrics', { timeout: 10000 });

    // Get initial timestamp
    const initialUpdate = await page.locator('text=Last Update:').textContent();
    
    // Wait 6 seconds for the next update cycle
    await page.waitForTimeout(6000);

    // Get new timestamp - should be different
    const updatedTime = await page.locator('text=Last Update:').textContent();
    
    // The timestamps should be different (updated)
    expect(updatedTime).not.toBe(initialUpdate);
  });

  test('should handle API errors gracefully', async ({ page }) => {
    // Intercept API calls and return error
    await page.route('**/api/metrics/all', route => {
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Internal Server Error' })
      });
    });

    await page.goto('http://localhost:3000/admin');
    await page.waitForLoadState('networkidle');

    // Should still show metrics panel but with error status
    await expect(page.locator('text=Feed Metrics')).toBeVisible();
    
    // Should show error status for all feeds
    await page.waitForTimeout(2000);
    await expect(page.locator('text=ERROR')).toHaveCount(3);
  });

  test('should display correct metrics when API returns data', async ({ page }) => {
    // Mock successful API response with test data
    const mockMetrics = {
      feeds: {
        siri: {
          feed_name: "SIRI Bus Feed",
          health_status: "healthy",
          last_message: "2025-08-27T10:30:00.000Z",
          last_5s: { messages: 5, connections: 4, errors: 0, interval_seconds: 5 },
          totals: { 
            messages: 150, 
            connections: 145, 
            errors: 0, 
            uptime_seconds: 300,
            avg_messages_per_min: 30,
            avg_connections_per_min: 29
          },
          rates: { connection_rate_percent: 96.7, error_rate_percent: 0 }
        },
        lrgps: {
          feed_name: "LRGPS Light Rail Feed",
          health_status: "healthy",
          last_message: "2025-08-27T10:30:05.000Z",
          last_5s: { messages: 3, connections: 3, errors: 0, interval_seconds: 5 },
          totals: { 
            messages: 89, 
            connections: 89, 
            errors: 0, 
            uptime_seconds: 300,
            avg_messages_per_min: 18,
            avg_connections_per_min: 18
          },
          rates: { connection_rate_percent: 100.0, error_rate_percent: 0 }
        },
        railcomm: {
          feed_name: "RailComm SCADA Feed",
          health_status: "warning",
          last_message: "2025-08-27T10:29:30.000Z",
          last_5s: { messages: 1, connections: 0, errors: 1, interval_seconds: 5 },
          totals: { 
            messages: 45, 
            connections: 32, 
            errors: 5, 
            uptime_seconds: 300,
            avg_messages_per_min: 9,
            avg_connections_per_min: 6
          },
          rates: { connection_rate_percent: 71.1, error_rate_percent: 11.1 }
        }
      },
      timestamp: "2025-08-27T10:30:10.000Z",
      uptime_ms: 300000
    };

    // Intercept API calls and return mock data
    await page.route('**/api/metrics/all', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockMetrics)
      });
    });

    await page.goto('http://localhost:3000/admin');
    await page.waitForLoadState('networkidle');
    
    // Wait for metrics to load
    await page.waitForSelector('text=Feed Metrics', { timeout: 10000 });
    await page.waitForTimeout(2000);

    // Verify SIRI metrics
    await expect(page.locator('text=HEALTHY')).toHaveCount(2); // SIRI and LRGPS
    await expect(page.locator('text=WARNING')).toHaveCount(1); // RailComm

    // Check specific metric values
    await expect(page.locator('text=96.7%')).toBeVisible(); // SIRI connection rate
    await expect(page.locator('text=100.0%')).toBeVisible(); // LRGPS connection rate
    await expect(page.locator('text=71.1%')).toBeVisible(); // RailComm connection rate

    // Check 5-second interval values
    const siriCard = page.locator('text=SIRI Bus Feed').locator('..').locator('..');
    await expect(siriCard.locator('text=5')).toBeVisible(); // Messages in last 5s
    await expect(siriCard.locator('text=4')).toBeVisible(); // Connections in last 5s
  });

  test('should allow reset functionality for individual feeds', async ({ page }) => {
    // Mock API calls
    await page.route('**/api/metrics/all', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          feeds: {
            siri: {
              feed_name: "SIRI Bus Feed",
              health_status: "healthy",
              last_message: null,
              last_5s: { messages: 0, connections: 0, errors: 0, interval_seconds: 5 },
              totals: { messages: 100, connections: 95, errors: 0, uptime_seconds: 300, avg_messages_per_min: 20, avg_connections_per_min: 19 }
            },
            lrgps: { feed_name: "LRGPS Light Rail Feed", health_status: "waiting", last_message: null, last_5s: { messages: 0, connections: 0, errors: 0, interval_seconds: 5 }, totals: { messages: 0, connections: 0, errors: 0, uptime_seconds: 0, avg_messages_per_min: 0, avg_connections_per_min: 0 } },
            railcomm: { feed_name: "RailComm SCADA Feed", health_status: "waiting", last_message: null, last_5s: { messages: 0, connections: 0, errors: 0, interval_seconds: 5 }, totals: { messages: 0, connections: 0, errors: 0, uptime_seconds: 0, avg_messages_per_min: 0, avg_connections_per_min: 0 } }
          },
          timestamp: "2025-08-27T10:30:00.000Z",
          uptime_ms: 300000
        })
      });
    });

    // Mock reset API endpoint
    let resetCalled = false;
    await page.route('**/api/metrics/siri/reset', route => {
      resetCalled = true;
      route.fulfill({ status: 200 });
    });

    await page.goto('http://localhost:3000/admin');
    await page.waitForLoadState('networkidle');
    
    // Wait for metrics panel
    await page.waitForSelector('text=Feed Metrics', { timeout: 10000 });

    // Find and click the reset button for SIRI feed
    const siriCard = page.locator('text=SIRI Bus Feed').locator('..').locator('..');
    const resetButton = siriCard.locator('button[title="Reset metrics"]');
    await resetButton.click();

    // Wait a moment for the request
    await page.waitForTimeout(500);
    
    // Verify the API was called
    expect(resetCalled).toBe(true);
  });

  test('should be responsive on different screen sizes', async ({ page }) => {
    await page.goto('http://localhost:3000/admin');
    await page.waitForSelector('text=Feed Metrics', { timeout: 10000 });

    // Test mobile view
    await page.setViewportSize({ width: 375, height: 667 });
    await page.waitForTimeout(1000);
    
    // Feed metrics should still be visible
    await expect(page.locator('text=Feed Metrics')).toBeVisible();
    
    // Test tablet view
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.waitForTimeout(1000);
    
    // Should maintain visibility and usability
    await expect(page.locator('text=Feed Metrics')).toBeVisible();
    await expect(page.locator('text=SIRI Bus Feed')).toBeVisible();
    
    // Test desktop view
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.waitForTimeout(1000);
    
    // All feeds should be visible in grid layout
    await expect(page.locator('text=SIRI Bus Feed')).toBeVisible();
    await expect(page.locator('text=LRGPS Light Rail Feed')).toBeVisible();
    await expect(page.locator('text=RailComm SCADA Feed')).toBeVisible();
  });

  test('should handle network connectivity issues gracefully', async ({ page }) => {
    // Start with successful API
    await page.route('**/api/metrics/all', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          feeds: {
            siri: { feed_name: "SIRI Bus Feed", health_status: "healthy", last_message: null, last_5s: { messages: 0, connections: 0, errors: 0, interval_seconds: 5 }, totals: { messages: 0, connections: 0, errors: 0, uptime_seconds: 0, avg_messages_per_min: 0, avg_connections_per_min: 0 } },
            lrgps: { feed_name: "LRGPS Light Rail Feed", health_status: "waiting", last_message: null, last_5s: { messages: 0, connections: 0, errors: 0, interval_seconds: 5 }, totals: { messages: 0, connections: 0, errors: 0, uptime_seconds: 0, avg_messages_per_min: 0, avg_connections_per_min: 0 } },
            railcomm: { feed_name: "RailComm SCADA Feed", health_status: "waiting", last_message: null, last_5s: { messages: 0, connections: 0, errors: 0, interval_seconds: 5 }, totals: { messages: 0, connections: 0, errors: 0, uptime_seconds: 0, avg_messages_per_min: 0, avg_connections_per_min: 0 } }
          },
          timestamp: "2025-08-27T10:30:00.000Z",
          uptime_ms: 0
        })
      });
    });

    await page.goto('http://localhost:3000/admin');
    await page.waitForSelector('text=Feed Metrics', { timeout: 10000 });

    // Now simulate network failure
    await page.route('**/api/metrics/all', route => {
      route.abort('failed');
    });

    // Wait for next polling cycle
    await page.waitForTimeout(6000);

    // Should show error message or fallback state
    await expect(page.locator('text=Feed Metrics')).toBeVisible();
    // The component should handle network errors gracefully without crashing
  });

  test('should display correct time formatting for last message times', async ({ page }) => {
    const mockMetrics = {
      feeds: {
        siri: {
          feed_name: "SIRI Bus Feed",
          health_status: "healthy",
          last_message: "2025-08-27T10:30:15.000Z",
          last_5s: { messages: 0, connections: 0, errors: 0, interval_seconds: 5 },
          totals: { messages: 0, connections: 0, errors: 0, uptime_seconds: 60, avg_messages_per_min: 0, avg_connections_per_min: 0 }
        },
        lrgps: { feed_name: "LRGPS Light Rail Feed", health_status: "waiting", last_message: null, last_5s: { messages: 0, connections: 0, errors: 0, interval_seconds: 5 }, totals: { messages: 0, connections: 0, errors: 0, uptime_seconds: 0, avg_messages_per_min: 0, avg_connections_per_min: 0 } },
        railcomm: { feed_name: "RailComm SCADA Feed", health_status: "waiting", last_message: null, last_5s: { messages: 0, connections: 0, errors: 0, interval_seconds: 5 }, totals: { messages: 0, connections: 0, errors: 0, uptime_seconds: 0, avg_messages_per_min: 0, avg_connections_per_min: 0 } }
      },
      timestamp: "2025-08-27T10:30:20.000Z",
      uptime_ms: 60000
    };

    await page.route('**/api/metrics/all', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockMetrics)
      });
    });

    await page.goto('http://localhost:3000/admin');
    await page.waitForSelector('text=Feed Metrics', { timeout: 10000 });
    
    // Should show formatted last message time for SIRI
    const siriCard = page.locator('text=SIRI Bus Feed').locator('..').locator('..');
    await expect(siriCard.locator('text=Last:')).toBeVisible();

    // Should show uptime formatting
    await expect(page.locator('text=1m')).toBeVisible(); // System uptime
  });
});