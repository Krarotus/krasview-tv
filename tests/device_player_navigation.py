"""Real-device regression for deferred seeking, still frames and next-episode navigation.
Requires a signed-in app and the separate tests/android/DpadHold.java helper on the device.
"""
import argparse,pathlib,subprocess,time,re,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--hold-helper',default='/data/local/tmp/krasview-qa-hold.dex');p.add_argument('--section',choices=['all','boundary'],default='all');a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];adb=root.parent/'tv-tools/platform-tools/adb.exe';out=root/'out/qa';out.mkdir(parents=True,exist_ok=True)
def command(*args):return [str(adb),'-s',a.serial,*args]
def run(*args):return subprocess.check_output(command(*args),timeout=45).decode('utf8',errors='replace')
def key(k):run('shell','input','keyevent',str(k))
def hold(k,ms):run('shell','app_process','-Djava.class.path='+a.hold_helper,'/system/bin','DpadHold',str(k),str(ms))
def tree(name):
 run('shell','uiautomator','dump','/sdcard/krasview-qa.xml');s=run('shell','cat','/sdcard/krasview-qa.xml');(out/(name+'.xml')).write_text(s,encoding='utf8');return ET.fromstring(s)
def texts(t):return [n.get('text','') for n in t.iter('node')]
def stamp(t):
 s=next(s for s in texts(t) if re.fullmatch(r'[\d:]+  /  [\d:]+',s));values=[]
 for value in s.split('  /  '):
  total=0
  for part in value.split(':'):total=total*60+int(part)
  values.append(total)
 return values
def shot(name):run('shell','screencap','-p','/sdcard/krasview-qa.png');run('pull','/sdcard/krasview-qa.png',str(out/(name+'.png')))
def log():return run('shell','logcat','-d','-s','KrasViewTV:I','AndroidRuntime:E')
def start(activity,*args):
 run('shell','am','force-stop','local.krasview.tv');run('shell','am','start','-n','local.krasview.tv/.'+activity,*args)
def assert_paused(t):assert any(n.get('content-desc')=='Продолжить' for n in t.iter('node')),'Pause must survive seeking'
if a.section=='all':
 start('DetailActivity','--es','address','The_Mentalist');time.sleep(5);key(23);time.sleep(3);key(20)
 t=tree('navigation-series-frames');assert any(n.get('focused')=='true' and n.get('content-desc','').startswith('1 серия') for n in t.iter('node'));assert any(n.get('content-desc','').startswith('Превью: 1 серия') for n in t.iter('node'));shot('navigation-series-frames')
 key(23);time.sleep(10);key(85);base=stamp(tree('seek-before'))[0]
 key(22);right=stamp(tree('seek-tap'))[0];assert abs(right-base-10)<=1
 key(21);left=stamp(tree('seek-return'))[0];assert abs(right-left-10)<=1
 run('shell','logcat','-c')
 proc=subprocess.Popen(command('shell','app_process','-Djava.class.path='+a.hold_helper,'/system/bin','DpadHold','22','2600'),stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 time.sleep(1.1);assert 'Seek committed' not in log(),'Holding must not seek the decoder';shot('seek-offset-holding');proc.communicate(timeout=15);assert proc.returncode==0
 t=tree('seek-after-hold');after=stamp(t)[0];assert 90<=after-left<=130;assert_paused(t);assert log().count('Seek committed')==1,'Release must commit exactly once'
 time.sleep(2);assert stamp(tree('seek-still-paused'))[0]==after
 print('PASS: 10-second taps, accelerated offset preview, exactly one seek on release, pause preserved',flush=True)
 key(19);t=tree('next-menu');assert 'Следующая серия' in texts(t)
 for _ in range(3):key(20)
 key(23);time.sleep(10);key(85);t=tree('next-second-episode');assert any('2 серия' in s for s in texts(t));assert 2612<=stamp(t)[1]<=2614
 assert 'Playback engine error' not in log() and 'FATAL EXCEPTION' not in log()
 print('PASS: next episode from player opens The Mentalist S1E2 natively',flush=True)
# Last episode of season 1 must resolve the first episode of season 2.
start('PlayerActivity','--ei','video_id','169180','--es','address','The_Mentalist','--ez','series','true','--ei','season_id','571','--eia','season_ids','571,572');time.sleep(10);key(85);key(19)
t=tree('next-season-menu');assert 'Следующая серия' in texts(t)
for _ in range(3):key(20)
key(23);time.sleep(10);key(85);t=tree('next-season-first');assert any('2 сезон, 1 серия' in s for s in texts(t)),texts(t)
print('PASS: last episode of season 1 advances to season 2 episode 1',flush=True)
# End-of-episode offer, without automatic playback.
hold(22,30000);key(23);time.sleep(5);t=tree('next-ended-offer');assert 'Серия закончилась' in texts(t) and any(s.casefold()=='следующая серия' for s in texts(t))
key(23);time.sleep(10);key(85);t=tree('next-ended-selected');assert any('2 сезон, 2 серия' in s for s in texts(t))
print('PASS: episode ending offers next episode; confirmation starts it',flush=True)
start('DetailActivity','--es','address','WreckItRalph');time.sleep(5);key(23);time.sleep(3)
t=tree('navigation-film-frames');assert all(any(n.get('content-desc','').startswith('Превью: '+label) for n in t.iter('node')) for label in ['Фильм','Трейлер']);shot('navigation-film-frames')
key(23);time.sleep(10);key(85);key(19);assert 'Следующая серия' not in texts(tree('movie-no-next'))
assert 'Playback engine error' not in log() and 'FATAL EXCEPTION' not in log()
print('PASS: film and trailer have separate previews; films have no next-episode action',flush=True)
key(4);key(4);key(4)
