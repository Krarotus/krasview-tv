"""Audit the Git index before publishing. Never print matched credential contents."""
import pathlib,re,subprocess

root=pathlib.Path(__file__).resolve().parents[1]
files=subprocess.check_output(['git','ls-files','-z'],cwd=root).decode('utf8').split('\0')
rules={
    'GitHub credential':rb'(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{30,})',
    'private key':rb'-----BEGIN [A-Z ]*PRIVATE KEY-----',
    'AWS access key':rb'AKIA[0-9A-Z]{16}',
    'personal Windows path':rb'C:[\\/]+Users[\\/]+User[\\/]',
    'home network address':rb'192\.168\.[0-9]{1,3}\.[0-9]{1,3}',
}
problems=[]
for name in filter(None,files):
    p=pathlib.PurePosixPath(name)
    if p.parts[0] in ['out','research'] or name.startswith('libs/media3/') or p.suffix.lower() in ['.apk','.keystore','.jks','.p12','.pfx','.idsig'] or '.local.' in p.name or p.name.startswith('.env'):
        problems.append((name,'private/generated file'))
    if p.suffix=='.jar':continue  # Published upstream dependency, not local research artifacts.
    data=subprocess.check_output(['git','show',':'+name],cwd=root)
    for label,pattern in rules.items():
        if re.search(pattern,data):problems.append((name,label))
if problems:
    for name,label in problems:print(name+': '+label)
    raise SystemExit(1)
print('PASS: staged files contain no known credential patterns, private paths, signing keys or research artifacts')
