"""Device regression: buttonless HUD, D-pad control, real SRT/ASS subtitles and quality switching.
Requires signed-in app and tests/android/DpadHold.java compiled to the device helper path.
"""
import argparse,pathlib,subprocess,time,re,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--hold-helper',default='/data/local/tmp/krasview-qa-hold.dex');a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];adb=root.parent/'tv-tools/platform-tools/adb.exe';out=root/'out/qa';out.mkdir(parents=True,exist_ok=True)
def run(*args):return subprocess.check_output([str(adb),'-s',a.serial,*args],timeout=45).decode('utf8',errors='replace')
def key(k):run('shell','input','keyevent',str(k))
def hold(k,ms):run('shell','app_process','-Djava.class.path='+a.hold_helper,'/system/bin','DpadHold',str(k),str(ms))
def tree(name):
 run('shell','uiautomator','dump','/sdcard/krasview-qa.xml');s=run('shell','cat','/sdcard/krasview-qa.xml');(out/(name+'.xml')).write_text(s,encoding='utf8');return ET.fromstring(s)
def texts(t):return [n.get('text','') for n in t.iter('node')]
def stamp(t):
 s=next(s for s in texts(t) if re.fullmatch(r'[\d:]+  /  [\d:]+',s));total=0
 for part in s.split('  /  ')[0].split(':'):total=total*60+int(part)
 return total
def subtitles():key(19);key(20);key(20);key(23)
def choose(label,t):
 rows=[n for n in t.iter('node') if n.get('class')=='android.widget.CheckedTextView'];target=next(i for i,n in enumerate(rows) if label in n.get('text',''));focused=next(i for i,n in enumerate(rows) if n.get('focused')=='true' or n.get('selected')=='true')
 for _ in range(abs(target-focused)):key(20 if target>focused else 19)
 key(23)
def checked(label,t):return any(label in n.get('text','') and n.get('checked')=='true' for n in t.iter('node'))
def cues(timeout):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  log=run('shell','logcat','-d','-s','KrasViewTV:I','AndroidRuntime:E')
  assert 'FATAL EXCEPTION' not in log and 'Playback engine error' not in log,log
  if 'Subtitle cues rendered' in log:return
  time.sleep(.5)
 raise AssertionError('Subtitle cue was not rendered')
run('shell','am','force-stop','local.krasview.tv');run('shell','logcat','-c')
run('shell','am','start','-n','local.krasview.tv/.PlayerActivity','--ei','video_id','1008475','--es','address','Game_of_Thrones','--es','title','Game-of-Thrones')
time.sleep(10);key(85);t=tree('minimal-paused');base=stamp(t)
assert '↑ Настройки' in texts(t)
assert not any(n.get('clickable')=='true' for n in t.iter('node')),'Player HUD must have no clickable transport controls'
key(22);right=stamp(tree('minimal-right'));assert abs(right-base-10)<=1
key(21);left=stamp(tree('minimal-left'));assert abs(right-left-10)<=1
hold(22,2600);held=stamp(tree('minimal-hold'));assert 90<=held-left<=130
key(23);time.sleep(5);assert '↑ Настройки' not in texts(tree('minimal-autohide'));key(20);assert '↑ Настройки' in texts(tree('minimal-show'));key(23)
print('PASS: no transport buttons, up hint, OK pause/resume, 10-second seeks, accelerated hold and HUD autohide',flush=True)
hold(21,12000);assert stamp(tree('subtitle-start'))<=1
subtitles();t=tree('subtitles-off');assert checked('Выключены',t);assert all(any(x in s for s in texts(t)) for x in ['Forced','Full'])
choose('Full',t);run('shell','logcat','-c');key(23);cues(22);key(85)
run('shell','screencap','-p','/sdcard/krasview-subtitle.png');run('pull','/sdcard/krasview-subtitle.png',str(out/'subtitle-srt.png'))
before=stamp(tree('subtitle-srt-visible'));key(82);key(20);key(23);choose('360p',tree('subtitle-quality'));time.sleep(2)
subtitles();assert checked('Full',tree('subtitle-after-quality'));key(4);assert abs(stamp(tree('subtitle-position'))-before)<=1
print('PASS: real SRT cues render; text selection and paused position survive quality change',flush=True)
subtitles();choose('Выключены',tree('subtitle-disable'));hold(22,18000)
pos=stamp(tree('subtitle-ass-seek'));delta=round((2068-pos)/10)
for _ in range(abs(delta)):key(22 if delta>0 else 21)
subtitles();choose('Forced',tree('subtitle-ass-select'));run('shell','logcat','-c');key(23);cues(25);key(85)
run('shell','screencap','-p','/sdcard/krasview-subtitle.png');run('pull','/sdcard/krasview-subtitle.png',str(out/'subtitle-ass.png'))
subtitles();assert checked('Forced',tree('subtitle-ass-selected'));choose('Выключены',tree('subtitle-ass-off'));subtitles();assert checked('Выключены',tree('subtitles-disabled'));key(4)
print('PASS: real ASS cues render; subtitle tracks can be disabled',flush=True)
key(4);key(4)
