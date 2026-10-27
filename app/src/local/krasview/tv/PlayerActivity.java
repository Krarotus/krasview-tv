package local.krasview.tv;

import android.app.AlertDialog;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class PlayerActivity extends TvActivity implements SurfaceHolder.Callback {
    final Handler main=new Handler(Looper.getMainLooper());final ExecutorService network=Executors.newSingleThreadExecutor();
    Media.Playback playback;VideoEngine player;SurfaceView surface;FrameLayout root;LinearLayout controls,transport;ProgressBar busy;
    TextView title,state,clock,play,audio,quality,external;SeekBar timeline;FrameLayout playButton;IconView playGlyph;
    boolean alive=true,resumed,ready,prepared,completed,loading,historyRecorded,centerHeld,desiredPlaying=true;int videoId,sourceIndex,heldKey;long position,heldAt,scrubTarget=-1;
    String sourceId,positionKey;boolean sourceQualityManual;int preferredAudio=-1;final Set<Integer> failedSources=new HashSet<>();
    Runnable hide=()->{if(prepared&&player!=null&&player.isPlaying()&&heldKey==0){controls.setVisibility(View.GONE);transport.setVisibility(View.GONE);}};
    final Runnable tick=new Runnable(){public void run(){if(!alive)return;if(prepared&&player!=null){try{int pos=heldKey==0?player.getCurrentPosition():(int)Math.max(0,scrubTarget),duration=player.getDuration();if(timeline.getMax()!=Math.max(1,duration))timeline.setMax(Math.max(1,duration));if(timeline.getProgress()!=pos)timeline.setProgress(pos);String stamp=time(pos)+"  /  "+time(duration);if(!stamp.contentEquals(clock.getText()))clock.setText(stamp);if(pos>1000){position=pos;savePosition();}}catch(IllegalStateException ignored){}}main.postDelayed(this,500);}};
    final Runnable seekRepeat=new Runnable(){public void run(){if(heldKey==0||!prepared||player==null)return;int direction=heldKey==KeyEvent.KEYCODE_DPAD_LEFT||heldKey==KeyEvent.KEYCODE_MEDIA_REWIND?-1:1;applySeek(direction*SeekRamp.step(SystemClock.uptimeMillis()-heldAt));main.postDelayed(this,SeekRamp.INTERVAL_MS);}};
    final Runnable longCenter=()->{centerHeld=true;settings();};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);videoId=getIntent().getIntExtra("video_id",0);address=getIntent().getStringExtra("address");sourceId=getIntent().getStringExtra("playback_source");if(sourceId==null)sourceId="krasview";positionKey=sourceId.equals("krasview")?"video-"+videoId:"source:"+sourceId+":"+getIntent().getStringExtra("position_key");position=getSharedPreferences("positions",0).getLong(positionKey,0);
        root=new FrameLayout(this);root.setBackgroundColor(0xff000000);root.setFocusable(true);setContentView(root);surface=new SurfaceView(this);root.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));surface.getHolder().addCallback(this);
        busy=new ProgressBar(this);FrameLayout.LayoutParams bl=new FrameLayout.LayoutParams(d(38),d(38),Gravity.CENTER);root.addView(busy,bl);
        state=text("",14,MUTED);play=text("",14,TEXT);audio=text("",14,TEXT);quality=text("",14,TEXT);external=text("",14,TEXT);
        controls=new LinearLayout(this);controls.setOrientation(1);controls.setPadding(d(28),d(12),d(28),d(14));controls.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0xd9000000}));root.addView(controls,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        title=text(getIntent().getStringExtra("title"),16,TEXT);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);controls.addView(title);
        timeline=new SeekBar(this);timeline.setFocusable(false);timeline.setFocusableInTouchMode(false);timeline.setEnabled(false);timeline.setProgressTintList(android.content.res.ColorStateList.valueOf(TEXT));timeline.setThumbTintList(android.content.res.ColorStateList.valueOf(TEXT));controls.addView(timeline,new LinearLayout.LayoutParams(-1,d(28)));
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int value,boolean fromUser){if(fromUser&&prepared){seek(value);showControls();}}public void onStartTrackingTouch(SeekBar s){main.removeCallbacks(hide);}public void onStopTrackingTouch(SeekBar s){showControls();}});
        clock=text("",13,TEXT);controls.addView(clock);
        transport=new LinearLayout(this);transport.setGravity(Gravity.CENTER);FrameLayout.LayoutParams tr=new FrameLayout.LayoutParams(-2,d(64),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);tr.bottomMargin=d(108);root.addView(transport,tr);
        FrameLayout rewind=transportButton("rewind","Назад на 10 секунд");transport.addView(rewind,transportParams());rewind.setOnClickListener(v->relative(-10000));
        playButton=transportButton("play","Пауза / продолжить");playGlyph=(IconView)playButton.getChildAt(0);transport.addView(playButton,transportParams());playButton.setOnClickListener(v->{if(prepared)toggle();});
        FrameLayout forward=transportButton("forward","Вперёд на 10 секунд");transport.addView(forward,transportParams());forward.setOnClickListener(v->relative(10000));
        playButton.requestFocus();fetch();
    }
    String address;
    LinearLayout.LayoutParams transportParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(d(62),d(62));p.leftMargin=p.rightMargin=d(12);return p;}
    FrameLayout transportButton(String kind,String label){FrameLayout box=iconButton(kind,label,29);box.setBackground(circle(0x88000000));IconView icon=(IconView)box.getChildAt(0);box.setOnFocusChangeListener((v,on)->{v.setBackground(circle(on?TEXT:0x88000000));icon.ink(on?BG:TEXT);});return box;}
    android.graphics.drawable.GradientDrawable circle(int color){android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setShape(android.graphics.drawable.GradientDrawable.OVAL);g.setColor(color);return g;}
    void fetch(){
        sourceIndex=0;failedSources.clear();sourceQualityManual=false;loading=true;busy.setVisibility(View.VISIBLE);state.setText("Получаю видео…");play.setText("Загрузка…");external.setVisibility(View.GONE);
        network.execute(()->{
            try{
                Media.Playback result;
                // Non-exported activity: this public sample is used only by device QA.
                if(getIntent().getBooleanExtra("qa_sample",false)){
                    result=new Media.Playback();result.title="Проверка встроенного плеера";result.id=0;
                    result.sources.add(new Media.Source("https://download.blender.org/durian/trailer/sintel_trailer-480p.mp4","Тестовое видео"));
                }else result=SourceRegistry.find(this,sourceId).resolve(SourceJson.query(getIntent().getStringExtra("query")),sourceId.equals("krasview")?Integer.toString(videoId):getIntent().getStringExtra("source_reference"));
                final Media.Playback value=result;
                main.post(()->{if(!alive)return;loading=false;playback=value;sourceIndex=Math.max(0,Math.min(value.defaultSource,value.sources.size()-1));if(!value.title.isEmpty())title.setText(value.title);quality.setEnabled(value.sources.size()>1);prepare();});
            }catch(Exception e){main.post(()->{if(!alive)return;loading=false;
                if(e instanceof Api.AccessRequired)error("Сервис не предоставил видео нашему приложению. Встроенный просмотр этого видео пока недоступен.");
                else if(e instanceof Api.AuthRequired)error("Сеанс завершился. Вернись в каталог и войди в аккаунт.");
                else error(sourceId.equals("lampac")&&e instanceof java.io.IOException?e.getMessage():"Не удалось получить видео. Проверь соединение и попробуй ещё раз.");
            });}
        });
    }
    void prepare(){
        if(!alive||!resumed||!ready||playback==null)return;
        if(player!=null&&prepared)desiredPlaying=player.isPlaying();final boolean continuePlaying=desiredPlaying;
        release();prepared=false;completed=false;timeline.setEnabled(false);busy.setVisibility(View.VISIBLE);
        try{
            player=new VideoEngine(this,surface,playback.audioNames,playback.headers,new VideoEngine.Listener(){
                public void ready(){if(!alive||player==null)return;int duration=player.getDuration(),expected=getIntent().getIntExtra("expected_duration",0);if(expected<=0)expected=playback.duration;
                    if(MovieChoices.shorterThanExpected(expected,duration)){android.util.Log.w("KrasViewTV","Rejected short source; expected_s="+expected+" actual_ms="+duration);if(!nextSource())error("Источник вернул короткий ролик вместо выбранного видео. Вернись к списку версий и попробуй ещё раз.");return;}
                    prepared=true;busy.setVisibility(View.GONE);timeline.setEnabled(true);if(duration>0&&position>duration-10000){position=0;player.seekTo(0);}if(preferredAudio>=0)for(VideoEngine.Choice track:player.audioChoices())if(track.ordinal==preferredAudio){player.selectAudio(track);break;}if(continuePlaying)player.start();updatePlayIcon();
                    if(!historyRecorded){LocalLibrary.watched(PlayerActivity.this,getIntent().getStringExtra("film")==null?"{}":getIntent().getStringExtra("film"));historyRecorded=true;}
                    showControls();playButton.requestFocus();android.util.Log.i("KrasViewTV","Player prepared and started; duration_ms="+duration);
                }
                public void size(int w,int h){fit(w,h);}
                public void buffering(boolean value){if(alive)busy.setVisibility(value?View.VISIBLE:View.GONE);}
                public void ended(){completed=true;position=0;savePosition();updatePlayIcon();showControls();main.removeCallbacks(hide);}
                public void firstFrame(){android.util.Log.i("KrasViewTV","First video frame rendered");}
                public void failed(){if(!alive)return;if(prepared&&player!=null)position=player.getCurrentPosition();if(!nextSource())error("Не удалось воспроизвести видео. Проверь соединение и попробуй ещё раз.");}
            });
            player.open(playback.sources.get(sourceIndex),position);
        }catch(Exception e){error("Не удалось открыть видеопоток. Попробуй ещё раз.");}
    }
    boolean nextSource(){failedSources.add(sourceIndex);for(int i=0;i<playback.sources.size();i++)if(!failedSources.contains(i)){sourceIndex=i;prepare();return true;}return false;}
    void updatePlayIcon(){boolean playing=player!=null&&player.isPlaying();playGlyph.kind(playing?"pause":"play");playButton.setContentDescription(completed?"Смотреть сначала":playing?"Пауза":"Продолжить");}
    void fit(int w,int h){if(w<=0||h<=0)return;int rw=root.getWidth(),rh=root.getHeight();if(rw<=0||rh<=0)return;float scale=Math.min(rw/(float)w,rh/(float)h);surface.setLayoutParams(new FrameLayout.LayoutParams((int)(w*scale),(int)(h*scale),Gravity.CENTER));}
    void error(String message){stopSeek();release();loading=false;busy.setVisibility(View.GONE);main.removeCallbacks(hide);new AlertDialog.Builder(this).setTitle("Не удалось открыть видео").setMessage(message).setPositiveButton("Повторить",(dialog,w)->fetch()).setNegativeButton("Назад",(dialog,w)->finish()).setOnCancelListener(dialog->finish()).show();}
    void toggle(){if(!prepared||player==null)return;if(completed){player.seekTo(0);completed=false;player.start();}else if(player.isPlaying())player.pause();else player.start();desiredPlaying=player.isPlaying();updatePlayIcon();showControls();}
    void relative(int amount){if(prepared&&player!=null)seek(player.getCurrentPosition()+amount);showControls();}
    void seek(int to){if(!prepared||player==null)return;try{int value=Math.max(0,Math.min(to,Math.max(0,player.getDuration()-1000)));player.seekTo(value);position=value;timeline.setProgress(value);clock.setText(time(value)+"  /  "+time(player.getDuration()));savePosition();}catch(IllegalStateException ignored){}}
    void qualities(){if(!prepared||player==null)return;if(playback.selectableSources){sourceQualities();return;}List<VideoEngine.Choice> tracks=player.videoChoices();if(tracks.isEmpty())return;String[] names=new String[tracks.size()+1];names[0]="Авто";for(int i=0;i<tracks.size();i++)names[i+1]=tracks.get(i).label;main.removeCallbacks(hide);
        AlertDialog picker=new AlertDialog.Builder(this).setTitle("Качество видео").setSingleChoiceItems(names,player.qualitySelection(tracks),(dialog,index)->{if(player!=null)player.selectVideo(index==0?null:tracks.get(index-1));dialog.dismiss();showControls();}).setNegativeButton("Закрыть",(dialog,w)->showControls()).setOnCancelListener(dialog->showControls()).create();picker.show();picker.getListView().requestFocus();picker.getListView().setSelection(player.qualitySelection(tracks));
    }
    void sourceQualities(){ArrayList<Integer> indexes=new ArrayList<>();ArrayList<String> names=new ArrayList<>();names.add("Авто");for(int i=0;i<playback.sources.size();i++)if(!playback.sources.get(i).label.equals("Авто")){indexes.add(i);names.add(playback.sources.get(i).label);}int selected=sourceQualityManual?indexes.indexOf(sourceIndex)+1:0;main.removeCallbacks(hide);AlertDialog picker=new AlertDialog.Builder(this).setTitle("Качество видео").setSingleChoiceItems(names.toArray(new String[0]),selected,(dialog,index)->{int next=index==0?playback.defaultSource:indexes.get(index-1);sourceQualityManual=index>0;dialog.dismiss();if(next!=sourceIndex){failedSources.clear();sourceIndex=next;prepare();}else showControls();}).setNegativeButton("Закрыть",(dialog,w)->showControls()).setOnCancelListener(dialog->showControls()).create();picker.show();picker.getListView().requestFocus();picker.getListView().setSelection(Math.max(0,selected));}
    void audioTracks(){if(!prepared||player==null)return;List<VideoEngine.Choice> tracks=player.audioChoices();if(tracks.isEmpty()){Toast.makeText(this,"Отдельных звуковых дорожек не найдено",Toast.LENGTH_SHORT).show();return;}String[] names=new String[tracks.size()];int selected=0;for(int i=0;i<tracks.size();i++){names[i]=tracks.get(i).label;if(player.selected(tracks.get(i)))selected=i;}main.removeCallbacks(hide);
        AlertDialog picker=new AlertDialog.Builder(this).setTitle("Звуковая дорожка").setSingleChoiceItems(names,selected,(dialog,index)->{if(player!=null){preferredAudio=tracks.get(index).ordinal;player.selectAudio(tracks.get(index));}dialog.dismiss();showControls();}).setNegativeButton("Закрыть",(dialog,w)->showControls()).setOnCancelListener(dialog->showControls()).create();picker.show();picker.getListView().requestFocus();picker.getListView().setSelection(selected);
    }
    void openOriginal(){if(address==null)return;try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://atoto.ru/item/"+Uri.encode(address)));i.setPackage("ru.video.krasview");startActivity(i);}catch(ActivityNotFoundException e){Toast.makeText(this,"Обычный KrasView не установлен",Toast.LENGTH_SHORT).show();}}

    void savePosition(){if(videoId>0||!sourceId.equals("krasview"))getSharedPreferences("positions",0).edit().putLong(positionKey,position).apply();}
    void showControls(){controls.setVisibility(View.VISIBLE);transport.setVisibility(View.VISIBLE);main.removeCallbacks(hide);if(prepared&&player!=null&&player.isPlaying()&&heldKey==0)main.postDelayed(hide,3200);}
    void settings(){if(!prepared)return;main.removeCallbacks(hide);String[] entries={"Звуковая дорожка","Качество видео"};AlertDialog picker=new AlertDialog.Builder(this).setTitle("Настройки просмотра").setItems(entries,(dialog,index)->{if(index==0)audioTracks();else qualities();}).setNegativeButton("Закрыть",(dialog,w)->showControls()).setOnCancelListener(dialog->showControls()).create();picker.show();picker.getListView().requestFocus();picker.getListView().setSelection(0);}
    void startSeek(int key){if(!prepared||player==null)return;if(heldKey==key)return;main.removeCallbacks(seekRepeat);heldKey=key;heldAt=SystemClock.uptimeMillis();scrubTarget=player.getCurrentPosition();int direction=key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_MEDIA_REWIND?-1:1;applySeek(direction*10000);showControls();main.postDelayed(seekRepeat,SeekRamp.INTERVAL_MS);}
    void applySeek(int delta){if(!prepared||player==null)return;scrubTarget=Math.max(0,Math.min(scrubTarget+delta,Math.max(0,player.getDuration()-1000)));seek((int)scrubTarget);}
    void stopSeek(){heldKey=0;scrubTarget=-1;main.removeCallbacks(seekRepeat);if(alive&&prepared)showControls();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){int key=e.getKeyCode();boolean seekKey=key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_MEDIA_REWIND||key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
        if(seekKey){if(e.getAction()==KeyEvent.ACTION_DOWN)startSeek(key);else if(e.getAction()==KeyEvent.ACTION_UP&&heldKey==key)stopSeek();return true;}
        if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){centerHeld=false;main.postDelayed(longCenter,650);}else if(e.getAction()==KeyEvent.ACTION_UP){main.removeCallbacks(longCenter);if(!centerHeld)toggle();}return true;}
        if(e.getAction()==KeyEvent.ACTION_DOWN){if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){toggle();return true;}if(key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_DPAD_UP){settings();return true;}if(key==KeyEvent.KEYCODE_DPAD_DOWN){showControls();playButton.requestFocus();return true;}}
        return super.dispatchKeyEvent(e);
    }
    @Override public void onBackPressed(){if(prepared&&controls.getVisibility()==View.VISIBLE){controls.setVisibility(View.GONE);transport.setVisibility(View.GONE);main.removeCallbacks(hide);}else finish();}
    void release(){if(player!=null){try{if(prepared&&!completed){position=player.getCurrentPosition();savePosition();}}catch(IllegalStateException ignored){}player.release();player=null;}prepared=false;}
    @Override public void surfaceCreated(SurfaceHolder h){ready=true;prepare();}
    @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder h){ready=false;stopSeek();release();}
    @Override protected void onResume(){super.onResume();resumed=true;main.removeCallbacks(tick);main.post(tick);prepare();}
    @Override protected void onPause(){resumed=false;if(player!=null&&prepared)desiredPlaying=player.isPlaying();stopSeek();main.removeCallbacks(hide);main.removeCallbacks(longCenter);main.removeCallbacks(tick);release();super.onPause();}
    @Override protected void onDestroy(){alive=false;main.removeCallbacksAndMessages(null);release();network.shutdownNow();super.onDestroy();}
}
