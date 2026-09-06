import base64
import json
import pathlib
import time
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'verification' / 'results'
OUT.mkdir(exist_ok=True)
HEADERS = {'User-Agent': 'AnonymousSMP/1.0.2 (build verification; discord.gg/Z7fYhESTH)'}

def read(url):
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=30) as response:
                return response.read()
        except Exception:
            if attempt == 2:
                raise
            time.sleep(2)

def find_texture(value):
    if isinstance(value, dict):
        if isinstance(value.get('value'), str) and isinstance(value.get('signature'), str):
            return value
        for child in value.values():
            result = find_texture(child)
            if result:
                return result
    if isinstance(value, list):
        for child in value:
            result = find_texture(child)
            if result:
                return result
    return None

resource = ROOT / 'src/main/resources/skins.properties'
if not resource.exists():
    data = json.loads(read('https://raw.githubusercontent.com/SkinsRestorer/SkinsRestorer/9fa70a40b2d2fae4a26f1a31cc058d78b3884865/shared/src/main/resources/hardcoded_skins.json'))
    result = data['steve']
    result['value'] += '=' * (-len(result['value']) % 4)
    (OUT / 'steve-source.json').write_text(json.dumps(result, indent=2))
    if not result:
        raise RuntimeError('Could not retrieve the pinned Steve skin')
    decoded = json.loads(base64.b64decode(result['value'] + '=' * (-len(result['value']) % 4)))
    assert decoded['textures']['SKIN'].get('metadata', {}).get('model', 'classic') != 'slim', decoded
    assert 'CAPE' not in decoded['textures'], decoded
    skin_url = decoded['textures']['SKIN']['url'].replace('http://', 'https://')
    (OUT / 'steve.png').write_bytes(read(skin_url))
    (OUT / 'steve-texture.json').write_text(json.dumps(decoded, indent=2))
    author = json.loads(read('https://sessionserver.mojang.com/session/minecraft/profile/619cd8fd5d1b44c9889e784f14841e3d?unsigned=false'))
    texture = next(p for p in author['properties'] if p['name'] == 'textures')
    resource.write_text(f"steve.value={result['value']}\nsteve.signature={result['signature']}\nauthor.value={texture['value']}\nauthor.signature={texture['signature']}\n")

builds = json.loads(read('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds'))
if isinstance(builds, dict):
    builds = builds.get('builds', builds.get('results', []))
build = next(b for b in builds if b.get('channel', '').lower() in ('stable', 'recommended'))
(OUT / 'paper-build.json').write_text(json.dumps(build, indent=2))
download = build['downloads']['server:default']
jar = read(download['url'])
import hashlib
assert hashlib.sha256(jar).hexdigest() == download['checksums']['sha256']
server = ROOT / 'verification/server'
server.mkdir(exist_ok=True)
(server / 'paper.jar').write_bytes(jar)

source = read('https://raw.githubusercontent.com/PaperMC/Paper/ver/1.21.11/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftPlayer.java').decode()
start = source.index('private void refreshPlayer(')
(OUT / 'paper-refresh-source.txt').write_text(source[max(0, start-1800):start+4000])
print('Prepared pinned skin properties and Paper server.', flush=True)

(OUT / 'mojang-publickeys.json').write_bytes(read('https://api.minecraftservices.com/publickeys'))

keys = json.loads((OUT / 'mojang-publickeys.json').read_bytes())
fixtures = ROOT / 'src/test/resources'
fixtures.mkdir(parents=True, exist_ok=True)
(fixtures / 'mojang-profile-keys.txt').write_text('\n'.join(key['publicKey'] for key in keys['profilePropertyKeys']))
