# FamilyPhotoFrame — Web Server UI Modernisation

**Document ID:** FPF-FEAT-WEBUI-001  
**Version:** 1.1  
**Status:** Implementation-ready  
**Target baseline:** FamilyPhotoFrame v50.5 or later  
**Primary hardware target:** Huawei PLK-L01, Android 6 / API 23  
**Primary browser targets:** Current Chrome, Firefox, Edge, and Safari  
**Primary network context:** Local LAN web interface hosted by the photo frame

---

## 1. Objective

Redesign the built-in FamilyPhotoFrame web interface into a modern, responsive control panel with:

- tab-based navigation;
- grouped settings;
- a live slideshow preview;
- playback controls;
- responsive desktop, tablet, and mobile layouts;
- diagnostics, backup, and maintenance tools;
- reliable Android/web settings synchronisation;
- low CPU, memory, and network overhead;
- no disruption to slideshow playback.

The implementation is divided into five delivery phases. Every required feature in this specification is assigned to one phase.

---

## 2. Current problems

The existing web interface has accumulated features on a long page. This causes:

- excessive scrolling;
- weak separation between status, settings, diagnostics, and maintenance;
- poor mobile usability;
- inconsistent controls and validation;
- no visual confirmation of display, collage, or transition changes;
- unclear save and error states;
- difficult diagnostics navigation;
- increased risk of removing or duplicating settings during future changes.

---

## 3. Design principles

1. **Status first**  
   The user must immediately see whether the frame is online, playing, paused, indexing, reconnecting, or in error.

2. **One task per tab**  
   Related controls belong together. No flat page containing unrelated settings.

3. **Preview without side effects**  
   Preview generation must not alter slideshow history, trigger scans, or repeatedly decode full-resolution files.

4. **Immediate feedback**  
   Every setting change shows Saving, Saved, Rejected, Offline, or Conflict.

5. **Responsive by default**  
   All essential features must work from a phone without horizontal scrolling.

6. **Safe destructive actions**  
   Clear cache, reset, import, restart, and reindex actions require explicit confirmation.

7. **Progressive disclosure**  
   Advanced controls remain available but are grouped and collapsed where appropriate.

8. **Low resource usage**  
   The web UI must not degrade slideshow rendering on Android 6 hardware.

---

## 4. Final information architecture

The finished interface shall contain these top-level tabs:

1. Overview
2. Photos
3. Playback
4. Display
5. Schedule
6. Device
7. Diagnostics
8. Backup
9. About

No individual setting may appear directly in the global navigation.

### 4.1 Desktop

For widths of approximately 900 px or greater:

- fixed left navigation rail;
- main settings/content area;
- optional right-side preview panel;
- persistent status header.

### 4.2 Tablet

For widths of approximately 600–899 px:

- collapsible navigation rail or top tabs;
- preview above content or in a collapsible panel;
- single-column grouped settings where necessary.

### 4.3 Mobile

Below approximately 600 px:

- compact app bar;
- navigation drawer or horizontally scrollable tab strip;
- full-width settings cards;
- no horizontal page scrolling;
- minimum touch target approximately 44 × 44 px.

---

# 5. Five-phase delivery plan

---

# Phase 1 — Foundation, navigation, and design system

## 5.1 Goal

Replace the flat-page shell with a responsive tab-based application structure without removing or changing existing functionality.

## 5.2 Features included

### A. Responsive application shell

Implement:

- desktop left navigation rail;
- tablet collapsed navigation;
- mobile drawer or compact tab strip;
- persistent global header;
- main content container;
- responsive breakpoints;
- stable content width;
- loading skeletons;
- empty states;
- offline and reconnecting banners.

### B. Top-level tabs

Create functional navigation for:

- Overview
- Photos
- Playback
- Display
- Schedule
- Device
- Diagnostics
- Backup
- About

At this phase, tabs may contain placeholders, but all navigation routes must work.

### C. Global header

Display:

- device name;
- connection status;
- playback status;
- current source;
- indexing status and progress;
- application version;
- saving/error indicator;
- reconnecting/offline state.

Header states:

```text
Online · Playing
Online · Paused
Online · Indexing 12,430 / 28,015
Saving…
Saved
Reconnecting…
Offline
Error
```

### D. Reusable component system

Create shared UI components or plain-JavaScript modules:

- `SettingsSection`
- `SettingsRow`
- `ToggleControl`
- `SelectControl`
- `SliderControl`
- `NumberControl`
- `TextControl`
- `PasswordControl`
- `ActionButton`
- `StatusBadge`
- `InlineValidation`
- `ConfirmationDialog`
- `ToastNotification`
- `LoadingSkeleton`
- `EmptyState`
- `CollapsibleAdvancedSection`

### E. Common visual design

Implement:

- restrained neutral theme;
- one accent colour;
- consistent spacing;
- typography hierarchy;
- card styles;
- button variants;
- danger-action styling;
- disabled states;
- focus states;
- status colours that also include icons or text.

### F. Basic accessibility foundation

Implement:

- semantic HTML;
- labels associated with inputs;
- visible keyboard focus;
- keyboard-operable navigation;
- ARIA live region for connection and save status;
- colour contrast suitable for normal use;
- no colour-only errors.

### G. Compatibility wrapper

Preserve all current web actions during migration.

Requirements:

- existing endpoints remain functional;
- old settings page may remain temporarily behind a development route;
- no setting is removed;
- no write operation changes behaviour in Phase 1.

## 5.3 Deliverables

- new responsive page shell;
- working tab navigation;
- global status header;
- reusable UI component library;
- placeholder content for all tabs;
- compatibility adapter for current APIs;
- mobile and desktop layouts.

## 5.4 Phase 1 review checklist

- Are all existing settings and actions inventoried?
- Does every top-level tab open correctly?
- Does browser refresh preserve the selected tab?
- Does mobile navigation work without horizontal scrolling?
- Does the global header remain stable during polling?
- Are all existing write actions still reachable?
- Are components reused rather than duplicated?

## 5.5 Gate 1 acceptance criteria

- All nine tabs are reachable.
- Desktop, tablet, and mobile navigation work.
- No current web feature is removed.
- Existing API actions remain functional.
- Keyboard navigation reaches every tab.
- No critical browser-console errors occur.
- The slideshow remains unaffected while the new shell is open.

---

# Phase 2 — Grouped settings and synchronised configuration

## 6.1 Goal

Move every existing setting into one logical tab and introduce consistent validation, save state, conflict handling, and Android/web synchronisation.

## 6.2 Features included

### A. Photos tab

#### Source group

- source type;
- SMB host;
- share;
- path;
- username;
- masked password state;
- connection test;
- source status;
- last successful connection;
- explicit Apply button for connection-affecting changes.

#### Folder selection group

- discovered-folder checkboxes;
- exact normalized folder path;
- photo count;
- search/filter;
- select all;
- clear all;
- play all folders;
- show selected only;
- preserve selected state after reload.

#### Indexing filters group

- file extensions;
- MIME types;
- default HEIC/HEIF entries;
- hidden-file handling;
- recursive scan;
- reset to defaults;
- advanced unsupported-format suppression settings.

#### Scan group

- Scan now;
- Cancel scan;
- last scan time;
- files found;
- supported count;
- unsupported count;
- duration;
- coalesced duplicate scan requests.

### B. Playback tab

#### Timing group

- interval slider;
- numeric value;
- −5 s button;
- +5 s button;
- minimum 3 s;
- maximum 600 s;
- synchronized controls;
- immediate save and runtime apply.

#### Playback order group

- shuffle;
- date ascending;
- date descending;
- filename order when supported;
- recent-photo repeat protection.

#### Portrait collage group

- Off;
- Automatic;
- Always use two;
- Prefer three;
- maximum collage size;
- fallback background;
- tile gap.

#### Transition group

- Fixed or Ambient Random;
- all ten transition effects;
- duration preset;
- custom duration;
- reduce motion;
- transition preview button placeholder, enabled in Phase 3.

### C. Display tab

#### Image fit group

- Fit;
- Fill;
- Crop;
- Smart/Automatic where supported.

#### Background group

- black;
- solid colour;
- blurred image;
- background intensity where supported.

#### Overlay group

- clock;
- date;
- weather;
- file information;
- folder name;
- overlay position;
- opacity;
- text size.

#### Screen group

- brightness;
- orientation lock;
- keep screen awake;
- fullscreen/immersive mode;
- burn-in mitigation where supported.

### D. Schedule tab

#### Daily schedule group

- enabled;
- wake time;
- sleep time;
- days of week;
- timezone;
- next scheduled action.

#### Quiet hours group

- dim display;
- pause playback;
- screen off;
- restore playback.

#### Temporary override group

- keep awake for 30 minutes;
- 1 hour;
- 2 hours;
- until manually disabled.

### E. Device tab

#### Identity group

- device name;
- installation ID;
- app version;
- Android version;
- uptime.

#### Network group

- IP;
- gateway;
- DNS;
- interface state;
- web port;
- pairing status.

#### Storage and memory group

- application storage;
- media cache size;
- free storage;
- Java heap;
- process PSS;
- image-cache size.

Maintenance controls remain placeholders until Phase 4.

### F. About tab

- application name;
- version;
- build type;
- build/commit identifier;
- licences;
- acknowledgements;
- compatibility information.

### G. Common settings update model

Safe setting flow:

```text
User changes setting
→ Saving…
→ API validates
→ value persists
→ runtime applies
→ normalized value returns
→ Saved
```

Use explicit Apply for:

- SMB credentials;
- source path;
- network/server changes;
- backup import;
- destructive actions.

### H. Settings revisions and stale-write protection

Request model:

```json
{
  "revision": 104,
  "settings": {
    "intervalSec": 18
  }
}
```

Conflict response:

```json
{
  "ok": false,
  "code": "REVISION_CONFLICT",
  "message": "Settings changed on another client.",
  "currentRevision": 105
}
```

### I. Structured validation

Example:

```json
{
  "ok": false,
  "code": "INVALID_INTERVAL",
  "field": "intervalSec",
  "message": "Interval must be between 3 and 600 seconds."
}
```

### J. API versioning foundation

Use:

```text
/api/v1/
```

Existing endpoints may remain as compatibility aliases.

Common success response:

```json
{
  "ok": true,
  "revision": 105,
  "data": {}
}
```

## 6.3 Deliverables

- every current setting assigned to one tab;
- grouped settings cards;
- common save and validation handling;
- settings revision support;
- synchronized Android and web values;
- old flat settings page removed from normal navigation.

## 6.4 Phase 2 review checklist

- Does each setting appear exactly once?
- Are current Android values reflected after page load?
- Does local Android setting change appear on the web?
- Are invalid values rejected next to the correct field?
- Are passwords never returned in plain text?
- Are legacy endpoint aliases still functional?
- Are folder selections identified by exact path?

## 6.5 Gate 2 acceptance criteria

- No long flat settings page remains.
- Every existing setting is present in one logical group.
- Reload preserves all values.
- Web changes apply without application restart where supported.
- Android-side changes are reflected on refresh.
- Interval controls remain synchronized and bounded.
- Stale writes return conflict responses.
- All numeric and enum values are validated server-side.

---

# Phase 3 — Live preview, playback controls, and Overview dashboard

## 7.1 Goal

Create a low-overhead live representation of the committed slideshow presentation and provide safe remote playback control.

## 7.2 Features included

### A. Overview dashboard

Display:

- live preview;
- playback controls;
- current presentation details;
- device summary;
- recent warnings;
- indexing status.

### B. Live preview pipeline

Preview must represent the committed prepared slide.

Requirements:

- supports single images;
- supports portrait fallback;
- supports two-photo collage;
- supports three-photo collage;
- preserves display aspect ratio;
- uses low-resolution output;
- does not modify slideshow history;
- does not trigger SMB rescans;
- does not repeatedly decode source images;
- serves one cached preview to all clients;
- allows only one preview-generation job at a time.

### C. Preview endpoints

```text
GET /api/v1/presentation/current
GET /api/v1/preview
```

Example presentation response:

```json
{
  "presentationId": 1042,
  "type": "collage_3",
  "photoIds": [201, 205, 209],
  "folder": "/Photos/2024/Italy",
  "transition": "soft_dissolve",
  "committedAt": "2026-07-28T09:22:31Z",
  "previewRevision": "1042-3"
}
```

### D. Conditional preview refresh

Initial implementation:

- poll current presentation every 2 seconds;
- use revision or ETag;
- download preview only when changed;
- stop or reduce polling when the browser tab is hidden;
- preserve preview dimensions during loading or reconnect.

### E. Preview states

- Loading
- Live
- Paused
- No photos
- Indexing
- Source offline
- Unsupported photo skipped
- Preview unavailable
- Reconnecting
- Offline

### F. Playback controls

Implement:

```text
POST /api/v1/playback/play
POST /api/v1/playback/pause
POST /api/v1/playback/next
POST /api/v1/playback/previous
POST /api/v1/playback/restart-interval
```

Controls:

- Previous
- Play/Pause
- Next
- Restart interval
- optional fullscreen preview

Repeated navigation requests must use the slideshow coordinator’s existing coalescing rules.

### G. Current presentation metadata

Show:

- filename;
- source;
- exact folder;
- capture date;
- presentation type;
- active transition;
- playback state;
- remaining interval where practical.

### H. Recent warnings

Show summary counts for:

- decode failures;
- unsupported HEIC/HEIF;
- weather failures;
- scan errors;
- low-performance transition mode.

Warnings link to Diagnostics.

### I. Transition preview

Enable the Playback-tab transition-preview button.

Requirements:

- use bundled low-resolution samples;
- no SMB access;
- no slideshow history changes;
- no modification to committed slideshow;
- run only when requested.

## 7.3 Deliverables

- Overview dashboard;
- committed-slide preview endpoint;
- cached preview generation;
- playback control APIs;
- preview metadata;
- transition preview;
- warning summary.

## 7.4 Phase 3 review checklist

- Does the preview match the committed slide?
- Can multiple clients reuse one preview?
- Is preview generation bounded?
- Does preview remain stable while next slide loads?
- Do playback buttons avoid duplicate navigation queues?
- Does browser refresh avoid downloading unchanged images?
- Does transition preview remain isolated from slideshow state?

## 7.5 Gate 3 acceptance criteria

- Preview supports single and collage presentations.
- Preview never changes slideshow history.
- Opening the page does not restart playback.
- No black or empty preview occurs during normal preload.
- Multiple clients do not create duplicate preview jobs.
- Previous, Play/Pause, Next, and Restart interval work reliably.
- Preview updates only after committed-presentation revision changes.
- Preview image normally remains below 300 KiB.

---

# Phase 4 — Diagnostics, backup, security, and maintenance

## 8.1 Goal

Complete the administrative functions of the web UI with safe diagnostics access, backup/restore, maintenance operations, and write-operation security.

## 8.2 Features included

### A. Diagnostics summary

Cards for:

- decode failures;
- unsupported files;
- transition fallbacks;
- slow transitions;
- weather failures;
- scan failures;
- web API errors;
- process restarts;
- low-memory events.

### B. Event viewer

Columns:

- timestamp;
- severity;
- category;
- event name;
- summary;
- expandable metadata.

Filters:

- severity;
- category;
- time range;
- text search.

### C. Diagnostics API

```text
GET /api/v1/diagnostics/events?after=<cursor>&limit=200
```

Requirements:

- cursor or page-based retrieval;
- default limit 200;
- capped browser rows;
- polling stops when tab is hidden;
- no complete-file loading into browser memory.

### D. Diagnostics export

- download JSONL;
- download summary report;
- copy filtered event details;
- include app and device metadata.

### E. Backup export

Options:

- settings only;
- settings and folder selections;
- diagnostics;
- complete supported backup.

### F. Backup import

Workflow:

1. choose file;
2. parse metadata;
3. validate version and schema;
4. show affected groups;
5. create rollback copy;
6. require confirmation;
7. apply;
8. report partial failures;
9. refresh settings revision.

### G. Maintenance controls

- restart application;
- restart web server;
- rescan;
- clear media cache;
- clear unsupported suppression;
- database maintenance;
- factory reset.

### H. Confirmation policy

Destructive actions require:

- explicit confirmation dialog;
- action description;
- affected data;
- cancel button;
- optional typed confirmation for factory reset.

### I. Pairing and write security

Requirements:

- paired/session-authorized writes;
- no credentials in URLs;
- no stored password returned;
- request-body size limits;
- server-side validation;
- escaped user strings;
- CSRF protection where applicable;
- secure cookie flags where cookies are used;
- rate limiting for sensitive endpoints.

### J. Error and reconnect handling

Temporary API failure:

- retain last known values;
- mark data stale;
- retry with bounded backoff;
- do not reset controls.

Frame restart:

- show reconnecting;
- refresh all values after connection returns;
- do not replay queued writes automatically.

Source offline:

- web UI remains available;
- source settings remain editable;
- last valid preview remains visible or shows offline state.

## 8.3 Deliverables

- diagnostics dashboard;
- paginated event viewer;
- diagnostics exports;
- backup export/import;
- maintenance tools;
- confirmation flows;
- paired write security;
- reconnect and stale-data handling.

## 8.4 Phase 4 review checklist

- Can a large diagnostics file be browsed without freezing?
- Are event rows bounded?
- Are all destructive actions confirmed?
- Are backups validated before apply?
- Is rollback available?
- Are credentials protected?
- Do write endpoints require authorization?
- Are malformed values rejected?

## 8.5 Gate 4 acceptance criteria

- Diagnostics pagination works.
- Large logs do not freeze the browser.
- Exports download correctly.
- Backup import validates before changing settings.
- Partial import failures are reported.
- Destructive actions cannot execute accidentally.
- Passwords and tokens are never exposed.
- Unauthorized write requests fail.
- Reconnection refreshes state without replaying stale writes.

---

# Phase 5 — Accessibility, performance, security review, and endurance validation

## 9.1 Goal

Harden the complete web UI for long-duration use on the target Android 6 frame and common desktop/mobile browsers.

## 9.2 Features included

### A. Responsive refinement

Validate and correct:

- desktop rail;
- tablet layout;
- mobile drawer;
- preview placement;
- card spacing;
- control wrapping;
- dialogs;
- event table;
- no horizontal page scrolling.

### B. Accessibility completion

Verify:

- semantic headings;
- associated labels;
- full keyboard navigation;
- visible focus;
- sufficient contrast;
- ARIA live regions;
- dialog focus trapping;
- Escape behaviour;
- screen-reader-friendly errors;
- no colour-only status;
- minimum touch targets.

### C. Browser reduced motion

Respect browser `prefers-reduced-motion` for web-page animation only.

This does not automatically change the frame’s slideshow transition setting.

### D. Performance optimisation

Requirements:

- initial HTML/CSS/JS below 500 KiB uncompressed where practical;
- avoid heavy frameworks unless justified;
- one preview-generation job;
- preview normally below 300 KiB;
- inactive-tab polling reduced or stopped;
- diagnostics rows capped;
- setting responses target below 500 ms on LAN;
- status polling no faster than 1 second;
- preview polling default 2 seconds.

### E. Slideshow isolation

Verify:

- preview activity does not increase slideshow dropped frames;
- browser clients do not trigger duplicate SMB downloads;
- browser clients do not create duplicate scans;
- preview generation does not retain full-resolution bitmaps;
- web UI does not alter slideshow order or history.

### F. Security review

Review:

- session/pairing enforcement;
- CSRF protection;
- input validation;
- path handling;
- HTML escaping;
- file-upload limits;
- backup parser;
- diagnostics export;
- sensitive values;
- endpoint rate limiting.

### G. 24-hour endurance test

Configuration:

```text
Web Overview open
Preview updating every 2 seconds
Slideshow interval 3 seconds
Mixed single and collage slides
Ambient Random transitions
Periodic diagnostics refresh
Intermittent LAN delay
At least two browser clients during part of the test
```

Capture:

- Java heap;
- native heap;
- process PSS;
- image-cache size;
- preview generation count;
- preview cache hits;
- API latency;
- failed requests;
- browser reconnect count;
- slideshow frame timing;
- scan duplication;
- process restarts;
- web-server errors.

### H. Final regression

Run all prior phase tests plus:

- Android setting changed from web;
- Android setting changed locally;
- frame restart;
- source offline/online recovery;
- preview single-to-collage;
- transition settings change;
- backup round trip;
- diagnostics export;
- simultaneous clients;
- mobile keyboard and touch navigation.

## 9.3 Deliverables

- responsive refinements;
- accessibility fixes;
- performance optimisations;
- security review results;
- endurance-test report;
- final regression report;
- production-ready web UI.

## 9.4 Phase 5 review checklist

- Does every core task work on a phone?
- Is the page keyboard navigable?
- Does a 24-hour session remain stable?
- Does preview polling stop when hidden?
- Is slideshow performance unchanged?
- Are API response times acceptable?
- Are all security findings resolved or documented?

## 9.5 Gate 5 acceptance criteria

- No web-UI memory leak during 24 hours.
- Slideshow remains visually smooth while preview is open.
- No duplicate preview-generation job occurs.
- No duplicate scan is triggered by multiple clients.
- Critical tasks work on phone, tablet, and desktop.
- Accessibility checklist passes.
- Security review has no unresolved critical or high-severity issue.
- API write validation and pairing enforcement pass.
- Application builds and installs successfully.

---

# 10. Feature-to-phase traceability matrix

| Feature | Phase |
|---|---:|
| Responsive app shell | 1 |
| Desktop navigation rail | 1 |
| Tablet navigation | 1 |
| Mobile drawer/tab strip | 1 |
| Global status header | 1 |
| Reusable UI components | 1 |
| Base accessibility structure | 1 |
| Existing API compatibility adapter | 1 |
| Photos grouped settings | 2 |
| Playback grouped settings | 2 |
| Display grouped settings | 2 |
| Schedule grouped settings | 2 |
| Device information groups | 2 |
| About tab | 2 |
| Save/Saved/Error states | 2 |
| Inline validation | 2 |
| Revision conflict handling | 2 |
| `/api/v1/` settings foundation | 2 |
| Live preview | 3 |
| Preview cache and ETag/revision | 3 |
| Current-presentation metadata | 3 |
| Playback controls | 3 |
| Overview dashboard | 3 |
| Recent warning summary | 3 |
| Transition preview | 3 |
| Diagnostics summary | 4 |
| Paginated event viewer | 4 |
| Diagnostics export | 4 |
| Backup export | 4 |
| Backup import | 4 |
| Rollback before import | 4 |
| Maintenance actions | 4 |
| Confirmation dialogs | 4 |
| Pairing/session write security | 4 |
| Reconnect and stale-data handling | 4 |
| Full mobile refinement | 5 |
| Full accessibility review | 5 |
| Browser reduced-motion handling | 5 |
| Performance optimisation | 5 |
| Security audit | 5 |
| Two-client validation | 5 |
| 24-hour endurance test | 5 |
| Final regression and release review | 5 |

---

# 11. Cross-phase engineering requirements

These requirements apply to every phase.

## 11.1 No feature loss

Every current web feature must be inventoried before implementation and mapped to the new UI.

## 11.2 No slideshow disruption

Opening or using the web UI must not:

- restart playback;
- reset interval timing;
- change slideshow history unless a playback command requests it;
- start duplicate scans;
- trigger unbounded SMB traffic;
- retain full-resolution preview images.

## 11.3 API response format

Success:

```json
{
  "ok": true,
  "revision": 105,
  "data": {}
}
```

Failure:

```json
{
  "ok": false,
  "code": "SOURCE_OFFLINE",
  "message": "The SMB source is not reachable.",
  "details": {}
}
```

## 11.4 Logging

Add structured events for:

```text
WEB_UI_CONNECTED
WEB_UI_DISCONNECTED
WEB_SETTINGS_UPDATED
WEB_SETTINGS_CONFLICT
WEB_PREVIEW_GENERATED
WEB_PREVIEW_CACHE_HIT
WEB_PREVIEW_FAILED
WEB_API_VALIDATION_FAILED
WEB_BACKUP_EXPORTED
WEB_BACKUP_IMPORTED
WEB_MAINTENANCE_ACTION
WEB_RECONNECT
```

Do not log passwords, session tokens, or complete credential-bearing URLs.

---

# 12. Test strategy by phase

## Phase 1 tests

- tab navigation;
- route persistence;
- mobile drawer;
- keyboard navigation;
- existing action compatibility;
- loading/offline/error shell states.

## Phase 2 tests

- every setting maps to one group;
- Android-to-web synchronisation;
- web-to-Android synchronisation;
- revision conflicts;
- validation;
- interval bounds;
- folder path identity;
- transition settings;
- schedule validation.

## Phase 3 tests

- preview unchanged revision;
- preview changed revision;
- single preview;
- collage preview;
- multiple clients;
- playback controls;
- transition preview;
- slow SMB preparation;
- source offline.

## Phase 4 tests

- diagnostics pagination;
- event filtering;
- export;
- backup validation;
- backup rollback;
- destructive confirmation;
- unauthorized writes;
- request-size limits;
- stale/reconnect handling.

## Phase 5 tests

- mobile/tablet/desktop regression;
- accessibility audit;
- security audit;
- performance profiling;
- two-client test;
- 24-hour endurance;
- final build and install.

---

# 13. Final acceptance criteria

The complete task is done when:

- all five phase gates pass;
- all current web features are preserved;
- all settings are grouped;
- no flat settings page remains;
- tabs work on phone, tablet, and desktop;
- live preview is stable and low overhead;
- playback controls work reliably;
- diagnostics and backup are complete;
- write operations are secured;
- accessibility and security reviews pass;
- the 24-hour endurance test passes;
- the application builds and installs with:

```bash
./gradlew clean installDebug
```

---

# 14. Recommended implementation order inside each phase

For each phase:

1. inventory affected existing code;
2. add or update automated tests;
3. implement API/data changes;
4. implement UI changes;
5. run static checks;
6. run unit tests;
7. run browser tests;
8. review for regressions;
9. fix all major findings;
10. pass the phase gate before moving forward.

---

# 15. Review verdict

This five-phase breakdown is implementation-ready.

No major feature remains unassigned. Dependencies are ordered correctly:

```text
Phase 1 shell
→ Phase 2 settings
→ Phase 3 preview
→ Phase 4 diagnostics/backup/security
→ Phase 5 hardening and endurance
```

The most critical technical risk remains the live-preview pipeline in Phase 3. It must reuse committed prepared-slide output and must not become a second independent image-loading pipeline.

The second major risk is preserving every existing web setting during Phase 2. A complete setting and endpoint inventory is mandatory before migration.
