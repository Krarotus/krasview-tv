"""Real Ralph film/trailer chooser, playback identity/duration and return navigation."""
import argparse,pathlib,subprocess,time,re,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];out=root/'out/qa';out.mkdir(parents=True,exist_ok=True);adb=root.parent/'tv-tools/platform-tools/adb.exe'
def run(*cmd):return subprocess.check_output([str(adb),'-s',a.serial,*cmd],timeout=40).decode('utf8',errors='replace')
def key(k):run('shell','input','keyevent',str(k))
def tree(name):
 run('shell','uiautomator','dump','/sdcard/krasview-qa.xml');s=run('shell','cat','/sdcard/krasview-qa.xml');(out/(name+'.xml')).write_text(s,encoding='utf8');return ET.fromstring(s)
def texts(t):return [n.get('text') for n in t.iter('node') if n.get('text')]
def total(t):
 stamp=next(s.split('  /  ')[1] for s in texts(t) if re.fullmatch(r'[\d:]+  /  [\d:]+',s));n=0
 for part in stamp.split(':'):n=n*60+int(part)
 return n
def shot(name):run('shell','screencap','-p','/sdcard/krasview-qa.png');run('pull','/sdcard/krasview-qa.png',str(out/(name+'.png')))
run('shell','am','force-stop','local.krasview.tv')
run('shell','am','start','-n','local.krasview.tv/.DetailActivity','--es','address','WreckItRalph');time.sleep(5);key(23)
t=tree('ralph-choices');assert any('Фильм' in s and '1:41:14' in s for s in texts(t));assert any('Трейлер' in s and '2:21' in s for s in texts(t));shot('ralph-choices')
assert any(n.get('focused')=='true' and 'Фильм' in n.get('text','') for n in t.iter('node'))
print('PASS: Watch opens film/trailer list with real durations, first film focused',flush=True)
key(23);time.sleep(10);key(85);time.sleep(1);t=tree('ralph-full-film');assert 6072<=total(t)<=6075
assert any('Фильм' in s for s in texts(t))
key(82);key(20);key(23);t=tree('ralph-full-quality');assert all(s in texts(t) for s in ['1080p','720p','360p']);key(4)
print('PASS: full Ralph is 1:41:13 with 1080/720/360 variants',flush=True)
key(4);key(4);t=tree('ralph-return');assert any('Трейлер' in s for s in texts(t))
key(20);key(23);time.sleep(8);key(85);time.sleep(1);t=tree('ralph-trailer');assert 139<=total(t)<=142;assert any('Трейлер' in s for s in texts(t))
print('PASS: trailer is separate (2:21); Back restores the version list',flush=True)
key(4);key(4)
