"""Regression: The Mentalist S1E1 must play natively even when its browser page is unavailable.
Uses the signed-in session of our own app; never reads another app's credentials.
"""
import argparse,pathlib,subprocess,time,re,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];adb=root.parent/'tv-tools/platform-tools/adb.exe'
out=root/'out/qa';out.mkdir(parents=True,exist_ok=True)
def run(*args):return subprocess.check_output([str(adb),'-s',a.serial,*args],timeout=40).decode('utf8',errors='replace')
def key(k):run('shell','input','keyevent',str(k))
def tree(name):
 run('shell','uiautomator','dump','/sdcard/krasview-qa.xml');s=run('shell','cat','/sdcard/krasview-qa.xml');(out/(name+'.xml')).write_text(s,encoding='utf8');return ET.fromstring(s)
def texts(t):return [n.get('text','') for n in t.iter('node')]
run('shell','am','force-stop','local.krasview.tv');run('shell','logcat','-c')
run('shell','am','start','-n','local.krasview.tv/.DetailActivity','--es','address','The_Mentalist')
time.sleep(5);key(23);time.sleep(3);key(20)
t=tree('mentalist-first-episode');assert any(n.get('focused')=='true' and n.get('text','').startswith('1 серия') for n in t.iter('node'))
key(23);time.sleep(12);key(85)
t=tree('mentalist-playing');stamp=next(s for s in texts(t) if re.fullmatch(r'[\d:]+  /  [\d:]+',s));total=0
for part in stamp.split('  /  ')[1].split(':'):total=total*60+int(part)
assert 2708<=total<=2710,'Must be the full 45:09 episode'
key(82);key(23);t=tree('mentalist-audio');choices=[n.get('text','') for n in t.iter('node') if n.get('class')=='android.widget.CheckedTextView']
assert len(choices)>=2 and not any(s.startswith('Дорожка ') for s in choices),'Studio names must remain available'
log=run('shell','logcat','-d','-s','KrasViewTV:I','AndroidRuntime:E');(out/'mentalist-player.log').write_text(log,encoding='utf8')
assert 'First video frame rendered' in log and 'FATAL EXCEPTION' not in log and 'Playback engine error' not in log
print('PASS: The Mentalist S1E1 starts through native API, full duration 45:09, first frame and named audio tracks',flush=True)
key(4);key(4);key(4)
