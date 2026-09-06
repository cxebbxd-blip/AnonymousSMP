import hashlib
import json
import pathlib
import subprocess
import zipfile

root = pathlib.Path(__file__).resolve().parents[1]
paths = subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode().split('\0')
manifest = {}
with zipfile.ZipFile(root / 'AnonymousSMPSource.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    for name in paths:
        if not name:
            continue
        path = root / name
        archive.write(path, 'AnonymousSMP/' + name)
        manifest[name] = hashlib.sha256(path.read_bytes()).hexdigest()
results = root / 'verification/results'
results.mkdir(parents=True, exist_ok=True)
(results / 'source-manifest.json').write_text(json.dumps(manifest, indent=2))
(results / 'source-commit.txt').write_text(subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root).decode())
jar = root / 'build/libs/AnonymousSMP.jar'
if jar.exists():
    (results / 'jar-sha256.txt').write_text(hashlib.sha256(jar.read_bytes()).hexdigest() + '  AnonymousSMP.jar\n')
