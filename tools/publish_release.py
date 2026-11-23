#!/usr/bin/env python3
"""Publish an already signed APK, never load or create a private signing key in the public repo."""
import hashlib, io, json, os, pathlib, re, subprocess, tempfile, urllib.request, urllib.parse, zipfile
from apk_payload import payload_digest

root = pathlib.Path(__file__).resolve().parents[1]
repo = os.environ.get('GITHUB_REPOSITORY', 'emanoelb5-lgtm/Slides-')
token = os.environ['GH_TOKEN']
info = json.loads((root / 'distribution/release.json').read_text())
apk = root / 'distribution/Slide-TD98.apk'
sdk = pathlib.Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', '')))
build_tools = sdk / 'build-tools' / '35.0.0'
assert (build_tools / 'apksigner').is_file(), 'Android Build-Tools 35.0.0 must be installed'
report = subprocess.check_output([str(build_tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
fingerprint = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)', report).group(1).lower()
assert fingerprint == (root / 'signing/certificate.sha256').read_text().strip().lower(), 'Unexpected APK signing key'
badging = subprocess.check_output([str(build_tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
assert package and package.group(1) == info['applicationId'] == 'online.b5solucoes.slide'
assert int(package.group(2)) == info['versionCode'] and package.group(3) == info['versionName']
assert hashlib.sha256(apk.read_bytes()).hexdigest() == info['sha256']
assert apk.stat().st_size == info['size']

def request(url, data=None, method='GET', content_type='application/json'):
    body = None if data is None else (data if isinstance(data, bytes) else json.dumps(data).encode())
    req = urllib.request.Request(url, data=body, method=method, headers={'Authorization': 'Bearer ' + token, 'Accept': 'application/vnd.github+json', 'Content-Type': content_type, 'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'Slide-TD98-release'})
    with urllib.request.urlopen(req, timeout=120) as response:
        raw = response.read()
        return raw if content_type == 'application/zip' else json.loads(raw)

base = 'https://api.github.com/repos/' + repo
run = request(base + '/actions/runs/' + str(info['buildRun']))
assert run['conclusion'] == 'success' and run['head_sha'] == info['sourceCommit'], 'Source build has not passed all checks'
assert run['path'] == '.github/workflows/android.yml', 'Not the Android build workflow'
artifacts = request(base + '/actions/runs/' + str(info['buildRun']) + '/artifacts')['artifacts']
artifact = next(a for a in artifacts if a['name'] == 'slide-release-unsigned' and not a['expired'])
data = request(artifact['archive_download_url'], content_type='application/zip')
with zipfile.ZipFile(io.BytesIO(data)) as archive:
    name = next(n for n in archive.namelist() if n.endswith('app-release-unsigned.apk'))
    with tempfile.NamedTemporaryFile(suffix='.apk') as temp:
        temp.write(archive.read(name)); temp.flush()
        assert payload_digest(temp.name) == payload_digest(apk), 'Signed APK differs from GitHub Actions build'

tag = 'v' + info['versionName']
filename = 'Slide-TD98-' + info['versionName'] + '.apk'
manifest = {key: info[key] for key in ['applicationId', 'versionCode', 'versionName', 'sha256', 'size', 'notes']}
manifest['downloadUrl'] = 'https://github.com/' + repo + '/releases/download/' + tag + '/' + filename
manifest['certificateSha256'] = fingerprint
manifest['minSdk'] = 21
manifest['sourceCommit'] = info['sourceCommit']
manifest['buildRun'] = info['buildRun']
existing = request(base + '/releases')
release = next((r for r in existing if r['tag_name'] == tag), None)
if release is None:
    release = request(base + '/releases', {'tag_name': tag, 'target_commitish': info['sourceCommit'], 'name': 'Slide TD98 ' + info['versionName'], 'body': info['notes'] + '\n\nAPK universal, Android 5.0+, sem Google Play. Compilado e testado no GitHub Actions. Assinatura de lançamento fixa.', 'draft': True, 'prerelease': False}, 'POST')
elif not release['draft']:
    raise SystemExit('Release already published. Increment version.properties; existing versions are immutable.')

upload = release['upload_url'].split('{')[0]
assets = {a['name'] for a in release.get('assets', [])}
for name, content, mime in [(filename, apk.read_bytes(), 'application/vnd.android.package-archive'), ('update.json', json.dumps(manifest, ensure_ascii=False, indent=2).encode(), 'application/json'), ('SHA256SUMS.txt', (info['sha256'] + '  ' + filename + '\n').encode(), 'text/plain')]:
    if name in assets:
        raise SystemExit('Draft already has an asset with this name. Inspect the draft before retrying.')
    request(upload + '?name=' + urllib.parse.quote(name), content, 'POST', mime)
request(base + '/releases/' + str(release['id']), {'draft': False, 'make_latest': 'true'}, 'PATCH')
print('Published ' + tag + ' with verified permanent certificate and Actions payload.')
