"""Android TV catalog/filter integration, using the real service and D-pad."""
import argparse,pathlib,subprocess,time,re,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--filter-only',action='store_true');a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];out=root/'out/qa';out.mkdir(parents=True,exist_ok=True);adb=root.parent/'tv-tools/platform-tools/adb.exe'
def run(*cmd):return subprocess.check_output([str(adb),'-s',a.serial,*cmd],timeout=35).decode('utf8',errors='replace')
def key(k):run('shell','input','keyevent',str(k))
def tree(name):
 run('shell','uiautomator','dump','/sdcard/krasview-qa.xml');s=run('shell','cat','/sdcard/krasview-qa.xml');(out/(name+'.xml')).write_text(s,encoding='utf8');return ET.fromstring(s)
def texts(t):return [n.get('text') for n in t.iter('node') if n.get('text')]
def section(n):run('shell','am','start','-n','local.krasview.tv/.MainActivity','--activity-clear-top','--activity-single-top','--ei','section',str(n));time.sleep(3)
def shot(name):run('shell','screencap','-p','/sdcard/krasview-qa.png');run('pull','/sdcard/krasview-qa.png',str(out/(name+'.png')))
def focus(t):return next((n for n in t.iter('node') if n.get('focused')=='true'),None)
def visible_cards(t):return [n for n in t.iter('node') if n.get('clickable')=='true' and re.search(r', \d{4}$',n.get('content-desc',''))]
if not a.filter_only:
 section(0);time.sleep(3);t=tree('popular-home');assert 'Популярные фильмы' in texts(t);assert not any(s in texts(t) for s in ['Новые фильмы','Новые сериалы']);shot('popular-home')
 for mode in [1,2,3,5]:
  section(mode);t=tree('popular-section-'+str(mode));assert visible_cards(t);assert focus(t) in visible_cards(t)
 print('PASS: popular home and populated movie/series/cartoon/anime sections, card focus',flush=True)
section(0);key(19);key(22);key(23);time.sleep(2);t=tree('filter-open');assert 'Фильтр' in texts(t)
key(23);key(20);key(23)  # Type: movies
key(20);key(23)  # Genres
t=tree('filter-genres');rows=[n for n in t.iter('node') if n.get('class')=='android.widget.CheckedTextView'];target=next(i for i,n in enumerate(rows) if n.get('text')=='Боевик')
for _ in range(target):key(20)
key(23);key(22);t=tree('filter-genre-done');assert focus(t).get('text').lower()=='готово';key(23)
key(20);key(23)  # Years
for _ in range(8):key(20)
key(23);key(20);key(20)  # Sort -> Show
t=tree('filter-ready');assert focus(t).get('text')=='Показать';shot('filter-ready');key(23);time.sleep(3)
t=tree('filter-results');assert 'Боевик · 2000–2009' in texts(t);cards=visible_cards(t);assert cards
for card in cards:
 year=int(card.get('content-desc').rsplit(', ',1)[1]);assert 2000<=year<=2009
 labels=[n.get('text') for n in card.iter('node') if n.get('text')]
 if labels:assert any('Боевик' in label for label in labels)  # Offscreen descendants are omitted by Android.
shot('filter-results');print('PASS: filter selected entirely by D-pad; movie/action/2000-2009 results match',flush=True)
run('shell','am','start','-n','local.krasview.tv/.DetailActivity','--es','address','The_Boss_Baby');time.sleep(3)
t=tree('genre-tags');shot('genre-tags');chip=next(n for n in t.iter('node') if n.get('content-desc')=='Жанр: Комедия');assert chip.get('focusable')=='true'
coords=list(map(int,re.findall(r'\d+',chip.get('bounds'))));run('shell','input','tap',str((coords[0]+coords[2])//2),str((coords[1]+coords[3])//2));time.sleep(3)
t=tree('genre-link-results');assert 'Комедия' in texts(t);assert visible_cards(t)
print('PASS: focusable genre tag opens actual comedy catalog',flush=True)
section(0)
