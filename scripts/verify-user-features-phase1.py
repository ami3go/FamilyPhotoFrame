#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
checks={
"playlist models": (root/"app/src/main/java/com/example/familyphotoframe/data/settings/AppSettings.kt", ["data class SlideshowPlaylist", "data class PlaylistScheduleRule", "data class PlaylistSettings", "builtin_recent_uploads"]),
"brightness models": (root/"app/src/main/java/com/example/familyphotoframe/data/settings/AppSettings.kt", ["data class BrightnessAutomationSettings", "enum class NightAction"]),
"upload model": (root/"app/src/main/java/com/example/familyphotoframe/data/settings/AppSettings.kt", ["data class WebUploadSettings", "UploadDuplicatePolicy"]),
"batch curation": (root/"app/src/main/java/com/example/familyphotoframe/data/db/PhotoDao.kt", ["setFavorites(ids", "setHiddenBatch(ids"]),
"local upload source": (root/"app/src/main/java/com/example/familyphotoframe/data/source/LocalUploadPhotoSource.kt", ["APP_PRIVATE_UPLOADS", "FamilyPhotoFrame", "local-photo-library"]),
"schedule evaluator": (root/"app/src/main/java/com/example/familyphotoframe/domain/schedule/PlaylistSchedule.kt", ["activeRule", "minutesUntilBoundary"]),
"brightness evaluator": (root/"app/src/main/java/com/example/familyphotoframe/domain/schedule/BrightnessPolicy.kt", ["fun decide", "temporaryWakeUntilEpochMs"]),
}
failed=[]
for name,(path,tokens) in checks.items():
    text=path.read_text()
    missing=[t for t in tokens if t not in text]
    if missing: failed.append(f"{name}: missing {missing}")
if failed:
    raise SystemExit("\n".join(failed))
print("phase 1 user-feature contracts passed")
