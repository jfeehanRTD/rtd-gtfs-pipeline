#!/usr/bin/env node

/**
 * Example MCP Client for Playwright Server
 * Shows how to use the Playwright MCP server to control the React app
 */

const { spawn } = require('child_process');
const { Client } = require('@modelcontextprotocol/sdk/client/index.js');
const { StdioClientTransport } = require('@modelcontextprotocol/sdk/client/stdio.js');

class PlaywrightMCPClient {
  constructor() {
    this.client = null;
    this.transport = null;
    this.serverProcess = null;
  }

  async connect() {
    console.log('🚀 Starting Playwright MCP Server...');
    
    // Start the MCP server process
    this.serverProcess = spawn('node', ['mcp-playwright-server.js'], {
      stdio: ['pipe', 'pipe', 'inherit']
    });

    // Create client and transport
    this.transport = new StdioClientTransport({
      stdin: this.serverProcess.stdin,
      stdout: this.serverProcess.stdout
    });

    this.client = new Client({
      name: 'playwright-client',
      version: '1.0.0'
    }, {
      capabilities: {}
    });

    await this.client.connect(this.transport);
    console.log('✅ Connected to Playwright MCP Server');
  }

  async disconnect() {
    if (this.client) {
      await this.client.close();
    }
    if (this.serverProcess) {
      this.serverProcess.kill();
    }
    console.log('🛑 Disconnected from Playwright MCP Server');
  }

  async callTool(name, args = {}) {
    if (!this.client) {
      throw new Error('Client not connected');
    }

    console.log(`🔧 Calling tool: ${name}`, args);
    
    try {
      const response = await this.client.request({
        method: 'tools/call',
        params: {
          name,
          arguments: args
        }
      });

      if (response.content && response.content[0]) {
        const result = response.content[0].text;
        console.log(`✅ ${result}`);
        return result;
      }
      
      return response;
    } catch (error) {
      console.error(`❌ Tool call failed: ${error.message}`);
      throw error;
    }
  }

  async listTools() {
    const response = await this.client.request({
      method: 'tools/list'
    });
    return response.tools;
  }
}

// Example usage functions
async function basicMapTest() {
  const client = new PlaywrightMCPClient();
  
  try {
    await client.connect();
    
    console.log('\n🧪 Running Basic Map Test...\n');
    
    // Launch browser
    await client.callTool('launch_browser', { browser: 'chromium', headless: false });
    
    // Navigate to static map
    await client.callTool('navigate_to', { url: 'http://localhost:3000' });
    
    // Wait for map to load
    await client.callTool('wait_for_element', { selector: '.leaflet-container', timeout: 10000 });
    
    // Take a screenshot
    await client.callTool('take_screenshot', { path: 'static-map-test.png' });
    
    // Navigate to live map
    await client.callTool('navigate_to', { url: 'http://localhost:3000/live' });
    await client.callTool('wait_for_element', { selector: '.leaflet-container', timeout: 10000 });
    
    // Take another screenshot
    await client.callTool('take_screenshot', { path: 'live-map-test.png' });
    
    console.log('\n📊 Getting vehicle counts...\n');
    
    // Compare vehicle counts
    await client.callTool('compare_vehicle_counts');
    
    // Close browser
    await client.callTool('close_browser');
    
  } finally {
    await client.disconnect();
  }
}

async function interactiveMapTest() {
  const client = new PlaywrightMCPClient();
  
  try {
    await client.connect();
    
    console.log('\n🎮 Running Interactive Map Test...\n');
    
    // Launch browser
    await client.callTool('launch_browser', { browser: 'chromium', headless: false });
    
    // Navigate to static map
    await client.callTool('navigate_to', { url: 'http://localhost:3000' });
    await client.callTool('wait_for_element', { selector: '.leaflet-container', timeout: 10000 });
    
    // Click navigation buttons
    await client.callTool('click_element', { selector: 'text=Live Transit', selectorType: 'text' });
    
    // Wait for live map to load
    await client.callTool('wait_for_element', { selector: 'text=Show Vehicles', timeout: 5000 });
    
    // Toggle bus visibility
    const busCheckbox = 'input[type="checkbox"]';
    await client.callTool('click_element', { selector: busCheckbox });
    
    // Wait a moment
    await new Promise(resolve => setTimeout(resolve, 2000));
    
    // Toggle it back
    await client.callTool('click_element', { selector: busCheckbox });
    
    // Navigate to admin panel
    await client.callTool('click_element', { selector: 'text=Admin', selectorType: 'text' });
    
    // Navigate back to static map
    await client.callTool('click_element', { selector: 'text=Static Map', selectorType: 'text' });
    
    // Take final screenshot
    await client.callTool('take_screenshot', { path: 'interactive-test-final.png' });
    
    await client.callTool('close_browser');
    
  } finally {
    await client.disconnect();
  }
}

async function continuousMonitoringTest() {
  const client = new PlaywrightMCPClient();
  
  try {
    await client.connect();
    
    console.log('\n📈 Running Continuous Monitoring Test...\n');
    
    await client.callTool('launch_browser', { browser: 'chromium', headless: false });
    
    // Monitor both maps over time
    for (let i = 0; i < 3; i++) {
      console.log(`\n🔄 Measurement ${i + 1}/3:\n`);
      
      // Check static map
      await client.callTool('navigate_to', { url: 'http://localhost:3000' });
      await client.callTool('wait_for_element', { selector: '.leaflet-container' });
      
      // Wait for data to stabilize
      await new Promise(resolve => setTimeout(resolve, 5000));
      
      await client.callTool('get_vehicle_count', { mapType: 'static' });
      
      // Check live map
      await client.callTool('navigate_to', { url: 'http://localhost:3000/live' });
      await client.callTool('wait_for_element', { selector: '.leaflet-container' });
      
      // Wait for data to stabilize
      await new Promise(resolve => setTimeout(resolve, 5000));
      
      await client.callTool('get_vehicle_count', { mapType: 'live' });
      
      // Wait before next measurement
      if (i < 2) {
        console.log('⏱️  Waiting 10 seconds before next measurement...');
        await new Promise(resolve => setTimeout(resolve, 10000));
      }
    }
    
    await client.callTool('close_browser');
    
  } finally {
    await client.disconnect();
  }
}

// Main execution
async function main() {
  const testType = process.argv[2] || 'basic';
  
  console.log(`🎯 Running ${testType} test...\n`);
  
  switch (testType) {
    case 'basic':
      await basicMapTest();
      break;
    case 'interactive':
      await interactiveMapTest();
      break;
    case 'monitoring':
      await continuousMonitoringTest();
      break;
    case 'tools':
      // Just list available tools
      const client = new PlaywrightMCPClient();
      await client.connect();
      const tools = await client.listTools();
      console.log('🔧 Available tools:');
      tools.forEach(tool => {
        console.log(`  - ${tool.name}: ${tool.description}`);
      });
      await client.disconnect();
      break;
    default:
      console.log(`❌ Unknown test type: ${testType}`);
      console.log('Available tests: basic, interactive, monitoring, tools');
      process.exit(1);
  }
  
  console.log('\n✅ Test completed!');
}

// Handle graceful shutdown
process.on('SIGINT', async () => {
  console.log('\n🛑 Shutting down...');
  process.exit(0);
});

if (require.main === module) {
  main().catch(console.error);
}