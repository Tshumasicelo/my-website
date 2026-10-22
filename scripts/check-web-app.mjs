// Sanity checks for the ASPECTS web app, run by CI before GitHub Pages serves it.
//   node scripts/check-web-app.mjs
//
// 1. index.html, sw.js and version.json agree on the version. If they drift,
//    installed copies either never hear about an update or are told about one
//    that never arrives.
// 2. Every script parses, so a typo can't ship a blank page.
// 3. The JSON files parse.
import { readFileSync } from "node:fs";
import vm from "node:vm";

const errors = [];
const read = f => readFileSync(new URL("../" + f, import.meta.url), "utf8");

const html = read("index.html");
const sw = read("sw.js");

let meta = null;
try { meta = JSON.parse(read("version.json")); } catch (e) { errors.push("version.json: " + e.message); }
try { JSON.parse(read("manifest.webmanifest")); } catch (e) { errors.push("manifest.webmanifest: " + e.message); }

const app = (html.match(/const APP_VERSION = "([^"]+)"/) || [])[1];
const cache = (sw.match(/const CACHE_VERSION = "v([^"]+)"/) || [])[1];
const live = meta && meta.version;

if (!app) errors.push("index.html: APP_VERSION not found");
if (!cache) errors.push('sw.js: CACHE_VERSION not found (expected "v<version>")');
if (meta && !/^\d+\.\d+\.\d+$/.test(String(live))) errors.push('version.json: "version" must look like 1.2.3');
if (app && cache && live && !(app === cache && app === live)) {
  errors.push(`versions differ: index.html ${app}, sw.js v${cache}, version.json ${live} — bump all three together`);
}

const inline = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]);
if (!inline.length) errors.push("index.html: no inline <script> found");
inline.forEach((code, i) => {
  try { new vm.Script(code, { filename: `index.html <script> #${i + 1}` }); }
  catch (e) { errors.push(`index.html script #${i + 1}: ${e.message}`); }
});
try { new vm.Script(sw, { filename: "sw.js" }); } catch (e) { errors.push("sw.js: " + e.message); }

if (errors.length) {
  errors.forEach(e => console.error("✗ " + e));
  process.exit(1);
}
console.log(`✓ ASPECTS ${app}: versions match, scripts parse, JSON is valid`);
