"""Recommendation shelves on the rooted Slimbox QA device; uses only our app's own data.
Does not change history unless --check-refresh is explicitly passed (plays Ralph briefly).
No account credentials or history contents are printed or saved by this test.
"""
import argparse,pathlib,subprocess,time,json,re,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--check-refresh',action='store_true');a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];adb=root.parent/'tv-tools/platform-tools/adb.exe';out=root/'out/qa';out.mkdir(parents=True,exist_ok=True)
def run(*args):return subprocess.check_output([str(adb),'-s',a.serial,*args],timeout=40).decode('utf8',errors='replace')
def key(k):run('shell','input','keyevent',str(k))
def prefs(name):
 xml=run('exec-out','cat','/data/user/0/local.krasview.tv/shared_prefs/'+name+'.xml');return {n.get('name'):n.text for n in ET.fromstring(xml)}
def library():return json.loads(prefs('library').get('history','[]'))
def rows():
 data=prefs('recommendations');return {s:json.loads(data['row-'+s]) for s in ['movie','series','anime'] if 'row-'+s in data}
def tree():run('shell','uiautomator','dump','/sdcard/recommendations-qa.xml');return ET.fromstring(run('shell','cat','/sdcard/recommendations-qa.xml'))
def focus(t):return next((n.get('content-desc','') for n in t.iter('node') if n.get('focused')=='true'),'')
def shot(name):run('shell','screencap','-p','/sdcard/recommendations-qa.png');run('pull','/sdcard/recommendations-qa.png',str(out/(name+'.png')))
def launch():
 run('shell','am','force-stop','local.krasview.tv');run('shell','am','start','-n','local.krasview.tv/.MainActivity');time.sleep(12)
def title(f):return f['title']+', '+f.get('year','')
def validate(data,history):
 seen={f['address'] for f in history}
 for section,row in data.items():
  items=row['items'];keys=[f['address'] for f in items]
  assert 0<len(items)<=12 and len(set(keys))==len(keys)
  assert not seen.intersection(keys),'Watched titles must be excluded'
  assert all(f['section']==section for f in items),'Type must match the shelf'
  popular=set(row['signature'].rsplit('|',12)[1:])
  assert not popular.intersection(keys),'Visible popular titles must be excluded'
run('shell','logcat','-c');launch();data=rows();history=library();validate(data,history)
assert len(data)==3,'QA history must contain films, series and anime'
t=tree();assert 'Популярные фильмы' in [n.get('text') for n in t.iter('node')]
for section in ['movie','series','anime']:
 key(20);t=tree();assert focus(t) in [title(f) for f in data[section]['items']],section
 if section=='movie':shot('recommendations-movies')
 if section=='series':shot('recommendations-series')
 if section!='anime':key(20)
print('PASS: six alternating popular/personal shelves, real cards, D-pad focus, watched/popular exclusions and type isolation',flush=True)
before={s:(r['at'],r['signature']) for s,r in data.items()};run('shell','logcat','-c');launch();after=rows()
assert before=={s:(r['at'],r['signature']) for s,r in after.items()},'Warm cache must be reused'
log=run('shell','logcat','-d','-s','KrasViewTV:I','AndroidRuntime:E');assert log.count('Recommendations cache hit')>=3
assert 'FATAL EXCEPTION' not in log
print('PASS: recommendations survive restart and reuse cache without recalculation',flush=True)
if a.check_refresh:
 run('shell','am','start','-n','local.krasview.tv/.DetailActivity','--es','address','WreckItRalph');time.sleep(5);key(23);time.sleep(2);key(23);time.sleep(9);key(85);key(4);key(4);key(4);key(4);time.sleep(12)
 changed=library();assert changed[0]['address']=='WreckItRalph' and changed[0].get('genre') and changed[0].get('genres'),'New history must retain genres'
 refreshed=rows();validate(refreshed,changed);assert refreshed['movie']['signature']!=data['movie']['signature'],'Returning from playback must invalidate recommendations'
 assert 'Не удалось загрузить рекомендации' not in [n.get('text') for n in tree().iter('node')]
 print('PASS: new playback saves genres, refreshes recommendations on return and excludes the watched title',flush=True)
