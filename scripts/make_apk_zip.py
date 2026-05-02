from pathlib import Path
import zipfile

src = Path('/home/grunvald/.hermes/hermes-agent/BandSongbook/app/build/outputs/apk/debug/app-debug.apk')
out = Path('/home/grunvald/.hermes/hermes-agent/BandSongbook/app-debug-apk.zip')
with zipfile.ZipFile(out, 'w', compression=zipfile.ZIP_DEFLATED) as zf:
    zf.write(src, arcname=src.name)
print(out)
