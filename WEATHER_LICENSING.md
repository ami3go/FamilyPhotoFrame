# Weather provider licensing — release gate

Spec §11 requires the weather overlay to use a **commercial-compatible provider, or be
disabled by default**. This build satisfies that by shipping the feature **off by
default** with a **configurable endpoint and API key**, and by documenting the gate below.

## The distinction that matters

For Open-Meteo (the default endpoint) there are two separate licences, and they do not
say the same thing:

| Thing | Licence | Commercial use? |
|---|---|---|
| The weather **data** | CC BY 4.0 | Allowed, **attribution required** |
| The free **API service** (`api.open-meteo.com`) | Provider terms | **Not allowed** — free tier is non-commercial only |
| The paid **customer API** | Subscription | Allowed; dedicated endpoint + API key |

Open-Meteo's terms classify apps that carry subscriptions or advertising as commercial
use. A paid photo-frame app is therefore commercial, and pointing it at the free endpoint
would breach the service terms even though the underlying data is CC BY.

## How the app is built around that

- **Weather is disabled by default.** Nothing is requested until a user turns it on.
- **The endpoint is configurable** (Settings → Weather → service address), so a
  commercial deployment points at the customer endpoint.
- **An API key can be supplied** and is stored in the Android Keystore, never in the
  settings file (Contract Rule 5).
- **Coordinates are entered manually.** The app never requests a location permission and
  the permission audit stays clean.
- **Failure is silent.** A provider outage leaves the previous reading in place, then
  hides the overlay; it never blocks or delays the slideshow.

## Gate — must be done before any paid/public release with weather enabled

- [ ] Obtain an Open-Meteo commercial subscription (customer endpoint + API key) **or**
      select another provider whose terms permit commercial use, and change the default
      `endpointBaseUrl` accordingly.
- [x] Attribution added — built this drop. Shown in Settings → Weather whenever the
      overlay is enabled (a link, "Weather data \u00A9 Open-Meteo.com (CC BY 4.0)"),
      and again in Settings → "Open-source licenses". **Not** on the live TV overlay
      itself: a permanent on-screen credit line sitting over family photos for as long
      as weather is on would be a real UX cost for something CC BY does not actually
      require to be shown continuously on the work itself, only attributed. Reconsider
      if the eventual paid-tier terms require otherwise.
- [ ] Confirm the chosen provider's terms permit the frame's request volume (one call per
      refresh interval per device; default 30 minutes).
- [ ] Record the decision and the signed agreement with the release owner.

Until this gate is signed off, weather must remain **off by default** and must not be
advertised as a product feature.

## Privacy note

When weather is enabled the frame sends the **user-entered coordinates** to the
configured third-party endpoint on each refresh. This is disclosed in
DATA_SAFETY_WORKSHEET.md. No device location is read and no personal identifier is sent.
