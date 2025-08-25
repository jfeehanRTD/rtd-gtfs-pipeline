# Playwright MCP Setup for RTD Maps Application

This document explains how to use Playwright with Model Context Protocol (MCP) to automate and control the RTD React web application.

## Overview

The Playwright MCP setup provides:
- **Automated browser testing** of the RTD transit maps
- **MCP protocol integration** for Claude Code to control the application
- **Vehicle count comparison** between static and live maps  
- **Interactive map controls testing**
- **Continuous monitoring capabilities**

## Files Created

### Core MCP Files
- `mcp-playwright-server.js` - MCP server that exposes Playwright automation tools
- `mcp-client-example.js` - Example client showing how to use the MCP server
- `playwright.config.ts` - Playwright configuration for the React app

### Test Files  
- `tests/example.spec.ts` - Basic application functionality tests
- `tests/vehicle-count-comparison.spec.ts` - Specific tests for comparing vehicle counts
- `tests/interactive-map-controls.spec.ts` - Tests for map interactions and controls

## Available npm Scripts

### Regular Playwright Tests
```bash
# Run all tests
npm run test

# Run tests with UI mode
npm run test:ui

# Run tests with browser visible (headed mode)
npm run test:headed

# Debug tests step by step
npm run test:debug

# Run specific test suites
npm run test:vehicle-counts      # Vehicle count comparison tests
npm run test:interactive         # Interactive map control tests
```

### MCP Integration
```bash
# Start MCP server (for manual connection)
npm run mcp:start

# Run basic MCP test (navigate, screenshot, compare counts)
npm run mcp:test

# Run interactive test (click buttons, toggle controls)
npm run mcp:test:interactive

# Run monitoring test (continuous measurements)
npm run mcp:test:monitoring

# List available MCP tools
npm run mcp:tools
```

## MCP Tools Available

The Playwright MCP server provides these tools for Claude Code:

### Browser Management
- `launch_browser` - Start browser (chromium, firefox, webkit)
- `close_browser` - Close browser instance
- `navigate_to` - Go to URL
- `take_screenshot` - Capture page screenshot

### Element Interaction
- `click_element` - Click on elements using CSS selectors, text, or test IDs
- `fill_input` - Fill form inputs
- `get_element_text` - Extract text content
- `wait_for_element` - Wait for elements to appear

### RTD-Specific Tools
- `get_vehicle_count` - Get vehicle count from static or live map
- `compare_vehicle_counts` - Compare counts between both maps
- `evaluate_script` - Execute custom JavaScript

## Usage Examples

### Basic Vehicle Count Comparison
```bash
# Start your React app first
npm run start

# In another terminal, run the MCP test
npm run mcp:test
```

This will:
1. Launch a browser
2. Navigate to both static and live maps
3. Take screenshots
4. Compare vehicle counts
5. Report the results

### Interactive Testing
```bash
npm run mcp:test:interactive
```

This will:
1. Navigate between map views
2. Click navigation buttons
3. Toggle vehicle visibility controls  
4. Test map interactions

### Continuous Monitoring
```bash
npm run mcp:test:monitoring
```

This will:
1. Take multiple measurements over time
2. Monitor both maps for consistency
3. Report vehicle count variations

## Integration with Claude Code

To use these tools from Claude Code, you can:

1. **Start the MCP server** in a terminal:
   ```bash
   npm run mcp:start
   ```

2. **Connect Claude Code** to the MCP server using the stdio transport

3. **Use MCP tools** in your conversations:
   ```
   Please launch a browser and navigate to the static map, then compare vehicle counts with the live map
   ```

## Prerequisites

Make sure you have:
- ✅ React app running on `http://localhost:3000`
- ✅ RTD API server running on `http://localhost:8080`
- ✅ Playwright browsers installed (`npx playwright install`)

## Troubleshooting

### Common Issues

**Browser doesn't launch:**
```bash
# Reinstall Playwright browsers  
npx playwright install
```

**MCP connection fails:**
```bash
# Check if MCP SDK is installed
npm install --save-dev @modelcontextprotocol/sdk
```

**Tests timeout waiting for vehicle data:**
- Ensure the RTD API server is running
- Check that vehicle data is being served at `/api/vehicles`
- Increase timeout values in test files if needed

**Vehicle counts don't match:**
- This is expected due to different update intervals (static: 1s, live: 5s)
- Static map may have filters applied
- Real-time data changes between measurements

### Debug Mode

Run tests in debug mode to step through interactions:
```bash
npm run test:debug
```

This opens the Playwright Inspector where you can:
- Step through test actions
- Inspect page elements
- View console logs
- Modify selectors

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Claude Code   │◄──►│ MCP Playwright   │◄──►│ React Web App   │
│                 │    │ Server           │    │ (localhost:3000)│
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌──────────────────┐
                       │ Browser Instance │
                       │ (Chromium/etc)   │
                       └──────────────────┘
```

The MCP server acts as a bridge between Claude Code and the browser automation, enabling sophisticated testing and monitoring of the RTD transit application.