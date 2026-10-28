"""Home snapshot, lazy posters, disk reuse and D-pad regression on the rooted QA stick.
Uses existing catalog/history, never changes account data or device networking.
"""
import argparse,pathlib,subprocess,time,re,json,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];adb=root.parent/'tv-tools/platform-tools/adb.exe';out=root/'out/qa';out.mkdir(parents=True,exist_ok=True)
def run(*args):return subprocess.check_output([str(adb),'-s',a.serial,*args],timeout=40).decode('utf8',errors='replace')
def key(k):run('shell','input','keyevent',str(k))
def tree():run('shell','uiautomator','dump','/sdcard/home-cache-qa.xml');return ET.fromstring(run('shell','cat','/sdcard/home-cache-qa.xml'))
def focus(t):return next((n.get('content-desc','') for n in t.iter('node') if n.get('focused')=='true' and n.get('clickable')=='true'),'')
def log():return run('shell','logcat','-d','-s','KrasViewTV:D','AndroidRuntime:E')
def launch():
 run('shell','am','force-stop','local.krasview.tv');run('shell','logcat','-c');run('shell','am','start','-W','-n','local.krasview.tv/.MainActivity');time.sleep(5)
def shot(name):run('shell','screencap','-p','/sdcard/home-cache-qa.png');run('pull','/sdcard/home-cache-qa.png',str(out/(name+'.png')))
def window():
 values=re.findall(r'Poster window; bound=(\d+) decoded=(\d+) total=(\d+)',log());assert values;return tuple(map(int,values[-1]))
launch();first=log();assert 'FATAL EXCEPTION' not in first
assert 'Home ready;' in first
bound,decoded,total=window();assert total>=36 and 0<decoded<=bound<total and bound<=32,(bound,decoded,total)
assert run('shell','test','-s','/data/user/0/local.krasview.tv/cache/home-v1.bin')==''
assert len(run('shell','ls','/data/user/0/local.krasview.tv/cache/art-v1').splitlines())<=256
base=focus(tree());assert base
shot('lazy-posters-home')
print('PASS: home snapshot exists; only a bounded visible poster window is decoded',flush=True)
launch();warm=log();ready=re.search(r'Home ready; cached=true elapsed_ms=(\d+)',warm);assert ready,'Warm start must display cached catalog first'
assert warm.count('Art ready; source=disk')>=6,'Visible posters must survive a cold process restart on disk'
initial=focus(tree());time.sleep(3);assert focus(tree())==initial,'Background refresh must preserve focus'
assert 'FATAL EXCEPTION' not in log()
mem=run('shell','dumpsys','meminfo','local.krasview.tv');pss=re.search(r'TOTAL PSS:\s*(\d+)',mem);summary={'cached_home_ms':int(ready.group(1)),'initial_poster_window':window(),'total_pss_kb':int(pss.group(1)) if pss else None}
for _ in range(10):key(22)
time.sleep(2);assert focus(tree())!=initial,'Must reach offscreen cards horizontally';bound,decoded,total=window();assert 0<decoded<=bound<=32 and bound<total
for _ in range(5):key(20)
time.sleep(3);assert focus(tree());bound,decoded,total=window();assert 0<decoded<=bound<=32 and bound<total
shot('lazy-posters-anime')
for _ in range(5):key(19)
time.sleep(2);assert focus(tree());assert 'FATAL EXCEPTION' not in log()
(out/'home-cache-metrics.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf8')
print('PASS: cached catalog rendered before refresh; disk posters, stable focus and distant row navigation; metrics='+json.dumps(summary),flush=True)
# Existing detail still obtains its poster/background/episode previews through the same image loader.
run('shell','am','start','-n','local.krasview.tv/.LauncherLinkActivity','-a','android.intent.action.VIEW','-d','krasviewtv://film/WreckItRalph');time.sleep(4);key(23);time.sleep(3);t=tree()
assert any(n.get('content-desc','').startswith('Превью: Фильм') for n in t.iter('node'))
assert any(n.get('content-desc','').startswith('Превью: Трейлер') for n in t.iter('node'))
shot('cached-film-previews');key(4);key(4);time.sleep(3);assert focus(tree());assert 'FATAL EXCEPTION' not in log()
print('PASS: detail previews and return to the cached home keep working',flush=True)
