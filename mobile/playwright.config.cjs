const fs = require('node:fs')
const { defineConfig } = require('@playwright/test')
const edge = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
module.exports = defineConfig({
  testDir: './tests',
  testMatch: '**/*.spec.cjs',
  workers: 1,
  use: {
    baseURL: process.env.MOBILE_TEST_URL || 'http://127.0.0.1:3001/mobile/',
    viewport: { width: 390, height: 844 },
    launchOptions: { ...(fs.existsSync(edge) ? { executablePath: edge } : {}) },
    screenshot: 'only-on-failure'
  }
})
