#!/usr/bin/env python3
"""Render supplied SVGs into Android icons. Requires rsvg-convert and Pillow.

The launcher adds its own mask/zoom, so the foreground needs a 1/6 inset.
Notification icons use coloroutline.svg's filled alpha, with system-applied tint.
"""
from pathlib import Path
import subprocess,io,xml.etree.ElementTree as E
from PIL import Image
root = Path(__file__).resolve().parents[1]
res = root / 'app/src/main/res'
out = res / 'drawable-nodpi'
out.mkdir(parents=True, exist_ok=True)
icons=[('white','whitelogo','#FFFFFF'),('white_black','whitelogowithblack','#FFFFFF'),('green','greenlogo','#16A34A'),('dark_green','darkgreenlogo','#276456'),('pink','pinklogo','#DB2777'),('blue','blue','#2563EB'),('blue_shade','blueshadelogo','#DDEAFF'),('black','black','#000000')]
def render(name,size):
 return Image.open(io.BytesIO(subprocess.check_output(['rsvg-convert','-w',str(size),'-h',str(size),str(root / 'assets' / (name + '.svg'))]))).convert('RGBA')
for key,name,bg in icons:
 render(name,512).save(out/f'aro_icon_{key}.png',optimize=True)
 (res/'mipmap-anydpi-v26'/f'aro_launcher_{key}.xml').write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/aro_icon_background_{key}" />
    <foreground>
        <inset android:drawable="@drawable/aro_icon_{key}"
            android:insetLeft="16.67%" android:insetTop="16.67%"
            android:insetRight="16.67%" android:insetBottom="16.67%" />
    </foreground>
    <monochrome android:drawable="@drawable/aro_icon_monochrome" />
</adaptive-icon>
''')
for key,name in [('white','whiteoutline'),('black','blackoutline'),('color','coloroutline')]:
 # Preserve aspect ratio while making a square resource for Android views.
 raw=Image.open(io.BytesIO(subprocess.check_output(['rsvg-convert','-h','256',str(root / 'assets' / (name + '.svg'))]))).convert('RGBA')
 square=Image.new('RGBA',(256,256));square.alpha_composite(raw,((256-raw.width)//2,0));square.save(out/f'aro_outline_{key}.png',optimize=True)
# The filled color artwork supplies a readable silhouette; thin outline strokes vanish at status-bar sizes.
outline=Image.open(out/'aro_outline_color.png').convert('RGBA')
# Fit the full silhouette inside the circular 66/108 safe zone, including its diagonal tip.
mono=Image.new('RGBA',(432,432));mark=outline.resize((200,200),Image.Resampling.LANCZOS)
mono.alpha_composite(mark,((432-200)//2,(432-200)//2));mono.save(out/'aro_icon_monochrome.png',optimize=True)
for density,pixels in [('mdpi',24),('hdpi',36),('xhdpi',48),('xxhdpi',72),('xxxhdpi',96)]:
 folder=res/f'drawable-{density}';folder.mkdir(exist_ok=True)
 mark=outline.resize((round(pixels*0.92),round(pixels*0.92)),Image.Resampling.LANCZOS)
 # Notification small icons carry shape/alpha only; Android supplies the tint.
 white=Image.new('RGBA',mark.size,'white');white.putalpha(mark.getchannel('A'))
 canvas=Image.new('RGBA',(pixels,pixels));canvas.alpha_composite(white,((pixels-white.width)//2,(pixels-white.height)//2))
 canvas.save(folder/'ic_aro_notification.png',optimize=True)
(res/'values'/'colors_app_icons.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'+''.join(f'    <color name="aro_icon_background_{key}">{bg}</color>\n' for key,_,bg in icons)+'</resources>\n')
# Existing application icon references now resolve to the requested default as well.
white_xml=(res/'mipmap-anydpi-v26'/'aro_launcher_white.xml').read_text()
for name in ['ic_launcher','ic_launcher_round']:(res/'mipmap-anydpi-v26'/f'{name}.xml').write_text(white_xml)
print('Generated padded adaptive icons and filled notification/themed masks.')

# Use the same Aro silhouette for in-app branding, including older resource aliases.
svg = E.parse(root / 'assets/coloroutline.svg').getroot()
logo_path = svg.find('{http://www.w3.org/2000/svg}path').get('d')
vector = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Aro silhouette derived from assets/coloroutline.svg. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="158" android:viewportHeight="158">
    <group android:translateX="15">
        <path android:fillColor="#FF000000" android:pathData="{logo_path}" />
    </group>
</vector>
'''
for name in ['aro_base_monochrome', 'svara_base_monochrome', 'monochrome_player', 'new_monochrome', 'monochrome']:
    (res / 'drawable' / f'{name}.xml').write_text(vector)
art = Image.open(out / 'aro_icon_white.png').convert('RGBA')
for density, size in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    folder = res / f'mipmap-{density}'
    for name in ['ic_launcher', 'ic_launcher_round']:
        art.resize((size, size), Image.Resampling.LANCZOS).save(folder / f'{name}.webp', lossless=True)
    Image.new('RGBA', (size, size), 'white').save(folder / 'ic_launcher_background.webp', lossless=True)
    foreground = Image.new('RGBA', (size, size))
    mark = art.resize((round(size * 2 / 3), round(size * 2 / 3)), Image.Resampling.LANCZOS)
    foreground.alpha_composite(mark, ((size - mark.width) // 2, (size - mark.height) // 2))
    foreground.save(folder / 'ic_launcher_foreground.webp', lossless=True)
art.save(root / 'web/public/aro-logo-white.png', optimize=True)
