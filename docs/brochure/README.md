# ASPECTS brochure

`ASPECTS-Product-Overview.pdf`: a 12-slide product overview of ASPECTS 1.2.0,
built from real screenshots of the app filled with made-up demo data.

| File | What it is |
|---|---|
| `deck.html` | The slides (1280×720 each), in the app's own style |
| `shots/` | App screenshots used by the slides |
| `fonts/` | Inter web fonts (SIL Open Font License, see `LICENSE-Inter-OFL.txt`) |
| `capture.mjs` | Retakes the screenshots with the demo data |
| `render.mjs` | Prints `deck.html` to the PDF |

## Rebuild

```sh
cd tests/e2e && npm ci && cd ../..      # Playwright, shared with the browser tests
node docs/brochure/capture.mjs          # optional: fresh screenshots
node docs/brochure/render.mjs           # writes ASPECTS-Product-Overview.pdf
```

Where jsDelivr is unreachable, point `CHART_JS` at a local copy of
`chart.js@4.4.1/dist/chart.umd.js` so the screenshots show real charts.
