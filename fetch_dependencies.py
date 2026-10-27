"""Fetch the exact player dependencies in libs/media3-lock.json. Python standard library only."""
import hashlib,json,pathlib,urllib.request,zipfile
root=pathlib.Path(__file__).resolve().parent
for dep in json.loads((root/'libs/media3-lock.json').read_text(encoding='utf8')):
    name=f"{dep['artifact']}-{dep['version']}.{dep['type']}"
    artifact=root/'libs/media3'/name
    repository='https://dl.google.com/dl/android/maven2/' if dep['group'].startswith('androidx.') else 'https://repo.maven.apache.org/maven2/'
    url=repository+dep['group'].replace('.','/')+'/'+dep['artifact']+'/'+dep['version']+'/'+name
    artifact.parent.mkdir(parents=True,exist_ok=True)
    if not artifact.exists():artifact.write_bytes(urllib.request.urlopen(url,timeout=60).read())
    assert hashlib.sha256(artifact.read_bytes()).hexdigest()==dep['sha256'],'Checksum mismatch: '+name
    if dep['type']=='aar':
        folder=(root/dep['jar']).parent;folder.mkdir(exist_ok=True)
        with zipfile.ZipFile(artifact) as archive:
            for entry in archive.namelist():
                if entry in ['classes.jar','AndroidManifest.xml','R.txt'] or entry.startswith('res/'):
                    archive.extract(entry,folder)
    print('Verified',name)
