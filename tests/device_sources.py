"""Player QA with public sample media. Requires configured Lampac; does not change its settings."""
import argparse,json,pathlib,re,shlex,subprocess,time,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];adb=root.parent/'tv-tools/platform-tools/adb.exe'
def run(*args):return subprocess.check_output([str(adb),'-s',a.serial,*args],timeout=40).decode('utf8',errors='replace')
def key(k):run('shell','input','keyevent',str(k))
def tree():
 run('shell','uiautomator','dump','/sdcard/source-player-qa.xml')
 return ET.fromstring(run('shell','cat','/sdcard/source-player-qa.xml'))
def texts():return [n.get('text','') for n in tree().iter('node')]
def position():
 s=next(s for s in texts() if re.fullmatch(r'[\d:]+  /  [\d:]+',s));n=0
 for part in s.split('  /  ')[0].split(':'):n=n*60+int(part)
 return n
def play(node,identity):
 run('shell','am','force-stop','local.krasview.tv');run('shell','logcat','-c')
 run('shell','am','start','-n','local.krasview.tv/.PlayerActivity','--es','playback_source','lampac','--es','source_reference',shlex.quote(json.dumps(node)),'--es','position_key',identity)
 time.sleep(12)
def quality():key(82);key(20);key(23)
base='https://download.blender.org/durian/trailer/sintel_trailer-'
play({'method':'play','title':'Sintel sample QA','voice_name':'Fixture voice','url':base+'480p.mp4','quality':{'480p':base+'480p.mp4','720p':base+'720p.mp4'}},'qa-sintel')
key(85);key(22);before=position();quality()
assert all(label in texts() for label in ['480p','720p','Авто'])
# The initial selection is Auto; the highest explicit quality is the next row.
key(20);key(23);time.sleep(8)
assert abs(position()-before)<=1,'File quality switch must retain paused position'
key(82);key(23);assert 'Fixture voice' in texts();key(4)
log=run('shell','logcat','-d','-s','KrasViewTV:I','AndroidRuntime:E')
assert 'Video size=854x480' in log or 'Video size=852x480' in log
assert 'Video size=1280x720' in log and 'FATAL EXCEPTION' not in log
print('PASS: source file quality 480 -> 720 retains paused position and voice',flush=True)
play({'method':'play','title':'Angel One HLS sample QA','type':'hls','url':'https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8'},'qa-hls')
log=run('shell','logcat','-d','-s','KrasViewTV:I','AndroidRuntime:E')
assert 'First video frame rendered' in log and 'Playback engine error' not in log and 'FATAL EXCEPTION' not in log
print('PASS: HLS sample renders first frame',flush=True)
key(4);key(4)
