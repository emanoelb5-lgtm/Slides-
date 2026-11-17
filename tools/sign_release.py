#!/usr/bin/env python3
"""Sign a downloaded GitHub Actions APK using a private backup outside this repository."""
import argparse, hashlib, json, os, pathlib, subprocess
from apk_payload import payload_digest

parser = argparse.ArgumentParser()
parser.add_argument('--apk', type=pathlib.Path, required=True)
parser.add_argument('--backup', type=pathlib.Path, required=True)
parser.add_argument('--build-run', type=int, required=True)
parser.add_argument('--source-commit', required=True)
parser.add_argument('--sdk', type=pathlib.Path, required=True)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
credentials = json.loads((args.backup / 'credentials.json').read_text())
out = root / 'distribution/Slide-TD98.apk'
tools = sorted((args.sdk / 'build-tools').iterdir())[-1]
aligned = out.with_suffix('.aligned.apk')
subprocess.run([str(tools / 'zipalign'), '-f', '-p', '4', str(args.apk), str(aligned)], check=True)
env = dict(os.environ, SLIDE_STORE_PASSWORD=credentials['storePassword'], SLIDE_KEY_PASSWORD=credentials['keyPassword'])
subprocess.run([str(tools / 'apksigner'), 'sign', '--ks', str(args.backup / 'slide-release.p12'), '--ks-key-alias', credentials['alias'], '--ks-pass', 'env:SLIDE_STORE_PASSWORD', '--key-pass', 'env:SLIDE_KEY_PASSWORD', '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true', '--out', str(out), str(aligned)], env=env, check=True)
aligned.unlink()
assert payload_digest(args.apk) == payload_digest(out)
subprocess.run([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(out)], check=True)
version = dict(line.split('=',1) for line in (root / 'version.properties').read_text().splitlines() if '=' in line)
metadata = {'applicationId': 'online.b5solucoes.slide', 'versionCode': int(version['versionCode']), 'versionName': version['versionName'], 'sha256': hashlib.sha256(out.read_bytes()).hexdigest(), 'size': out.stat().st_size, 'buildRun': args.build_run, 'sourceCommit': args.source_commit, 'notes': 'Apresentações offline no TD98 Pro sem ATV. Navegação por controle remoto, pendrive, recentes com retomada, tela preta, miniaturas, modo automático e atualizações pelo GitHub. Leitura estática de PowerPoint/ODP; recursos complexos podem diferir. PDF e imagens são recomendados para fidelidade visual.'}
(root / 'distribution/release.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n')
print('Signed APK and release metadata are ready in distribution/.')
