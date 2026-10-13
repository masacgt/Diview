# Todo

## Completed

- [x] Publish the J-Quants adjusted daily quote subset through the Worker API. Evidence: `src/index.js` streams the selected R2 object; production returned HEAD 200, ranged GET 206, full GET 200 with 93,355,497 bytes, and existing health/search-catalog routes returned 200.

## Next

- [ ] Publish the monthly-price split correction from the deployed Worker baseline and refresh the Android monthly-price cache. Local code now keeps Yahoo Finance `Close` undivided and uses a versioned cache key; production still returns the old split-divided values until deployment and app installation.
- [ ] Verify fetched price and calculated yield in the top-right search modal on-device after the monthly-price Worker correction is published; the updated APK is installed, but the app was not launched while production still serves the incorrect monthly-price series.
- [ ] Audit and correct split-transition dividend history before claiming full dividend accuracy. Toyota FY2022 is 52.245 yen in the current data but 52.00 yen in Toyota's issuer report after its 5:1 split; check other transition-year records and the yfinance migration path.
- [ ] Complete and publish the yfinance v2 R2 migration. Local code now retains J-Quants product categories, routes dividend/monthly-price reads to v2, stores rights-date derivations under v2, and records per-device fetch attempts and failures. R2 publication/read-back and Worker production deployment remain open because this environment has no R2 credentials or local v2 dataset.
- [ ] Refresh the static snapshot only when a newer J-Quants date range is requested and the distribution permission continues to cover it.
