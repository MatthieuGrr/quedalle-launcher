// Renders the F-Droid feature graphic (1024x500) for each locale from banner.html.
// Usage: node tools/feature-graphic/render.js   (needs Playwright with Chromium)
const path = require('path');
const { chromium } = require('playwright');

const locales = { en: 'en-US', fr: 'fr-FR' };

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1024, height: 500 }, deviceScaleFactor: 1 });
  for (const [lang, locale] of Object.entries(locales)) {
    await page.goto('file://' + path.join(__dirname, 'banner.html') + '?lang=' + lang);
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({
      path: path.join(__dirname, '../../fastlane/metadata/android', locale, 'images/featureGraphic.png'),
    });
  }
  await browser.close();
})();
