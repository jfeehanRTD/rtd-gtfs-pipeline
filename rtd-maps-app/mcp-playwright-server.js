#!/usr/bin/env node

/**
 * MCP Playwright Server
 * Provides Playwright automation capabilities through MCP protocol
 * Allows Claude Code to control the React web application
 */

const { Server } = require('@modelcontextprotocol/sdk/server/index.js');
const { StdioServerTransport } = require('@modelcontextprotocol/sdk/server/stdio.js');
const { CallToolRequestSchema, ListToolsRequestSchema } = require('@modelcontextprotocol/sdk/types.js');
const { chromium, firefox, webkit } = require('playwright');

class PlaywrightMCPServer {
  constructor() {
    this.server = new Server(
      {
        name: 'playwright-server',
        version: '1.0.0',
      },
      {
        capabilities: {
          tools: {},
        },
      }
    );
    
    this.browser = null;
    this.context = null;
    this.page = null;
    this.setupToolHandlers();
  }

  setupToolHandlers() {
    this.server.setRequestHandler(ListToolsRequestSchema, async () => {
      return {
        tools: [
          {
            name: 'launch_browser',
            description: 'Launch a browser instance (chromium, firefox, or webkit)',
            inputSchema: {
              type: 'object',
              properties: {
                browser: {
                  type: 'string',
                  enum: ['chromium', 'firefox', 'webkit'],
                  default: 'chromium',
                  description: 'Browser type to launch'
                },
                headless: {
                  type: 'boolean',
                  default: false,
                  description: 'Run in headless mode'
                }
              }
            }
          },
          {
            name: 'navigate_to',
            description: 'Navigate to a URL',
            inputSchema: {
              type: 'object',
              properties: {
                url: {
                  type: 'string',
                  description: 'URL to navigate to'
                }
              },
              required: ['url']
            }
          },
          {
            name: 'click_element',
            description: 'Click on an element using various selectors',
            inputSchema: {
              type: 'object',
              properties: {
                selector: {
                  type: 'string',
                  description: 'CSS selector, text, or data-testid'
                },
                selectorType: {
                  type: 'string',
                  enum: ['css', 'text', 'testid'],
                  default: 'css',
                  description: 'Type of selector to use'
                }
              },
              required: ['selector']
            }
          },
          {
            name: 'fill_input',
            description: 'Fill an input field',
            inputSchema: {
              type: 'object',
              properties: {
                selector: {
                  type: 'string',
                  description: 'CSS selector for the input field'
                },
                value: {
                  type: 'string',
                  description: 'Value to fill'
                }
              },
              required: ['selector', 'value']
            }
          },
          {
            name: 'get_element_text',
            description: 'Get text content of an element',
            inputSchema: {
              type: 'object',
              properties: {
                selector: {
                  type: 'string',
                  description: 'CSS selector for the element'
                }
              },
              required: ['selector']
            }
          },
          {
            name: 'wait_for_element',
            description: 'Wait for an element to appear',
            inputSchema: {
              type: 'object',
              properties: {
                selector: {
                  type: 'string',
                  description: 'CSS selector for the element'
                },
                timeout: {
                  type: 'number',
                  default: 5000,
                  description: 'Timeout in milliseconds'
                }
              },
              required: ['selector']
            }
          },
          {
            name: 'take_screenshot',
            description: 'Take a screenshot of the current page',
            inputSchema: {
              type: 'object',
              properties: {
                path: {
                  type: 'string',
                  description: 'Path to save the screenshot'
                }
              }
            }
          },
          {
            name: 'evaluate_script',
            description: 'Execute JavaScript in the page context',
            inputSchema: {
              type: 'object',
              properties: {
                script: {
                  type: 'string',
                  description: 'JavaScript code to execute'
                }
              },
              required: ['script']
            }
          },
          {
            name: 'get_vehicle_count',
            description: 'Get vehicle count from the RTD maps application',
            inputSchema: {
              type: 'object',
              properties: {
                mapType: {
                  type: 'string',
                  enum: ['static', 'live'],
                  description: 'Type of map to check (static or live)'
                }
              }
            }
          },
          {
            name: 'compare_vehicle_counts',
            description: 'Compare vehicle counts between static and live maps',
            inputSchema: {
              type: 'object',
              properties: {}
            }
          },
          {
            name: 'close_browser',
            description: 'Close the browser instance',
            inputSchema: {
              type: 'object',
              properties: {}
            }
          }
        ]
      };
    });

    this.server.setRequestHandler(CallToolRequestSchema, async (request) => {
      const { name, arguments: args } = request.params;

      try {
        switch (name) {
          case 'launch_browser':
            return await this.launchBrowser(args);
          case 'navigate_to':
            return await this.navigateTo(args);
          case 'click_element':
            return await this.clickElement(args);
          case 'fill_input':
            return await this.fillInput(args);
          case 'get_element_text':
            return await this.getElementText(args);
          case 'wait_for_element':
            return await this.waitForElement(args);
          case 'take_screenshot':
            return await this.takeScreenshot(args);
          case 'evaluate_script':
            return await this.evaluateScript(args);
          case 'get_vehicle_count':
            return await this.getVehicleCount(args);
          case 'compare_vehicle_counts':
            return await this.compareVehicleCounts(args);
          case 'close_browser':
            return await this.closeBrowser(args);
          default:
            throw new Error(`Unknown tool: ${name}`);
        }
      } catch (error) {
        return {
          content: [
            {
              type: 'text',
              text: `Error: ${error.message}`
            }
          ],
          isError: true
        };
      }
    });
  }

  async launchBrowser(args) {
    const { browser: browserType = 'chromium', headless = false } = args;
    
    let browserInstance;
    switch (browserType) {
      case 'firefox':
        browserInstance = firefox;
        break;
      case 'webkit':
        browserInstance = webkit;
        break;
      default:
        browserInstance = chromium;
    }

    this.browser = await browserInstance.launch({ 
      headless,
      devtools: !headless 
    });
    this.context = await this.browser.newContext({
      viewport: { width: 1920, height: 1080 }
    });
    this.page = await this.context.newPage();

    return {
      content: [
        {
          type: 'text',
          text: `✅ Launched ${browserType} browser (headless: ${headless})`
        }
      ]
    };
  }

  async navigateTo(args) {
    if (!this.page) throw new Error('Browser not launched');
    
    const { url } = args;
    await this.page.goto(url, { waitUntil: 'networkidle' });
    
    return {
      content: [
        {
          type: 'text',
          text: `✅ Navigated to: ${url}`
        }
      ]
    };
  }

  async clickElement(args) {
    if (!this.page) throw new Error('Browser not launched');
    
    const { selector, selectorType = 'css' } = args;
    
    let element;
    switch (selectorType) {
      case 'text':
        element = this.page.getByText(selector);
        break;
      case 'testid':
        element = this.page.getByTestId(selector);
        break;
      default:
        element = this.page.locator(selector);
    }
    
    await element.click();
    
    return {
      content: [
        {
          type: 'text',
          text: `✅ Clicked element: ${selector} (${selectorType})`
        }
      ]
    };
  }

  async fillInput(args) {
    if (!this.page) throw new Error('Browser not launched');
    
    const { selector, value } = args;
    await this.page.fill(selector, value);
    
    return {
      content: [
        {
          type: 'text',
          text: `✅ Filled input ${selector} with: ${value}`
        }
      ]
    };
  }

  async getElementText(args) {
    if (!this.page) throw new Error('Browser not launched');
    
    const { selector } = args;
    const text = await this.page.textContent(selector);
    
    return {
      content: [
        {
          type: 'text',
          text: `📄 Text content: "${text}"`
        }
      ]
    };
  }

  async waitForElement(args) {
    if (!this.page) throw new Error('Browser not launched');
    
    const { selector, timeout = 5000 } = args;
    await this.page.waitForSelector(selector, { timeout });
    
    return {
      content: [
        {
          type: 'text',
          text: `✅ Element appeared: ${selector}`
        }
      ]
    };
  }

  async takeScreenshot(args) {
    if (!this.page) throw new Error('Browser not launched');
    
    const { path = `screenshot-${Date.now()}.png` } = args;
    await this.page.screenshot({ path, fullPage: true });
    
    return {
      content: [
        {
          type: 'text',
          text: `📸 Screenshot saved: ${path}`
        }
      ]
    };
  }

  async evaluateScript(args) {
    if (!this.page) throw new Error('Browser not launched');
    
    const { script } = args;
    const result = await this.page.evaluate(script);
    
    return {
      content: [
        {
          type: 'text',
          text: `🔧 Script result: ${JSON.stringify(result, null, 2)}`
        }
      ]
    };
  }

  async getVehicleCount(args) {
    if (!this.page) throw new Error('Browser not launched');
    
    const { mapType } = args;
    let count = 0;
    let description = '';

    if (mapType === 'static') {
      // For static map, get count from the status bar
      try {
        const statusText = await this.page.textContent('.text-sm.text-gray-600:has-text("vehicles")');
        const match = statusText.match(/(\d+)\s*\/\s*(\d+)\s*vehicles/);
        if (match) {
          count = parseInt(match[1]); // Filtered count
          description = `Static map showing ${match[1]} out of ${match[2]} vehicles`;
        }
      } catch (e) {
        description = 'Could not find vehicle count on static map';
      }
    } else if (mapType === 'live') {
      // For live map, get bus and train counts
      try {
        const busText = await this.page.textContent('text=Buses');
        const trainText = await this.page.textContent('text=Trains');
        
        const busMatch = busText.match(/Buses \((\d+)\)/);
        const trainMatch = trainText.match(/Trains \((\d+)\)/);
        
        const busCount = busMatch ? parseInt(busMatch[1]) : 0;
        const trainCount = trainMatch ? parseInt(trainMatch[1]) : 0;
        
        count = busCount + trainCount;
        description = `Live map showing ${busCount} buses + ${trainCount} trains = ${count} total vehicles`;
      } catch (e) {
        description = 'Could not find vehicle count on live map';
      }
    }

    return {
      content: [
        {
          type: 'text',
          text: `🚌 Vehicle Count: ${count}\n📊 ${description}`
        }
      ]
    };
  }

  async compareVehicleCounts(args) {
    if (!this.page) throw new Error('Browser not launched');
    
    // Navigate to static map and get count
    await this.page.goto('http://localhost:3000/', { waitUntil: 'networkidle' });
    await this.page.waitForTimeout(2000); // Wait for data to load
    
    let staticCount = 0;
    let staticDesc = '';
    try {
      const statusText = await this.page.textContent('.text-sm.text-gray-600:has-text("vehicles")');
      const match = statusText.match(/(\d+)\s*\/\s*(\d+)\s*vehicles/);
      if (match) {
        staticCount = parseInt(match[1]);
        staticDesc = `${match[1]}/${match[2]} vehicles`;
      }
    } catch (e) {
      staticDesc = 'Could not read static map count';
    }

    // Navigate to live map and get count
    await this.page.goto('http://localhost:3000/live', { waitUntil: 'networkidle' });
    await this.page.waitForTimeout(2000); // Wait for data to load
    
    let liveCount = 0;
    let liveDesc = '';
    try {
      const busText = await this.page.textContent('text=Buses');
      const trainText = await this.page.textContent('text=Trains');
      
      const busMatch = busText.match(/Buses \((\d+)\)/);
      const trainMatch = trainText.match(/Trains \((\d+)\)/);
      
      const busCount = busMatch ? parseInt(busMatch[1]) : 0;
      const trainCount = trainMatch ? parseInt(trainMatch[1]) : 0;
      
      liveCount = busCount + trainCount;
      liveDesc = `${busCount} buses + ${trainCount} trains = ${liveCount} total`;
    } catch (e) {
      liveDesc = 'Could not read live map count';
    }

    const difference = Math.abs(staticCount - liveCount);
    
    return {
      content: [
        {
          type: 'text',
          text: `🔍 Vehicle Count Comparison:
📊 Static Map: ${staticDesc}
🚊 Live Map: ${liveDesc}
📈 Difference: ${difference} vehicles
${difference === 0 ? '✅ Counts match!' : '⚠️ Counts differ'}`
        }
      ]
    };
  }

  async closeBrowser(args) {
    if (this.browser) {
      await this.browser.close();
      this.browser = null;
      this.context = null;
      this.page = null;
    }
    
    return {
      content: [
        {
          type: 'text',
          text: '✅ Browser closed'
        }
      ]
    };
  }
}

async function main() {
  const server = new PlaywrightMCPServer();
  const transport = new StdioServerTransport();
  await server.server.connect(transport);
  
  // Graceful shutdown
  process.on('SIGINT', async () => {
    await server.closeBrowser();
    process.exit(0);
  });
}

if (require.main === module) {
  main().catch(console.error);
}

module.exports = { PlaywrightMCPServer };