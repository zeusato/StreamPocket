"""Prepare the signed release APK and updater manifest. Run after assembleRelease."""
from pathlib import Path
import hashlib
import json
import shutil

root = Path(__file__).resolve().parent
output = root / "app/build/outputs/apk/release"
metadata = json.loads((output / "output-metadata.json").read_text(encoding="utf-8"))
element, = metadata["elements"]
version = element["versionName"]
code = element["versionCode"]
assert metadata["applicationId"] == "vn.streampocket.mobile"
apk = output / element["outputFile"]
assert apk.is_file() and not apk.name.endswith("-unsigned.apk"), "Build a signed release APK first"
dest = root / "releases" / ("v" + version)
dest.mkdir(parents=True, exist_ok=True)
name = "Stream-Pocket-" + version + ".apk"
target = dest / name
shutil.copyfile(apk, target)
sha = hashlib.sha256(target.read_bytes()).hexdigest()
manifest = {"packageName": metadata["applicationId"], "versionCode": code, "versionName": version, "apk": name, "size": target.stat().st_size, "sha256": sha}
(dest / "update.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
(dest / "SHA256SUMS.txt").write_text(sha + "  " + name + "\n", encoding="utf-8")
print(str(dest))
print(json.dumps(manifest))
