"""Non-destructive device QA. App must show the Boss Baby detail page initially."""
import argparse
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--serial', required=True)
args = p.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
adb = root.parent / 'tv-tools/platform-tools/adb.exe'
out = root / 'out/qa'
out.mkdir(parents=True, exist_ok=True)
def run(*cmd):
    return subprocess.check_output([str(adb), '-s', args.serial, *cmd], timeout=30).decode('utf-8', errors='replace')
def key(code):
    run('shell', 'input', 'keyevent', str(code))
def snapshot(name):
    run('shell', 'uiautomator', 'dump', '/sdcard/krasview-qa.xml')
    data = run('shell', 'cat', '/sdcard/krasview-qa.xml')
    (out / (name + '.xml')).write_text(data, encoding='utf-8')
    return ET.fromstring(data)
def texts(tree):
    return [n.get('text', '') for n in tree.iter('node') if n.get('text')]
def stamp(tree):
    for s in texts(tree):
        if re.fullmatch(r'[\d:]+  /  [\d:]+', s):
            result = 0
            for part in s.split('  /  ')[0].split(':'):
                result = result * 60 + int(part)
            return result
    raise AssertionError('Player clock absent: ' + repr(texts(tree)))

detail = snapshot('detail-final')
assert 'Смотреть' in texts(detail)
key(22)  # Watch -> bookmark
key(23)  # Save
saved = snapshot('detail-bookmarked')
if 'В закладках' not in texts(saved):
    key(23)
    saved = snapshot('detail-bookmarked')
assert 'В закладках' in texts(saved)
print('PASS: D-pad bookmark toggle')
key(21)
key(23)  # Watch
time.sleep(10)
key(85)  # Pause for stable timestamps and idle UI tree
time.sleep(1)
before = snapshot('player-paused')
base = stamp(before)
assert not any(s in texts(before) for s in ['Воспроизведение', 'Пауза', 'Продолжить'])
clickable = [n for n in before.iter('node') if n.get('clickable') == 'true']
assert len(clickable) == 3, [(n.get('text'), n.get('content-desc')) for n in clickable]
print('PASS: three player controls, no playback status captions')
key(22)
time.sleep(1)
right = stamp(snapshot('player-right'))
print('Short right:', base, '->', right, 'delta', right - base)
assert abs(right - base - 10) <= 1, 'Right must seek 10 seconds'
key(21)
time.sleep(1)
left = stamp(snapshot('player-left'))
assert abs(left - right + 10) <= 1, 'Left must seek 10 seconds'
print('PASS: short right/left seek 10 seconds with visible HUD')
print(run('shell', 'app_process', '-Djava.class.path=/data/local/tmp/krasview-qa-hold.dex', '/system/bin', 'DpadHold', '22', '2600').strip())
time.sleep(1)
held = stamp(snapshot('player-held'))
print('Held right:', left, '->', held, 'delta', held - left)
assert 90 <= held - left <= 130, 'Hold should progress from 10 to 20 sec steps'
print('PASS: paced hold acceleration')
key(82)  # player menu
menu = snapshot('player-settings')
assert 'Звуковая дорожка' in texts(menu)
key(4)
key(4)
key(4)  # detail
print('PASS: player settings shortcut; returned to detail')
