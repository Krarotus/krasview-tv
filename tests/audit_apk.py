"""Check the shipped APK for accidental ad SDK or diagnostic activity inclusion."""
import pathlib,sys,zipfile,json
apk=pathlib.Path(sys.argv[1] if len(sys.argv)>1 else 'KrasView-TV.apk')
with zipfile.ZipFile(apk) as archive:
    dex=b''.join(archive.read(n) for n in archive.namelist() if n.endswith('.dex'))
    forbidden={
        'AppLovin':b'Lcom/applovin/',
        'Google Mobile Ads':b'Lcom/google/android/gms/ads/',
        'Yandex Ads':b'Lcom/yandex/mobile/ads/',
        'Unity Ads':b'Lcom/unity3d/ads/',
        'IronSource':b'Lcom/ironsource/',
    }
    found=[name for name,signature in forbidden.items() if signature in dex]
    assert not found,'Unexpected ad SDK: '+', '.join(found)
    assert b'Llocal/krasview/tv/FrameProbeActivity;' not in dex,'Temporary diagnostics must not ship'
    assert b'LDpadHold;' not in dex,'Input injection QA helper must not ship'
    assert b'Llocal/krasview/tv/PlayerActivity;' in dex,'Built-in player is missing'
    print(json.dumps({'advertising_sdks_found':found,'temporary_frame_probe_included':False,'native_player_included':True}))
