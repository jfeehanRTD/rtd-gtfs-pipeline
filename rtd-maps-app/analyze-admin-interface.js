#!/usr/bin/env node

const { chromium } = require('playwright');

async function analyzeAdminInterface() {
  console.log('🔍 Analyzing current admin interface at http://localhost:3000/admin...\n');
  
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
  const page = await context.newPage();
  
  try {
    // Navigate to admin page
    await page.goto('http://localhost:3002/admin', { waitUntil: 'networkidle' });
    
    // Wait for content to load
    await page.waitForTimeout(3000);
    
    // Take a screenshot
    await page.screenshot({ path: 'current-admin-interface.png', fullPage: true });
    console.log('📸 Screenshot saved: current-admin-interface.png');
    
    // Analyze the page structure
    console.log('\n📋 Current Admin Interface Analysis:');
    
    // Check for main sections
    const sections = await page.$$eval('[class*="bg-white"], [class*="border"], [class*="rounded"]', elements => {
      return elements.slice(0, 10).map(el => ({
        tag: el.tagName,
        classes: el.className,
        text: el.innerText?.substring(0, 100) || ''
      }));
    });
    
    console.log('\n🏗️ Main UI Sections Found:');
    sections.forEach((section, i) => {
      console.log(`${i + 1}. ${section.tag} - ${section.classes}`);
      if (section.text) {
        console.log(`   Text: ${section.text.replace(/\n/g, ' ')}`);
      }
    });
    
    // Check for navigation or tabs
    const navElements = await page.$$eval('[class*="tab"], [class*="nav"], [role="tab"]', elements => {
      return elements.map(el => el.innerText);
    }).catch(() => []);
    
    if (navElements.length > 0) {
      console.log('\n📍 Navigation Elements:');
      navElements.forEach((text, i) => console.log(`${i + 1}. ${text}`));
    }
    
    // Check for buttons
    const buttons = await page.$$eval('button', elements => {
      return elements.slice(0, 8).map(el => ({
        text: el.innerText,
        classes: el.className
      }));
    });
    
    console.log('\n🔘 Interactive Buttons:');
    buttons.forEach((btn, i) => {
      console.log(`${i + 1}. "${btn.text}" - ${btn.classes}`);
    });
    
    // Get page dimensions and scroll info
    const pageInfo = await page.evaluate(() => ({
      scrollHeight: document.body.scrollHeight,
      clientHeight: document.body.clientHeight,
      title: document.title
    }));
    
    console.log(`\n📏 Page Info:`);
    console.log(`   Title: ${pageInfo.title}`);
    console.log(`   Full Height: ${pageInfo.scrollHeight}px`);
    console.log(`   Viewport Height: ${pageInfo.clientHeight}px`);
    console.log(`   Is Scrollable: ${pageInfo.scrollHeight > pageInfo.clientHeight ? 'Yes' : 'No'}`);
    
    console.log('\n✅ Analysis complete! Check current-admin-interface.png for visual reference.');
    
  } catch (error) {
    console.error('❌ Error analyzing admin interface:', error.message);
  } finally {
    await browser.close();
  }
}

analyzeAdminInterface();