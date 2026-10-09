#!/usr/bin/env python3
"""Check built packages for missing mixins, native entry points and licence notices."""
import json
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

root = Path(__file__).resolve().parent.parent
version = re.search(r'^mod_version=(.+)$', (root / 'gradle.properties').read_text(), re.M)[1]
for mc in ('26.2', '26.3'):
    shared = root / ('versions/26.2/shared' if mc == '26.2' else 'metal/src/main')
    with zipfile.ZipFile(root / f'dist/mcopt-{version}+mc{mc}.jar') as jar:
        names = set(jar.namelist())
        meta = json.loads(jar.read('fabric.mod.json'))
        assert meta['depends']['minecraft'] == f'~{mc}', meta
        assert ('sodium' in meta.get('depends', {})) == (mc == '26.2'), meta
        assert ('sodium' in meta.get('breaks', {})) == (mc == '26.3'), meta
        assert {'LICENSE', 'NOTICE', 'natives/libmcmetal.dylib'} <= names
        if mc == '26.2':
            assert 'LICENSES/PolyForm-Shield-1.0.0.md' in names
        else:
            assert meta['jars'] and all(entry['file'] in names for entry in meta['jars'])
        for config in meta['mixins']:
            mixin = json.loads(jar.read(config))
            classes = [f"{mixin['package']}.{name}" for group in ('client', 'mixins', 'server') for name in mixin.get(group, [])]
            if mixin.get('plugin'):
                classes.append(mixin['plugin'])
            for cls in classes:
                assert cls.replace('.', '/') + '.class' in names, (mc, config, cls)
        with tempfile.TemporaryDirectory() as directory:
            native = Path(directory) / 'libmcmetal.dylib'
            native.write_bytes(jar.read('natives/libmcmetal.dylib'))
            symbols = subprocess.check_output(['nm', '-gU', str(native)], text=True)
            exports = {line.split()[-1].removeprefix('_') for line in symbols.splitlines()}
            for source in (shared / 'java').rglob('*.java'):
                for symbol in re.findall(r'\bfn\("([^"]+)"', source.read_text()):
                    assert symbol in exports, (mc, source, symbol)
    print(f'{mc}: package, mixins and native symbols OK')
