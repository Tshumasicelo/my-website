/* ============================================================
   ASPECTS service worker
   Bump CACHE_VERSION on every deploy — it is what tells an
   already-installed copy that a new build exists.
   ============================================================ */
"use strict";

const CACHE_VERSION = "v1.0.0";
const CORE_CACHE    = "aspects-core-" + CACHE_VERSION;
const RUNTIME_CACHE = "aspects-runtime-" + CACHE_VERSION;

/* Paths are relative to the worker's scope, so this works unchanged
   whether the app is served from a domain root or from /my-website/. */
const CORE = [
  "./",
  "./index.html",
  "./manifest.webmanifest",
  "./icons/icon-192.png",
  "./icons/icon-512.png",
  "./icons/icon-maskable-512.png",
  "./icons/apple-touch-icon.png",
  "./icons/favicon.svg"
];

/* Third-party assets. Best-effort: a CDN hiccup must never fail the
   install, or the app would have no offline copy at all. */
/* A request that hangs is worse than one that fails: it stalls the page with no
   error to react to. Never wait on the network longer than this. */
const NET_TIMEOUT_MS = 6000;

function fetchWithTimeout(request, ms = NET_TIMEOUT_MS) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("network timeout")), ms);
    fetch(request).then(
      res => { clearTimeout(timer); resolve(res); },
      err => { clearTimeout(timer); reject(err); }
    );
  });
}

const EXTERNAL = [
  "https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.js"
];

self.addEventListener("install", event => {
  event.waitUntil((async () => {
    const cache = await caches.open(CORE_CACHE);
    await cache.addAll(CORE);
    await Promise.allSettled(EXTERNAL.map(async url => {
      const res = await fetchWithTimeout(new Request(url, { mode: "cors" }));
      if (res && res.ok) await cache.put(url, res);
    }));
  })());
});

self.addEventListener("activate", event => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(
      keys.filter(k => k.startsWith("aspects-") && k !== CORE_CACHE && k !== RUNTIME_CACHE)
          .map(k => caches.delete(k))
    );
    if (self.registration.navigationPreload) await self.registration.navigationPreload.enable();
    await self.clients.claim();
  })());
});

/* The page asks for this when the user accepts an update. Without it a
   waiting worker sits idle until every tab is closed. */
self.addEventListener("message", event => {
  if (event.data === "SKIP_WAITING") self.skipWaiting();
});

self.addEventListener("fetch", event => {
  const req = event.request;
  if (req.method !== "GET") return;

  const url = new URL(req.url);
  if (url.protocol !== "http:" && url.protocol !== "https:") return;

  /* Navigation: network-first, so a fresh deploy is picked up immediately;
     fall back to the cached shell when offline. ignoreSearch matters because
     the manifest shortcuts navigate to ./?go=expense and friends. */
  if (req.mode === "navigate") {
    event.respondWith((async () => {
      try {
        const preload = await event.preloadResponse;
        const fresh = preload || await fetchWithTimeout(req);
        const cache = await caches.open(CORE_CACHE);
        cache.put("./index.html", fresh.clone());
        return fresh;
      } catch (_) {
        const cached = await caches.match("./index.html", { ignoreSearch: true });
        return cached || new Response(
          "<h1>Offline</h1><p>Open ASPECTS once while connected to make it available offline.</p>",
          { status: 503, headers: { "Content-Type": "text/html; charset=utf-8" } }
        );
      }
    })());
    return;
  }

  /* Everything else: serve from cache immediately, refresh in the background
     so the next load gets the newer copy without ever blocking on the network. */
  event.respondWith((async () => {
    const cached = await caches.match(req);

    const fromNetwork = fetchWithTimeout(req).then(res => {
      if (res && (res.ok || res.type === "opaque")) {
        const copy = res.clone();
        caches.open(RUNTIME_CACHE).then(c => c.put(req, copy)).catch(() => {});
      }
      return res;
    }).catch(() => null);

    if (cached) { fromNetwork.catch(() => {}); return cached; }
    return (await fromNetwork) || new Response("", { status: 504, statusText: "Offline" });
  })());
});
