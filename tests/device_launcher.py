"""Free My TV integration: feed cards, six-slot carousel, type tabs and title deep links.
Requires the companion launcher patch and a rooted QA device with recommendation cache.
Reads only our application's published cards, never session data.
"""
import argparse,pathlib,subprocess,time,json,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--background-only',action='store_true');a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];adb=root.parent/'tv-tools/platform-tools/adb.exe';out=root/'out/qa';out.mkdir(parents=True,exist_ok=True)
def run(*args):return subprocess.check_output([str(adb),'-s',a.serial,*args],timeout=40).decode('utf8',errors='replace')
def key(k):run('shell','input','keyevent',str(k))
def tree():run('shell','uiautomator','dump','/sdcard/launcher-qa.xml');return ET.fromstring(run('shell','cat','/sdcard/launcher-qa.xml'))
def texts(t):return [n.get('text','') for n in t.iter('node')]
def cards(t):return [n.get('text','') for n in t.iter('node') if n.get('clickable')=='true' and 'App: KrasView' in n.get('text','')]
def focus(t):return next((n for n in t.iter('node') if n.get('focused')=='true' and 'App: KrasView' in n.get('text','')),None)
def shot(name):run('shell','screencap','-p','/sdcard/launcher-qa.png');run('pull','/sdcard/launcher-qa.png',str(out/(name+'.png')))
raw=run('exec-out','cat','/data/user/0/local.krasview.tv/shared_prefs/recommendations.xml');values={n.get('name'):n.text for n in ET.fromstring(raw)};movies=json.loads(values['row-movie'])['items'];shows=json.loads(values['row-series'])['items'];anime=json.loads(values['row-anime'])['items']
if not a.background_only:
 run('shell','input','keyevent','3');time.sleep(5);t=tree();assert len(cards(t))==6,'Launcher must display six real cards'
 key(19);assert focus(tree()) is not None,'Up from apps reaches recommendations'
 key(19);key(22);t=tree();assert all(any(label.startswith(f['title']+',') for f in movies) for label in cards(t)),'Movies tab must contain only movie recommendations'
 key(20)
 for _ in range(8):key(22)
 t=tree();assert len(cards(t))==6 and focus(t) is not None
 assert any(any(label.startswith(f['title']+',') for f in movies[6:]) for label in cards(t)),'Right must scroll beyond the first six cards'
 shot('launcher-recommendations-movies')
 for _ in range(20):key(21)
 t=tree();assert focus(t).get('text','').startswith(movies[0]['title']+','),'Left returns to first title'
 key(23);time.sleep(6);t=tree();assert any(n.get('package')=='local.krasview.tv' and n.get('content-desc')=='Смотреть' for n in t.iter('node')),'OK must open the native title detail'
 assert movies[0]['title'] in texts(t),'Must open the selected title rather than the app home'
 print('PASS: six launcher cards, D-pad access, movie filter, scrolling and native title deep link',flush=True)
 key(3);time.sleep(3);key(19);key(19);key(22);key(22);t=tree()
 assert all(any(label.startswith(f['title']+',') for f in shows+anime) for label in cards(t)),'Shows tab must contain series and anime'
 key(20);key(20);t=tree();assert any(n.get('focused')=='true' and n.get('text') in ['SmartTube','Twitch','Play Маркет','KrasView TV · проба'] for n in t.iter('node')),'Down returns to apps'
 home=run('shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME');assert 'io.freemytv/.HomeActivity' in home
 response=subprocess.run([str(adb),'-s',a.serial,'shell','content','query','--uri','content://local.krasview.tv.recommendations/items'],capture_output=True,timeout=30);denied=(response.stdout+response.stderr).decode('utf8',errors='replace');assert 'Launcher access only' in denied or 'Permission Denial' in denied,'Untrusted clients must not read the feed'
 log=run('shell','logcat','-d','-s','AndroidRuntime:E');assert 'FATAL EXCEPTION' not in log
 key(3);shot('launcher-recommendations-overview')
 print('PASS: shows/anime filter, return to app dock, Home unchanged and feed denied to untrusted caller',flush=True)
else:
 before=json.loads(values['row-series'])['signature']
 run('shell','am','start','-n','local.krasview.tv/.LauncherLinkActivity','-a','android.intent.action.VIEW','-d','krasviewtv://film/The_Mentalist');time.sleep(6);key(23);time.sleep(3);key(20);key(23);time.sleep(10)
 assert any('Менталист' in n.get('text','') for n in tree().iter('node')),'Known playable episode must open'
 key(3);time.sleep(15)
 raw=run('exec-out','cat','/data/user/0/local.krasview.tv/shared_prefs/recommendations.xml');cache={n.get('name'):n.text for n in ET.fromstring(raw)}
 assert json.loads(cache['row-series'])['signature']!=before,'Home must refresh recommendations without returning through the app home'
 assert len(cards(tree()))==6
 print('PASS: new playback refreshes the launcher feed in the background on Home',flush=True)
