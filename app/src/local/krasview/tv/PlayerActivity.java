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
import androidx.media3.ui.SubtitleView;
import androidx.media3.ui.CaptionStyleCompat;

public class PlayerActivity extends TvActivity implements SurfaceHolder.Callback {
    final Handler main=new Handler(Looper.getMainLooper());final ExecutorService network=Executors.newSingleThreadExecutor();
    Api api;Api.Playback playback;VideoEngine player;SurfaceView surface;SubtitleView captions;FrameLayout root;LinearLayout controls;ProgressBar busy;
    TextView title,clock,seekBadge;SeekBar timeline;String selectedSubtitle;Api.NextEpisode nextEpisode;boolean nextLoading,nextFailed,nextPrompted,seekWasPlaying;
    long scrubOrigin;
    boolean alive=true,resumed,ready,prepared,completed,loading,historyRecorded,centerHeld,desiredPlaying=true;int videoId,sourceIndex,heldKey;long position,heldAt,scrubTarget=-1;
    final Runnable hideSeekBadge=()->{if(heldKey==0&&seekBadge!=null)seekBadge.setVisibility(View.GONE);};
    Runnable hide=()->{if(prepared&&player!=null&&player.isPlaying()&&heldKey==0){controls.setVisibility(View.GONE);captions.setBottomPaddingFraction(.08f);}};
    final Runnable tick=new Runnable(){public void run(){if(!alive)return;if(prepared&&player!=null){try{int pos=heldKey==0?player.getCurrentPosition():(int)Math.max(0,scrubTarget),duration=player.getDuration();if(timeline.getMax()!=Math.max(1,duration))timeline.setMax(Math.max(1,duration));if(timeline.getProgress()!=pos)timeline.setProgress(pos);String stamp=time(pos)+"  /  "+time(duration);if(!stamp.contentEquals(clock.getText()))clock.setText(stamp);if(heldKey==0&&!completed&&pos>1000){position=pos;savePosition();}}catch(IllegalStateException ignored){}}main.postDelayed(this,500);}};
    final Runnable seekRepeat=new Runnable(){public void run(){if(heldKey==0||!prepared||player==null)return;int direction=heldKey==KeyEvent.KEYCODE_DPAD_LEFT||heldKey==KeyEvent.KEYCODE_MEDIA_REWIND?-1:1;applySeek(direction*SeekRamp.step(SystemClock.uptimeMillis()-heldAt));main.postDelayed(this,SeekRamp.INTERVAL_MS);}};
    final Runnable longCenter=()->{centerHeld=true;settings();};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);api=new Api(this);videoId=getIntent().getIntExtra("video_id",0);address=getIntent().getStringExtra("address");position=getSharedPreferences("positions",0).getLong("video-"+videoId,0);
        root=new FrameLayout(this);root.setBackgroundColor(0xff000000);root.setFocusable(true);setContentView(root);surface=new SurfaceView(this);root.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));surface.getHolder().addCallback(this);
        captions=new SubtitleView(this);captions.setFractionalTextSize(.045f);captions.setStyle(new CaptionStyleCompat(0xffffffff,0x00000000,0x00000000,CaptionStyleCompat.EDGE_TYPE_OUTLINE,0xff000000,null));captions.setFocusable(false);root.addView(captions,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        busy=new ProgressBar(this);FrameLayout.LayoutParams bl=new FrameLayout.LayoutParams(d(38),d(38),Gravity.CENTER);root.addView(busy,bl);
        controls=new LinearLayout(this);controls.setOrientation(1);controls.setPadding(d(28),d(12),d(28),d(14));controls.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0xd9000000}));root.addView(controls,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        title=text(getIntent().getStringExtra("title"),16,TEXT);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);controls.addView(title);
        timeline=new SeekBar(this);timeline.setFocusable(false);timeline.setFocusableInTouchMode(false);timeline.setEnabled(false);timeline.setProgressTintList(android.content.res.ColorStateList.valueOf(TEXT));timeline.setThumbTintList(android.content.res.ColorStateList.valueOf(TEXT));controls.addView(timeline,new LinearLayout.LayoutParams(-1,d(28)));
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int value,boolean fromUser){if(fromUser&&prepared){seek(value);showControls();}}public void onStartTrackingTouch(SeekBar s){main.removeCallbacks(hide);}public void onStopTrackingTouch(SeekBar s){showControls();}});
        LinearLayout timeRow=new LinearLayout(this);timeRow.setGravity(Gravity.CENTER_VERTICAL);controls.addView(timeRow);clock=text("",13,TEXT);timeRow.addView(clock);TextView hint=text("↑ Настройки",12,MUTED);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-2,-2);hp.leftMargin=d(18);timeRow.addView(hint,hp);
        seekBadge=text("",24,TEXT);seekBadge.setTypeface(null,1);seekBadge.setGravity(Gravity.CENTER);seekBadge.setPadding(d(20),d(10),d(20),d(10));seekBadge.setBackground(shape(0xe6251e20,0));seekBadge.setVisibility(View.GONE);FrameLayout.LayoutParams badgeLp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);badgeLp.bottomMargin=d(126);root.addView(seekBadge,badgeLp);
        root.requestFocus();fetch();
    }
    String address;
    void fetch(){
        sourceIndex=0;loading=true;nextEpisode=null;nextLoading=false;nextFailed=false;nextPrompted=false;busy.setVisibility(View.VISIBLE);
        network.execute(()->{
            try{
                Api.Playback result;
                // Non-exported activity: this public sample is used only by device QA.
                if(getIntent().getBooleanExtra("qa_sample",false)){
                    result=new Api.Playback();result.title="Проверка встроенного плеера";result.id=0;
                    result.sources.add(new Api.Source("https://download.blender.org/durian/trailer/sintel_trailer-480p.mp4","Тестовое видео"));
                }else result=api.playback(videoId);
                final Api.Playback value=result;
                main.post(()->{if(!alive)return;loading=false;playback=value;if(!value.title.isEmpty())title.setText(value.title);prepare();loadNextEpisode(value);});
            }catch(Exception e){main.post(()->{if(!alive)return;loading=false;
                if(e instanceof Api.AccessRequired)error("Сервис не предоставил видео нашему приложению. Встроенный просмотр этого видео пока недоступен.");
                else if(e instanceof Api.AuthRequired)error("Сеанс завершился. Вернись в каталог и войди в аккаунт.");
                else error("Не удалось получить видео. Проверь соединение и попробуй ещё раз.");
            });}
        });
    }
    void prepare(){
        if(!alive||!resumed||!ready||playback==null)return;
        if(player!=null&&prepared)desiredPlaying=player.isPlaying();final boolean continuePlaying=desiredPlaying;
        release();prepared=false;completed=false;timeline.setEnabled(false);busy.setVisibility(View.VISIBLE);
        try{
            player=new VideoEngine(this,surface,captions,playback.audioNames,playback.subtitles,new VideoEngine.Listener(){
                public void ready(){if(!alive||player==null)return;int duration=player.getDuration(),expected=getIntent().getIntExtra("expected_duration",0);if(expected<=0)expected=playback.duration;
                    if(MovieChoices.shorterThanExpected(expected,duration)){android.util.Log.w("KrasViewTV","Rejected short source; expected_s="+expected+" actual_ms="+duration);if(sourceIndex+1<playback.sources.size()){sourceIndex++;prepare();}else error("Источник вернул короткий ролик вместо выбранного видео. Вернись к списку версий и попробуй ещё раз.");return;}
                    prepared=true;busy.setVisibility(View.GONE);timeline.setEnabled(true);if(duration>0&&position>duration-10000){position=0;player.seekTo(0);}if(selectedSubtitle!=null)for(VideoEngine.Choice track:player.textChoices())if(track.label.equals(selectedSubtitle)){player.selectText(track);break;}if(continuePlaying)player.start();updatePlaybackState();
                    if(!historyRecorded){LocalLibrary.watched(PlayerActivity.this,getIntent().getStringExtra("film")==null?"{}":getIntent().getStringExtra("film"));historyRecorded=true;}
                    showControls();root.requestFocus();android.util.Log.i("KrasViewTV","Player prepared and started; duration_ms="+duration);
                }
                public void size(int w,int h){fit(w,h);}
                public void buffering(boolean value){if(alive)busy.setVisibility(value?View.VISIBLE:View.GONE);}
                public void ended(){completed=true;position=0;savePosition();updatePlaybackState();showControls();main.removeCallbacks(hide);offerNextEpisode();}
                public void firstFrame(){android.util.Log.i("KrasViewTV","First video frame rendered");}
                public void failed(){if(!alive)return;if(prepared&&player!=null)position=player.getCurrentPosition();if(sourceIndex+1<playback.sources.size()){sourceIndex++;prepare();}else error("Не удалось воспроизвести видео. Проверь соединение и попробуй ещё раз.");}
            });
            player.open(playback.sources.get(sourceIndex).url,position);
        }catch(Exception e){error("Не удалось открыть видеопоток. Попробуй ещё раз.");}
    }
    void updatePlaybackState(){boolean playing=player!=null&&player.isPlaying();root.setContentDescription(completed?"Смотреть сначала":playing?"Пауза":"Продолжить");}
    void fit(int w,int h){if(w<=0||h<=0)return;int rw=root.getWidth(),rh=root.getHeight();if(rw<=0||rh<=0)return;float scale=Math.min(rw/(float)w,rh/(float)h);surface.setLayoutParams(new FrameLayout.LayoutParams((int)(w*scale),(int)(h*scale),Gravity.CENTER));captions.setLayoutParams(new FrameLayout.LayoutParams((int)(w*scale),(int)(h*scale),Gravity.CENTER));}
    void error(String message){finishSeek(false);release();loading=false;busy.setVisibility(View.GONE);main.removeCallbacks(hide);new AlertDialog.Builder(this).setTitle("Не удалось открыть видео").setMessage(message).setPositiveButton("Повторить",(dialog,w)->fetch()).setNegativeButton("Назад",(dialog,w)->finish()).setOnCancelListener(dialog->finish()).show();}
    void toggle(){if(!prepared||player==null)return;stopSeek();if(completed){player.seekTo(0);completed=false;player.start();}else if(player.isPlaying())player.pause();else player.start();desiredPlaying=player.isPlaying();updatePlaybackState();showControls();}
    void relative(int amount){if(prepared&&player!=null)seek(player.getCurrentPosition()+amount);showControls();}
    void seek(int to){if(!prepared||player==null)return;try{int value=Math.max(0,Math.min(to,Math.max(0,player.getDuration()-1000)));player.seekTo(value);position=value;timeline.setProgress(value);clock.setText(time(value)+"  /  "+time(player.getDuration()));savePosition();}catch(IllegalStateException ignored){}}
    void qualities(){if(!prepared||player==null)return;List<VideoEngine.Choice> tracks=player.videoChoices();if(tracks.isEmpty())return;String[] names=new String[tracks.size()+1];names[0]="Авто";for(int i=0;i<tracks.size();i++)names[i+1]=tracks.get(i).label;main.removeCallbacks(hide);
        AlertDialog picker=new AlertDialog.Builder(this).setTitle("Качество видео").setSingleChoiceItems(names,player.qualitySelection(tracks),(dialog,index)->{if(player!=null)player.selectVideo(index==0?null:tracks.get(index-1));dialog.dismiss();showControls();}).setNegativeButton("Закрыть",(dialog,w)->showControls()).setOnCancelListener(dialog->showControls()).create();picker.show();picker.getListView().requestFocus();picker.getListView().setSelection(player.qualitySelection(tracks));
    }
    void audioTracks(){if(!prepared||player==null)return;List<VideoEngine.Choice> tracks=player.audioChoices();if(tracks.isEmpty()){Toast.makeText(this,"Отдельных звуковых дорожек не найдено",Toast.LENGTH_SHORT).show();return;}String[] names=new String[tracks.size()];int selected=0;for(int i=0;i<tracks.size();i++){names[i]=tracks.get(i).label;if(player.selected(tracks.get(i)))selected=i;}main.removeCallbacks(hide);
        AlertDialog picker=new AlertDialog.Builder(this).setTitle("Звуковая дорожка").setSingleChoiceItems(names,selected,(dialog,index)->{if(player!=null)player.selectAudio(tracks.get(index));dialog.dismiss();showControls();}).setNegativeButton("Закрыть",(dialog,w)->showControls()).setOnCancelListener(dialog->showControls()).create();picker.show();picker.getListView().requestFocus();picker.getListView().setSelection(selected);
    }
    void openOriginal(){if(address==null)return;try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://atoto.ru/item/"+Uri.encode(address)));i.setPackage("ru.video.krasview");startActivity(i);}catch(ActivityNotFoundException e){Toast.makeText(this,"Обычный KrasView не установлен",Toast.LENGTH_SHORT).show();}}

    void savePosition(){if(videoId>0)getSharedPreferences("positions",0).edit().putLong("video-"+videoId,position).apply();}
    void showControls(){controls.setVisibility(View.VISIBLE);captions.setBottomPaddingFraction(.18f);main.removeCallbacks(hide);if(prepared&&player!=null&&player.isPlaying()&&heldKey==0)main.postDelayed(hide,3200);}
    void subtitles(){if(!prepared||player==null)return;List<VideoEngine.Choice> tracks=player.textChoices();if(tracks.isEmpty()){new AlertDialog.Builder(this).setTitle("Субтитры").setMessage("Для этого видео субтитры не предоставлены.").setPositiveButton("Закрыть",(dialog,w)->showControls()).setOnCancelListener(dialog->showControls()).show();return;}String[] names=new String[tracks.size()+1];names[0]="Выключены";int selected=0;for(int i=0;i<tracks.size();i++){names[i+1]=tracks.get(i).label;if(player.textEnabled()&&player.selected(tracks.get(i)))selected=i+1;}main.removeCallbacks(hide);AlertDialog picker=new AlertDialog.Builder(this).setTitle("Субтитры").setSingleChoiceItems(names,selected,(dialog,index)->{if(player!=null){selectedSubtitle=index==0?null:tracks.get(index-1).label;player.selectText(index==0?null:tracks.get(index-1));}dialog.dismiss();showControls();}).setNegativeButton("Закрыть",(dialog,w)->showControls()).setOnCancelListener(dialog->showControls()).create();picker.show();picker.getListView().requestFocus();picker.getListView().setSelection(selected);}
    void settings(){if(!prepared)return;stopSeek();main.removeCallbacks(hide);String[] entries=nextEpisode==null&&!nextLoading&&!nextFailed?new String[]{"Звуковая дорожка","Качество видео","Субтитры"}:new String[]{"Звуковая дорожка","Качество видео","Субтитры","Следующая серия"};AlertDialog picker=new AlertDialog.Builder(this).setTitle("Настройки просмотра").setItems(entries,(dialog,index)->{if(index==0)audioTracks();else if(index==1)qualities();else if(index==2)subtitles();else playNextEpisode();}).setNegativeButton("Закрыть",(dialog,w)->showControls()).setOnCancelListener(dialog->showControls()).create();picker.show();picker.getListView().requestFocus();picker.getListView().setSelection(0);}
    void loadNextEpisode(Api.Playback current){
        if(!getIntent().getBooleanExtra("series",false))return;nextLoading=true;nextFailed=false;
        final int season=getIntent().getIntExtra("season_id",0);final int[] seasons=getIntent().getIntArrayExtra("season_ids");
        network.execute(()->{Api.NextEpisode result=null;boolean failed=false;try{result=api.nextEpisode(current,season,seasons);}catch(Exception ignored){failed=true;}final Api.NextEpisode value=result;final boolean error=failed;main.post(()->{if(!alive||playback!=current)return;nextLoading=false;nextFailed=error;nextEpisode=value;android.util.Log.i("KrasViewTV","Next episode lookup; available="+(value!=null)+" failed="+error);if(completed)offerNextEpisode();});});
    }
    void offerNextEpisode(){if(!alive||!resumed||nextEpisode==null||nextPrompted)return;nextPrompted=true;new AlertDialog.Builder(this).setTitle("Серия закончилась").setMessage(nextEpisode.episode.title).setPositiveButton("Следующая серия",(dialog,w)->playNextEpisode()).setNegativeButton("Остаться",(dialog,w)->showControls()).show();}
    void playNextEpisode(){if(nextEpisode==null){if(nextFailed&&playback!=null)loadNextEpisode(playback);Toast.makeText(this,nextLoading?"Загружаем следующую серию…":"Следующая серия не найдена",Toast.LENGTH_SHORT).show();return;}stopSeek();savePosition();Api.Episode e=nextEpisode.episode;Intent intent=new Intent(this,PlayerActivity.class);if(getIntent().getExtras()!=null)intent.putExtras(getIntent().getExtras());intent.putExtra("video_id",e.id);intent.putExtra("expected_duration",e.duration);intent.putExtra("title",e.title);intent.putExtra("season_id",nextEpisode.seasonId);startActivity(intent);finish();}
    void startSeek(int key){if(!prepared||player==null)return;if(heldKey==key)return;stopSeek();heldKey=key;heldAt=SystemClock.uptimeMillis();scrubOrigin=scrubTarget=player.getCurrentPosition();seekWasPlaying=player.isPlaying();player.pause();main.removeCallbacks(hideSeekBadge);int direction=key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_MEDIA_REWIND?-1:1;applySeek(direction*10000);showControls();main.postDelayed(seekRepeat,SeekRamp.INTERVAL_MS);}
    void applySeek(int delta){if(!prepared||player==null)return;scrubTarget=SeekRamp.target(scrubTarget,delta,player.getDuration());timeline.setProgress((int)scrubTarget);clock.setText(time(scrubTarget)+"  /  "+time(player.getDuration()));seekBadge.setText(SeekRamp.offset(scrubTarget-scrubOrigin));seekBadge.setVisibility(View.VISIBLE);}
    void stopSeek(){finishSeek(true);}
    void finishSeek(boolean commit){boolean active=heldKey!=0;heldKey=0;main.removeCallbacks(seekRepeat);if(active&&prepared&&player!=null){if(commit){seek((int)scrubTarget);completed=false;android.util.Log.i("KrasViewTV","Seek committed; delta_ms="+(scrubTarget-scrubOrigin));}if(seekWasPlaying&&resumed)player.start();desiredPlaying=seekWasPlaying;updatePlaybackState();}scrubTarget=-1;if(active){main.removeCallbacks(hideSeekBadge);main.postDelayed(hideSeekBadge,1000);}if(alive&&prepared)showControls();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){int key=e.getKeyCode();boolean seekKey=key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_MEDIA_REWIND||key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
        if(seekKey){if(e.getAction()==KeyEvent.ACTION_DOWN)startSeek(key);else if(e.getAction()==KeyEvent.ACTION_UP&&heldKey==key)stopSeek();return true;}
        if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){centerHeld=false;main.postDelayed(longCenter,650);}else if(e.getAction()==KeyEvent.ACTION_UP){main.removeCallbacks(longCenter);if(!centerHeld)toggle();}return true;}
        if(e.getAction()==KeyEvent.ACTION_DOWN){if(key==KeyEvent.KEYCODE_MEDIA_NEXT){playNextEpisode();return true;}if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){stopSeek();toggle();return true;}if(key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_DPAD_UP){settings();return true;}if(key==KeyEvent.KEYCODE_DPAD_DOWN){showControls();root.requestFocus();return true;}}
        return super.dispatchKeyEvent(e);
    }
    @Override public void onBackPressed(){if(heldKey!=0){finishSeek(false);seekBadge.setVisibility(View.GONE);return;}if(prepared&&controls.getVisibility()==View.VISIBLE){controls.setVisibility(View.GONE);captions.setBottomPaddingFraction(.08f);main.removeCallbacks(hide);}else finish();}
    void release(){if(player!=null){try{if(prepared&&!completed){position=player.getCurrentPosition();savePosition();}}catch(IllegalStateException ignored){}player.release();player=null;}prepared=false;}
    @Override public void surfaceCreated(SurfaceHolder h){ready=true;prepare();}
    @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder h){ready=false;stopSeek();release();}
    @Override protected void onResume(){super.onResume();resumed=true;main.removeCallbacks(tick);main.post(tick);prepare();}
    @Override protected void onPause(){resumed=false;if(player!=null&&prepared)desiredPlaying=player.isPlaying();stopSeek();main.removeCallbacks(hide);main.removeCallbacks(longCenter);main.removeCallbacks(tick);release();super.onPause();}
    @Override protected void onDestroy(){alive=false;main.removeCallbacksAndMessages(null);release();network.shutdownNow();super.onDestroy();}
}
