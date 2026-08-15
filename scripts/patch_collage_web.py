#!/usr/bin/env python3
from pathlib import Path
import re
R=Path(__file__).resolve().parents[1]
def rd(p): return (R/p).read_text(encoding='utf-8')
def wr(p,s): (R/p).write_text(s,encoding='utf-8')
def rep(s,a,b,label):
    if s.count(a)!=1: raise RuntimeError(f'{label}: {s.count(a)} matches')
    return s.replace(a,b,1)
def rex(s,p,b,label):
    o,n=re.subn(p,b,s,count=1,flags=re.S)
    if n!=1: raise RuntimeError(f'{label}: {n} matches')
    return o

p='app/src/main/java/com/example/familyphotoframe/web/WebSettingsJson.kt'; s=rd(p)
s=rep(s,'''            put("portraitCollageGap", s.portraitCollage.gap.name)
            put("portraitCollageAnimateThree", s.portraitCollage.animateThreePhotoFrames)''','''            put("portraitCollageGap", s.portraitCollage.gap.name)
            put("portraitCollageOrientationFilter", s.portraitCollage.orientationFilter.name)
            put("portraitCollageLayoutPreference", s.portraitCollage.layoutPreference.name)
            put("portraitCollageScaleMode", s.portraitCollage.scaleMode.name)
            put("portraitCollageAlignment", s.portraitCollage.alignment.name)
            put("portraitCollageBackground", s.portraitCollage.background.name)
            put("portraitCollageCornerRadiusDp", s.portraitCollage.cornerRadiusDpClamped)
            put("portraitCollageAnimateThree", s.portraitCollage.animateThreePhotoFrames)''','projection'); wr(p,s)

p='app/src/main/java/com/example/familyphotoframe/web/WebSettingsPatchApplier.kt'; s=rd(p)
s=rep(s,'import com.example.familyphotoframe.data.settings.CollageGap\n','import com.example.familyphotoframe.data.settings.CollageAlignment\nimport com.example.familyphotoframe.data.settings.CollageBackground\nimport com.example.familyphotoframe.data.settings.CollageGap\nimport com.example.familyphotoframe.data.settings.CollageLayoutPreference\nimport com.example.familyphotoframe.data.settings.CollageOrientationFilter\nimport com.example.familyphotoframe.data.settings.CollageScaleMode\n','imports')
a='''        str("portraitCollageGap")?.let { v ->
            if (CollageGap.entries.none { it.name == v }) return "Unknown portraitCollageGap"
        }
'''
b=a+'''        str("portraitCollageOrientationFilter")?.let { v -> if (CollageOrientationFilter.entries.none { it.name == v }) return "Unknown portraitCollageOrientationFilter" }
        str("portraitCollageLayoutPreference")?.let { v -> if (CollageLayoutPreference.entries.none { it.name == v }) return "Unknown portraitCollageLayoutPreference" }
        str("portraitCollageScaleMode")?.let { v -> if (CollageScaleMode.entries.none { it.name == v }) return "Unknown portraitCollageScaleMode" }
        str("portraitCollageAlignment")?.let { v -> if (CollageAlignment.entries.none { it.name == v }) return "Unknown portraitCollageAlignment" }
        str("portraitCollageBackground")?.let { v -> if (CollageBackground.entries.none { it.name == v }) return "Unknown portraitCollageBackground" }
        int("portraitCollageCornerRadiusDp")?.let { if (it !in 0..32) return "portraitCollageCornerRadiusDp must be 0-32" }
'''
s=rep(s,a,b,'validation')
a='''            str("portraitCollageGap")?.let { v ->
                next = next.copy(portraitCollage = next.portraitCollage.copy(
                    gap = CollageGap.valueOf(v),
                ))
            }
'''
b=a+'''            str("portraitCollageOrientationFilter")?.let { v -> next = next.copy(portraitCollage = next.portraitCollage.copy(orientationFilter = CollageOrientationFilter.valueOf(v))) }
            str("portraitCollageLayoutPreference")?.let { v -> next = next.copy(portraitCollage = next.portraitCollage.copy(layoutPreference = CollageLayoutPreference.valueOf(v))) }
            str("portraitCollageScaleMode")?.let { v -> next = next.copy(portraitCollage = next.portraitCollage.copy(scaleMode = CollageScaleMode.valueOf(v))) }
            str("portraitCollageAlignment")?.let { v -> next = next.copy(portraitCollage = next.portraitCollage.copy(alignment = CollageAlignment.valueOf(v))) }
            str("portraitCollageBackground")?.let { v -> next = next.copy(portraitCollage = next.portraitCollage.copy(background = CollageBackground.valueOf(v))) }
            int("portraitCollageCornerRadiusDp")?.let { v -> next = next.copy(portraitCollage = next.portraitCollage.copy(cornerRadiusDp = v)) }
'''
s=rep(s,a,b,'application'); wr(p,s)

p='app/src/main/java/com/example/familyphotoframe/web/WebUiScript.kt'; s=rd(p)
pattern=r'''var collage=card\('Portrait collage'.*?,'playback-collage-card'\);var mode='''
replacement='''var collage=card('Collage','Combine 2–3 photos with adjustable orientation, layout, scaling and framing',row('Mode','',selectControl('portraitCollageMode',c.portraitCollageMode,[['OFF','Off'],['AUTOMATIC','Automatic'],['ALWAYS_TWO','Always use two'],['PREFER_THREE','Prefer three']],'portraitCollageMode'),'portraitCollageMode')+row('Maximum photos','',selectControl('portraitCollageMaxPhotos',c.portraitCollageMaxPhotos||3,[[2,'2'],[3,'3']],'portraitCollageMaxPhotos'),'portraitCollageMaxPhotos')+row('Photo orientation','Limit which photos can join a collage',selectControl('portraitCollageOrientationFilter',c.portraitCollageOrientationFilter||'ANY',[['ANY','Any'],['PORTRAIT_ONLY','Vertical only'],['LANDSCAPE_ONLY','Horizontal only'],['SAME_AS_ANCHOR','Match selected photo']],'portraitCollageOrientationFilter'),'portraitCollageOrientationFilter')+row('Layout style','Choose equal rows/columns, featured layouts, or automatic',selectControl('portraitCollageLayoutPreference',c.portraitCollageLayoutPreference||'AUTO',[['AUTO','Automatic'],['COLUMNS','Columns'],['ROWS','Rows'],['FEATURED','Featured']],'portraitCollageLayoutPreference'),'portraitCollageLayoutPreference')+row('Photo scaling','Fit keeps the whole image visible; crop fills each tile',selectControl('portraitCollageScaleMode',c.portraitCollageScaleMode||'CROP',[['CROP','Fill & crop'],['FIT','Fit whole photo']],'portraitCollageScaleMode'),'portraitCollageScaleMode')+row('Photo alignment','Crop focus / fitted-image placement',selectControl('portraitCollageAlignment',c.portraitCollageAlignment||'CENTER',[['CENTER','Center'],['TOP','Top'],['BOTTOM','Bottom'],['LEFT','Left'],['RIGHT','Right']],'portraitCollageAlignment'),'portraitCollageAlignment')+row('Background','Used for Fit letterboxing and gaps',selectControl('portraitCollageBackground',c.portraitCollageBackground||'APP_BACKGROUND',[['APP_BACKGROUND','Frame color'],['BLACK','Black'],['WHITE','White']],'portraitCollageBackground'),'portraitCollageBackground')+row('Rounded corners','0 to 32 dp',inputControl('portraitCollageCornerRadiusDp',c.portraitCollageCornerRadiusDp||0,'number','portraitCollageCornerRadiusDp',0,32,4),'portraitCollageCornerRadiusDp')+row('Single-photo fallback','When no compatible collage can be formed',selectControl('portraitCollageFallback',c.portraitCollageFallback,[['BLURRED_BACKGROUND','Blurred background'],['SOLID_BACKGROUND','Solid background'],['CROP_TO_FILL','Crop to fill']],'portraitCollageFallback'),'portraitCollageFallback')+row('Tile gap','',selectControl('portraitCollageGap',c.portraitCollageGap,[['NONE','None'],['SMALL','Small'],['MEDIUM','Medium'],['LARGE','Large']],'portraitCollageGap'),'portraitCollageGap')+row('Animate three-photo columns','Disabled automatically with Fit whole photo',toggleControl('portraitCollageAnimateThree',c.portraitCollageAnimateThree!==false,'portraitCollageAnimateThree'),'portraitCollageAnimateThree'),'playback-collage-card');var mode='''
s=rex(s,pattern,replacement,'web card'); wr(p,s)

p='app/src/main/java/com/example/familyphotoframe/web/WebUiAssets.kt'; s=rd(p)
s=rep(s,'const val REVISION: String = "v52152"','const val REVISION: String = "v52153"','asset revision'); wr(p,s)
print('collage web settings patch applied')
