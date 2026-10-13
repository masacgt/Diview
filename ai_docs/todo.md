# Todo

## Completed

- [x] Publish the J-Quants adjusted daily quote subset through the Worker API. Evidence: `src/index.js` streams the selected R2 object; production returned HEAD 200, ranged GET 206, full GET 200 with 93,355,497 bytes, and existing health/search-catalog routes returned 200.

## Next

- [ ] Complete and publish the yfinance v2 R2 migration. Local changes add a J-Quants/XTKS daily sync, v2-first Worker APIs, static end-of-day quotes, and per-device 12-hour refresh suppression. Python and Worker syntax checks and the JDK 17 Android `assembleDebug` build passed; production publication, controlled R2 read-back migration, and connected-device verification remain open.
- [ ] Refresh the static snapshot only when a newer J-Quants date range is requested and the distribution permission continues to cover it.
