# BRouter (vendored)

Offline routing engine, copied unmodified from <https://github.com/abrensch/brouter>
(commit `fed8f185ecdba4c01f4d6913bff7c0fae4576a32`, 2026-11-06), license: MIT (see `LICENSE`).

Included modules (sources only, `src/main/java`): `brouter-util`, `brouter-codec`,
`brouter-expressions`, `brouter-mapaccess`, `brouter-core`. Not included: the Android app,
the map creator, the server, tests.

Also taken from the same commit and shipped as app assets in `mobile/src/main/assets/brouter/`:
`lookups.dat` and `hiking-mountain.brf` (from `misc/profiles2`).

**`lookups.dat` must match the routing data (`.rd5`) version.** The segments downloaded from
brouter.de are built with the current upstream `lookups.dat`; when updating this copy, update the
sources and the two asset files together from the same upstream commit.
