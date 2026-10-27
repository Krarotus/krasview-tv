"""Real DASH quality/voice regression on the Game of Thrones finale (1008475)."""
import argparse,pathlib,subprocess,time,xml.etree.ElementTree as ET,re
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];out=root/'out/qa';out.mkdir(parents=True,exist_ok=True)
adb=root.parent/'tv-tools/platform-tools/adb.exe'
def run(*cmd):return subprocess.check_output([str(adb),'-s',a.serial,*cmd],timeout=40).decode('utf8',errors='replace')
def key(k):run('shell','input','keyevent',str(k))
def tree(name):
 run('shell','uiautomator','dump','/sdcard/krasview-qa.xml');s=run('shell','cat','/sdcard/krasview-qa.xml');(out/(name+'.xml')).write_text(s,encoding='utf8');return ET.fromstring(s)
def texts(t):return [n.get('text') for n in t.iter('node') if n.get('text')]
def menu_audio():key(82);key(23)
def menu_quality():key(82);key(20);key(23)
def stamp(t):
 s=next(s for s in texts(t) if re.fullmatch(r'[\d:]+  /  [\d:]+',s));v=0
 for part in s.split('  /  ')[0].split(':'):v=v*60+int(part)
 return v
def choose(label,t):
 # Traverse the actual focused radio list using D-pad only.
 rows=[n for n in t.iter('node') if n.get('class')=='android.widget.CheckedTextView']
 target=next(i for i,n in enumerate(rows) if n.get('text')==label)
 focused=next((i for i,n in enumerate(rows) if n.get('focused')=='true' or n.get('selected')=='true'),None)
 if focused is None:raise AssertionError('Radio list must receive focus')
 for _ in range(abs(target-focused)):key(20 if target>focused else 19)
 key(23)
def checked(label,t):return any(n.get('text')==label and n.get('checked')=='true' for n in t.iter('node'))
run('shell','am','force-stop','local.krasview.tv')
run('shell','logcat','-c')
run('shell','am','start','-n','local.krasview.tv/.PlayerActivity','--ei','video_id','1008475','--es','address','Game_of_Thrones','--es','title','Game-of-Thrones')
time.sleep(10);key(85);time.sleep(1)
base=stamp(tree('tracks-paused'))
menu_audio();t=tree('voices-available')
assert all(s in texts(t) for s in ['Dub (Amedia)','AlexFilm','LostFilm','Original'])
choose('LostFilm',t);menu_audio();t=tree('voice-selected');assert checked('LostFilm',t);key(4)
print('PASS: real studio names, LostFilm selected with D-pad',flush=True)
menu_quality();t=tree('qualities-available')
assert all(s in texts(t) for s in ['Авто','1080p','720p','360p'])
choose('360p',t);time.sleep(2);menu_quality();t=tree('quality-360');assert checked('360p',t);key(4)
menu_audio();assert checked('LostFilm',tree('voice-after-quality'));key(4)
assert abs(stamp(tree('position-after-quality'))-base)<=1
print('PASS: real 1080/720/360 options; 360 selection preserves LostFilm and paused position',flush=True)
key(85);time.sleep(5);key(85)
menu_quality();choose('1080p',tree('quality-to-1080'));time.sleep(2)
key(85);time.sleep(5);key(85)
menu_quality();t=tree('quality-1080');assert checked('1080p',t)
choose('Авто',t);menu_quality();assert checked('Авто',tree('quality-auto'));key(4)
print('PASS: quality switches 360 -> 1080 -> Auto',flush=True)
key(22);time.sleep(1);right=stamp(tree('tracks-right'));key(21);time.sleep(1);left=stamp(tree('tracks-left'));assert abs(right-left-10)<=1
print('PASS: new engine retains 10-second D-pad seeks',flush=True)
log=run('shell','logcat','-d','-s','KrasViewTV','AndroidRuntime')
(out/'tracks-player.log').write_text(log,encoding='utf8')
assert 'First video frame rendered' in log
assert 'Video size=640x360' in log and 'Video size=1920x1080' in log
assert 'FATAL EXCEPTION' not in log and 'Playback engine error' not in log
print('PASS: decoder rendered real 360p and 1080p frames, no engine errors',flush=True)
key(4);key(4)
run('shell','am','start','-n','local.krasview.tv/.MainActivity','--activity-clear-top','--activity-single-top','--ei','section','0')
