# Changelog

All notable changes to Preset Pony will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.1] — 2026-08-15

### Improved
- About tab: fixed text wrapping and layout, added GitHub repository URL
- Folder locations now cached for Preset Explorer and Preset Backup/CSV Export (in addition to Pedalboards)
- Simple warnings for loading presets for V1 Mustangs to a V2 Mustang amp
- Clear preferences button now resets all cached folder locations

## [1.2.0] — 2026-08-13

### Added
- Preset Explorer tab to browse through a folder of .fuse files and apply them to the amp in one click
- Warning bypass checkbox in Preset Explorer to load presets from other Mustang models (still validates file structure)

### Improved
- Tab descriptions: added contextual hint labels to Toybox, Pedalboard Sets, and Preset Explorer tabs
- Toybox hint text now wraps dynamically to fit window width

## [1.1.1] — 2026-08-09

### Added
- Version shown in about tab
- Build process updates

## [1.1.0] — 2026-08-09

### Added
- Toybox tab to get a random sound
- Pedalboard sets tab
- Cache wiper for folder locations
- Disconnect from amp option


## [1.0.1] — 2026-08-03

Sources release

### Fixed
- Non-working cabinets removed from choice list. 
- Test updates
- Documentation updates

## [1.0.0] — 2026-08-03

### Added
- Initial public release
- USB HID connection to Fender Mustang III v2 amplifiers
- Live control of amp parameters (Gain, Volume, Bass, Middle, Treble, Presence/Cut, Master Volume, Noise Gate, Threshold, Depth, Sag, Bias, USB Gain)
- Live control of effect chains (4 effect slots per preset, 15+ effect types with knob controls)
- Preset management: read/write all 100 stored presets from amp
- Import/export presets from `.fuse` XML format (Fender Fuse compatible)
- Bulk backup of all 100 presets to ZIP file
- Export all presets to CSV
- Amp model selection (17 amp models: Fender, Marshall, Vox, Mesa/Boogie, Peavey, Orange, HiWatt)
- Cabinet selection (8 cabinet types)
- About tab with app version, disclaimer, and attribution
- Configurable effect knob display scales (delay time in ms, rate in Hz, etc.)
- Per-amp factory default values reference (`amp-facts.properties`)
- Support for dynamic Gain 2 relabeling (Blend on Bassman/British '70s)
- Studio Preamp Sag/Bias controls disabled to match amp hardware
- Standalone JAR build scripts (`.sh` / `.bat`)
- Native app image builds via jpackage (`.exe` on Windows, app-image on Linux)
- Command-line tools for preset import/export and diagnostics

### Fixed
- Connect/Refresh button double-click re-entrancy guard (prevents race conditions on USB)
- USB device handle leak on failed or repeated Connect (now properly closed before reconnect)
- Missing window-close cleanup (USB device now released on app exit)
- Threshold slider default mismatch (changed from 2 to 0 to match Fuse's factory defaults)
- Master Volume, Gain 2, and Depth control scaling (raw byte → user-facing display values)

### Known Limitations
- No live panel sync (Amp/Effects only update on Connect/Refresh)
- Single connected amp at a time
- Mustang (I to V) v1 amps not supported/tested
- One unidentified packet type (`DSP=0x0a`) on the wire, function unknown
- Effect parameter metadata not yet cross-verified against refuse repo

---

## Unreleased

### Planned
- Maven/Gradle build system migration
- Enhanced UI: live panel sync, effect chain visualization, preset search/favorites
- Android app (pending hid4java Android support)
- Detailed Fuse XML format documentation
- Protocol reference documentation for extending support to other Mustang amps/models