# Build 90 diagnostic catalog correction

The clean Build 89 hardware start recovered one interrupted shuffle reservation. That
exercised two existing fields which the central diagnostic catalog did not allow:
`RESERVATION_RECOVERED.ageMs` and `SHUFFLE_STARTUP_RECOVERY.reservations`. The events
were still durable, but the catalog correctly counted the stripped fields and made the
diagnostic-integrity gate fail.

Build 90 keeps the Build 89 media-cache hashing and retention changes unchanged. It only
adds those two non-sensitive numeric fields to the engine-event allowlist and adds a unit
contract covering both recovery events. A new version code is required because a fresh
hardware session must prove `fieldsDropped=0`; the contaminated Build 89 session cannot
be reused for qualification.
