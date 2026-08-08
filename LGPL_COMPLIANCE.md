# LGPL Compliance Plan — jcifs-ng

Required by spec §19 / §19.1 because the SMB source depends on an LGPL library.

## Component

- **Artifact:** `eu.agno3.jcifs:jcifs-ng`
- **Version:** 2.1.10 (pinned in `gradle/libs.versions.toml`)
- **License:** LGPL-2.1
- **Use:** SMB2/3 client for the NAS/SMB photo source (`SmbPhotoSource`).

## Obligations and how they are met

- **License text availability.** Settings → "Open-source licenses" lists jcifs-ng with
  its license name and a link to the license's canonical text
  (`https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html`), opened via the system
  browser. This links to the text rather than embedding a copy: reconstructing several
  thousand words of legal text from memory or from web fragments risks a subtly wrong
  copy, which is worse for a compliance screen than a correct link — and the Apache
  Software Foundation's own guidance for its (also common, also long) license is that a
  canonical link is sufficient in place of embedding the text. Also listed in
  `THIRD_PARTY_LICENSES.md`.
- **Relinking / replaceability.** jcifs-ng is used as an **unmodified**, separately
  distributed library, linked dynamically as a normal Gradle dependency. We do not
  fork or patch it. Because it is a standard `.jar`/`.aar` dependency, a user
  exercising LGPL §6 rights can substitute a compatible jcifs-ng build by rebuilding
  the app with a replaced artifact; the app does not statically inline or obfuscate
  the library in a way that prevents this. (R8/shrinking, when enabled in a later
  release-engineering pass, must keep jcifs-ng classes replaceable — documented as a
  release-engineering task.)
- **Modification notices.** None required — the library is unmodified. If it is ever
  patched, the modification and its source must be published per LGPL.
- **Source offer.** jcifs-ng source is publicly available from its upstream project
  (`https://github.com/AgNO3/jcifs-ng`); the notices screen links to it directly. No
  proprietary modifications exist to offer.

## Sign-off gate (before paid/public release)

- [ ] Release owner confirms jcifs-ng remains unmodified and dynamically linked.
- [x] Notices screen exists, lists jcifs-ng's license, and links to both the canonical
      LGPL-2.1 text and the upstream source — built this drop. *Link-based, not an
      embedded text copy; see "License text availability" above for why that is the
      chosen approach, and confirm the release owner is comfortable with it rather than
      an embedded copy before sign-off.*
- [ ] If R8 is enabled, verify jcifs-ng classes are kept/replaceable and the keep
      rules are recorded.
- [ ] Legal/compliance sign-off recorded for commercial distribution.

This is an engineering compliance plan; a human release owner must approve it before
any paid/public release (spec §19.1).
