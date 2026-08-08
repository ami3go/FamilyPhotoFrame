package com.example.familyphotoframe.web

/** Small HTML shell. CSS and JavaScript are served as separately cacheable resources. */
object SetupPage {
    val HTML: String = """
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
  <meta name="color-scheme" content="light dark">
  <title>FamilyPhotoFrame</title>
  <link rel="stylesheet" href="/assets/app-${WebUiAssets.REVISION}.css">
</head>
<body>
  <div id="pairPane" class="pair-shell">
    <form id="pairForm" class="pair-card" autocomplete="off">
      <div class="brand-mark" aria-hidden="true">FP</div>
      <h1>FamilyPhotoFrame</h1><span class="sr-only">Photo Frame setup</span>
      <p>Enter the pairing PIN shown on the frame.</p>
      <label for="pin">Pairing PIN</label>
      <input id="pin" inputmode="numeric" pattern="[0-9]*" maxlength="8" autocomplete="one-time-code">

      <div id="rememberFields" class="remember-options hidden">
        <label for="rememberMode">Remember this browser</label>
        <select id="rememberMode">
          <option value="SESSION_ONLY">Do not remember — this browser session only</option>
          <option value="ONE_HOUR">1 hour</option>
          <option value="ONE_DAY">1 day</option>
          <option value="ONE_WEEK">1 week</option>
          <option value="ONE_MONTH">1 month</option>
          <option value="ONE_YEAR">1 year</option>
          <option value="FOREVER">Forever — until revoked</option>
          <option value="CUSTOM">Custom</option>
        </select>
        <div id="rememberCustom" class="remember-custom hidden">
          <label for="rememberAmount">Remember for</label>
          <div class="remember-duration">
            <input id="rememberAmount" type="number" min="1" max="3650" value="30">
            <select id="rememberUnit">
              <option value="MINUTES">Minutes</option>
              <option value="HOURS">Hours</option>
              <option value="DAYS" selected>Days</option>
              <option value="WEEKS">Weeks</option>
              <option value="MONTHS">Months</option>
              <option value="YEARS">Years</option>
            </select>
          </div>
        </div>
        <label for="browserLabel">Browser name</label>
        <input id="browserLabel" maxlength="64" autocomplete="off" placeholder="Example: Aleksu Vostro">
        <label id="foreverConfirmRow" class="switch-line hidden">
          <input id="foreverConfirm" type="checkbox">
          <span>I understand this browser stays trusted until revoked.</span>
        </label>
        <div id="rememberExpiryPreview" class="helper"></div>
        <div id="rememberWarning" class="warning-box">Use remembered access only on a trusted private network.</div>
      </div>
      <button class="primary" type="submit">Pair</button>
      <div id="pairMsg" class="inline-message" role="alert"></div>
    </form>
  </div>

  <div id="app" class="app-shell hidden">
    <aside id="navRail" class="nav-rail" aria-label="Primary navigation">
      <div class="brand-row"><span class="brand-mark small">FP</span><strong>FamilyPhotoFrame</strong></div>
      <nav id="navList"></nav>
    </aside>

    <header class="topbar">
      <button id="menuButton" class="icon-button mobile-only" aria-label="Open navigation">☰</button>
      <div class="topbar-title">
        <strong id="deviceName">FamilyPhotoFrame</strong>
        <span id="globalStatus" class="status-line" aria-live="polite">Connecting…</span>
      </div>
      <div class="topbar-actions">
        <span id="saveState" class="save-state" aria-live="polite"></span>
        <span id="versionBadge" class="badge"></span>
        <button class="topbar-signout" data-action="logout">Sign out</button>
      </div>
    </header>

    <main id="mainContent" class="main-content">
      <section id="tab-overview" class="tab-page" data-tab="overview"></section>
      <section id="tab-photos" class="tab-page hidden" data-tab="photos"></section>
      <section id="tab-playback" class="tab-page hidden" data-tab="playback"></section>
      <section id="tab-display" class="tab-page hidden" data-tab="display"></section>
      <section id="tab-schedule" class="tab-page hidden" data-tab="schedule"></section>
      <section id="tab-device" class="tab-page hidden" data-tab="device"></section>
      <section id="tab-diagnostics" class="tab-page hidden" data-tab="diagnostics"></section>
      <section id="tab-backup" class="tab-page hidden" data-tab="backup"></section>
      <section id="tab-about" class="tab-page hidden" data-tab="about"></section>
    </main>
  </div>

  <div id="drawerScrim" class="drawer-scrim hidden"></div>
  <div id="toast" class="toast hidden" role="status" aria-live="polite"></div>
  <dialog id="confirmDialog">
    <form method="dialog" class="dialog-card">
      <h2 id="confirmTitle">Confirm action</h2>
      <p id="confirmMessage"></p>
      <div class="dialog-actions">
        <button value="cancel">Cancel</button>
        <button id="confirmAccept" value="confirm" class="danger">Continue</button>
      </div>
    </form>
  </dialog>
  <dialog id="stepUpDialog">
    <form method="dialog" class="dialog-card">
      <h2 id="stepUpTitle">Confirm with frame PIN</h2>
      <p id="stepUpMessage">Enter the current PIN shown on the frame.</p>
      <label for="stepUpPin">Frame PIN</label>
      <input id="stepUpPin" type="password" inputmode="numeric" pattern="[0-9]*" maxlength="8" autocomplete="one-time-code">
      <div class="dialog-actions">
        <button value="cancel">Cancel</button>
        <button value="confirm" class="primary">Confirm</button>
      </div>
    </form>
  </dialog>
  <script src="/assets/app-${WebUiAssets.REVISION}.js" defer></script>
</body>
</html>
""".trimIndent()
}
