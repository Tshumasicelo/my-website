// Static server that caches like GitHub Pages (max-age=600) and logs requests.
import http from "node:http";
import { readFile } from "node:fs/promises";
import path from "node:path";

const root = process.argv[2];
const port = Number(process.argv[3] || 8765);
const types = { ".html": "text/html; charset=utf-8", ".js": "text/javascript", ".json": "application/json",
                ".webmanifest": "application/manifest+json", ".png": "image/png", ".svg": "image/svg+xml" };

http.createServer(async (req, res) => {
  let p = decodeURIComponent(new URL(req.url, "http://x").pathname);
  if (p.endsWith("/")) p += "index.html";
  try {
    const body = await readFile(path.join(root, p));
    res.writeHead(200, { "Content-Type": types[path.extname(p)] || "application/octet-stream", "Cache-Control": "max-age=600" });
    res.end(body);
  } catch {
    res.writeHead(404, { "Content-Type": "text/plain" });
    res.end("not found");
  }
}).listen(port, () => console.log("serving", root, "on", port));
