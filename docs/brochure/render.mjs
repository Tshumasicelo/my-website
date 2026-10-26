// Renders deck.html to ASPECTS-Product-Overview.pdf (1280x720 slides).
//   node docs/brochure/render.mjs
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
const playwright = createRequire(path.resolve(here, "../../tests/e2e/package.json"))("playwright");
const out = process.argv[2] || path.join(here, "ASPECTS-Product-Overview.pdf");
const browser = await playwright.chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
await page.goto("file://" + path.join(here, "deck.html"), { waitUntil: "load" });
await page.evaluate(() => document.fonts.ready);
await page.pdf({ path: out, width: "1280px", height: "720px", printBackground: true, preferCSSPageSize: true });
await browser.close();
console.log("wrote", out);
