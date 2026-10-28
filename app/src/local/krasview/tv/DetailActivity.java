package local.krasview.tv;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class DetailActivity extends TvActivity {
    final ExecutorService network=Executors.newFixedThreadPool(2);final Handler main=new Handler(Looper.getMainLooper());
    Api api;Api.Detail detail;Api.Film film;FrameLayout root;TextView state;LinearLayout episodeList;Dialog episodesDialog;
    String address;boolean alive=true;int seasonTicket,selectedSeasonId;View watch,bookmark;
    @Override public void onCreate(Bundle b){super.onCreate(b);api=new Api(this);address=getIntent().getStringExtra("address");root=new FrameLayout(this);root.setBackgroundColor(BG);setContentView(root);state=text("Загрузка…",20,MUTED);state.setPadding(d(32),d(100),d(32),0);root.addView(state);fetch();}
    void fetch(){network.execute(()->{try{Api.Detail data=api.detail(address);main.post(()->{if(alive){detail=data;film=new Api.Film();film.id=data.id;film.address=data.address;film.title=title();film.image=data.poster;film.year=data.year;film.section=data.section;film.genre=data.genres;for(Api.Genre tag:data.genreTags)film.genreTags.put(tag.id,tag.name);film.rating="";render();}});}catch(Exception e){main.post(()->{if(alive){state.setText("Описание не загрузилось. Нажми OK, чтобы повторить.");state.setFocusable(true);state.setClickable(true);state.setOnClickListener(v->{state.setText("Загрузка…");fetch();});state.requestFocus();}});}});}
    String title(){String value=detail.title;int split=value.indexOf(" / ");return split>0?value.substring(0,split):value;}
    void render(){
        root.removeAllViews();ScrollView page=new ScrollView(this);page.setFillViewport(true);page.setClipToPadding(false);root.addView(page,new FrameLayout.LayoutParams(-1,-1));LinearLayout flow=new LinearLayout(this);flow.setOrientation(1);page.addView(flow);
        FrameLayout hero=new FrameLayout(this);flow.addView(hero,new LinearLayout.LayoutParams(-1,d(510)));
        ImageView background=new ImageView(this);background.setScaleType(ImageView.ScaleType.CENTER_CROP);background.setAlpha(detail.background.isEmpty()?.38f:.72f);hero.addView(background,new FrameLayout.LayoutParams(-1,-1));Art.load(this,detail.background.isEmpty()?detail.poster:detail.background,background,true);
        hero.addView(new Shade(),new FrameLayout.LayoutParams(-1,-1));
        ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);poster.setClipToOutline(true);poster.setBackground(shape(PANEL,0));FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(d(228),d(342));pp.leftMargin=d(28);pp.topMargin=d(112);hero.addView(poster,pp);Art.load(this,detail.poster,poster,false);
        LinearLayout info=new LinearLayout(this);info.setOrientation(1);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(-1,-2);ip.leftMargin=d(300);ip.rightMargin=d(36);ip.topMargin=d(112);hero.addView(info,ip);
        TextView meta=text(detail.primaryMeta,18,TEXT);info.addView(meta);
        TextView name=text(title(),43,TEXT);name.setTypeface(null,1);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=d(14);info.addView(name,np);
        HorizontalScrollView genreScroll=new HorizontalScrollView(this);genreScroll.setHorizontalScrollBarEnabled(false);LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,d(42));gp.topMargin=d(18);info.addView(genreScroll,gp);LinearLayout tags=new LinearLayout(this);genreScroll.addView(tags);for(Api.Genre g:detail.genreTags){TextView chip=button(g.name);chip.setTextSize(16);chip.setSingleLine(true);chip.setContentDescription("Жанр: "+g.name);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,d(38));cp.rightMargin=d(10);tags.addView(chip,cp);chip.setOnClickListener(v->Navigation.genre(this,g.id,g.name));}
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,d(58));ap.topMargin=d(38);info.addView(actions,ap);
        watch=pill("play","Смотреть",true);actions.addView(watch,new LinearLayout.LayoutParams(d(192),-1));watch.setOnClickListener(v->watch());
        bookmark=pill(LocalLibrary.bookmarked(this,address)?"saved":"bookmark",LocalLibrary.bookmarked(this,address)?"В закладках":"В закладки",false);LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(d(194),-1);blp.leftMargin=d(16);actions.addView(bookmark,blp);bookmark.setOnClickListener(v->{boolean saved=LocalLibrary.toggle(this,film);LinearLayout box=(LinearLayout)bookmark;((IconView)box.getChildAt(0)).kind(saved?"saved":"bookmark");((TextView)box.getChildAt(1)).setText(saved?"В закладках":"В закладки");bookmark.setContentDescription(saved?"Убрать из закладок":"Добавить в закладки");});
        LinearLayout description=new LinearLayout(this);description.setOrientation(1);description.setPadding(d(28),0,d(60),d(42));flow.addView(description);
        TextView label=text("Подробно",25,MUTED);description.addView(label);TextView body=text(detail.description.isEmpty()?"Описание пока отсутствует.":detail.description,20,TEXT);body.setLineSpacing(d(5),1);body.setPadding(0,d(18),0,0);description.addView(body);
        body.setFocusable(true);body.setContentDescription("Описание: "+body.getText());body.setOnKeyListener((v,key,e)->{if(e.getAction()==0&&key==KeyEvent.KEYCODE_DPAD_UP){watch.requestFocus();page.smoothScrollTo(0,0);return true;}return false;});
        watch.setOnKeyListener((v,key,e)->{if(e.getAction()==0&&key==KeyEvent.KEYCODE_DPAD_DOWN){body.requestFocus();page.smoothScrollTo(0,d(300));return true;}return false;});
        topBar();watch.requestFocus();
    }
    View pill(String iconName,String label,boolean primary){LinearLayout box=new LinearLayout(this);box.setGravity(Gravity.CENTER);box.setFocusable(true);box.setClickable(true);box.setContentDescription(label);box.setPadding(d(16),0,d(16),0);box.setBackground(shape(primary?TEXT:0x99251e20,0));
        IconView icon=new IconView(this,iconName);icon.ink(primary?BG:TEXT);box.addView(icon,new LinearLayout.LayoutParams(d(25),d(25)));TextView name=text(label,20,primary?BG:TEXT);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.leftMargin=d(12);box.addView(name,lp);
        box.setOnFocusChangeListener((v,on)->{v.setBackground(shape(on||primary?TEXT:0x99251e20,on?0xff999192:0));icon.ink(on||primary?BG:TEXT);name.setTextColor(on||primary?BG:TEXT);v.setScaleX(on?1.035f:1);v.setScaleY(on?1.035f:1);});return box;
    }
    void topBar(){LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(d(22),d(10),d(24),d(8));FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,d(72),Gravity.TOP);root.addView(top,tp);
        FrameLayout back=iconButton("back","К каталогу",27);top.addView(back,new LinearLayout.LayoutParams(d(44),d(48)));back.setOnClickListener(v->finish());FrameLayout menu=iconButton("menu","Открыть меню",30);top.addView(menu,new LinearLayout.LayoutParams(d(48),d(48)));menu.setOnClickListener(v->Navigation.show(this));
        top.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));FrameLayout search=iconButton("search","Поиск",28);top.addView(search,new LinearLayout.LayoutParams(d(48),d(48)));search.setOnClickListener(v->Navigation.go(this,4,true));
        TextView clock=text(new java.text.SimpleDateFormat("HH:mm",new Locale("ru")).format(new Date()),31,TEXT);clock.setTypeface(null,1);clock.setPadding(d(18),0,d(14),0);top.addView(clock);TextView date=text(new java.text.SimpleDateFormat("d MMMM yyyy\nEEEE",new Locale("ru")).format(new Date()),12,TEXT);top.addView(date);
    }
    void watch(){if(detail.seasons.isEmpty()){if(detail.episodes.isEmpty()){new AlertDialog.Builder(this).setMessage("Видео пока недоступно").setPositiveButton("Закрыть",null).show();}else if(detail.episodes.size()==1)play(detail.episodes.get(0));else showEpisodes();}else showEpisodes();}
    void play(Api.Episode episode){Intent intent=new Intent(this,PlayerActivity.class);intent.putExtra("video_id",episode.id);intent.putExtra("expected_duration",episode.duration);intent.putExtra("address",address);intent.putExtra("title",title()+" — "+episodeLabel(episode));intent.putExtra("film",LocalLibrary.json(film).toString());intent.putExtra("series",!detail.seasons.isEmpty()||"series".equals(detail.section));intent.putExtra("season_id",selectedSeasonId);int[] ids=new int[detail.seasons.size()];for(int i=0;i<ids.length;i++)ids[i]=detail.seasons.get(i).id;intent.putExtra("season_ids",ids);startActivity(intent);}
    String episodeLabel(Api.Episode e){String label=e.title;if(label.startsWith(detail.title+" - "))label=label.substring(detail.title.length()+3);else label=label.replaceFirst("^.*? / .*? - ","");return label.replaceFirst("^\\d+\\s+сезон,\\s*","");}
    void showEpisodes(){
        episodesDialog=new Dialog(this,android.R.style.Theme_Material_NoActionBar);LinearLayout column=new LinearLayout(this);column.setOrientation(1);column.setBackgroundColor(BG);column.setPadding(d(30),d(24),d(30),d(24));episodesDialog.setContentView(column);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);column.addView(header);TextView back=button("←  К описанию");header.addView(back,new LinearLayout.LayoutParams(d(180),d(48)));back.setOnClickListener(v->episodesDialog.dismiss());TextView heading=text(title(),27,TEXT);heading.setPadding(d(24),0,0,0);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        HorizontalScrollView seasons=new HorizontalScrollView(this);seasons.setHorizontalScrollBarEnabled(false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,d(56));sp.topMargin=d(24);column.addView(seasons,sp);if(detail.seasons.isEmpty())seasons.setVisibility(View.GONE);LinearLayout row=new LinearLayout(this);seasons.addView(row);
        state=text("",14,MUTED);state.setPadding(0,d(12),0,d(12));column.addView(state);ScrollView scroll=new ScrollView(this);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,0,1);column.addView(scroll,ep);episodeList=new LinearLayout(this);episodeList.setOrientation(1);scroll.addView(episodeList);scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->loadEpisodeFrames());episodeList.addOnLayoutChangeListener((v,l,t,r,b,oldL,oldT,oldR,oldB)->loadEpisodeFrames());
        for(Api.Season s:detail.seasons){TextView b=button(s.title);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-1);lp.rightMargin=d(12);row.addView(b,lp);b.setOnClickListener(v->loadSeason(s));}
        episodesDialog.show();episodesDialog.getWindow().setLayout(-1,-1);episodesDialog.setOnDismissListener(v->{seasonTicket++;watch.requestFocus();});
        if(!detail.seasons.isEmpty()){row.getChildAt(0).requestFocus();loadSeason(detail.seasons.get(0));}else{state.setText("Выбери версию");renderEpisodes(detail.episodes);if(episodeList.getChildCount()>0)episodeList.getChildAt(0).requestFocus();}
    }
    void loadSeason(Api.Season season){int ticket=++seasonTicket;state.setText(season.title+" · Загрузка…");episodeList.removeAllViews();network.execute(()->{try{List<Api.Episode> items=api.season(season.id);main.post(()->{if(alive&&ticket==seasonTicket&&episodesDialog.isShowing()){selectedSeasonId=season.id;state.setText(season.title);renderEpisodes(items);}});}catch(Exception e){main.post(()->{if(alive&&ticket==seasonTicket&&episodesDialog.isShowing())state.setText("Не удалось загрузить серии. Выбери сезон ещё раз.");});}});}
    void renderEpisodes(List<Api.Episode> items){episodeList.removeAllViews();for(Api.Episode e:items){
        String duration=e.duration>0?time(e.duration*1000L):"";if(duration.startsWith("0:"))duration=duration.substring(2);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(d(8),d(8),d(18),d(8));row.setFocusable(true);row.setClickable(true);row.setBackground(shape(PANEL,0));row.setContentDescription(episodeLabel(e)+(duration.isEmpty()?"":" · "+duration));
        ImageView frame=new ImageView(this);frame.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.setBackground(shape(BG,0));frame.setClipToOutline(true);frame.setContentDescription("Превью: "+episodeLabel(e));row.addView(frame,new LinearLayout.LayoutParams(d(164),d(92)));row.setTag(e.image.isEmpty()?detail.poster:e.image);
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(1);LinearLayout.LayoutParams labelsLp=new LinearLayout.LayoutParams(0,-2,1);labelsLp.leftMargin=d(20);row.addView(labels,labelsLp);
        TextView name=text(episodeLabel(e),20,TEXT);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(name);TextView length=text(duration,14,MUTED);length.setPadding(0,d(8),0,0);labels.addView(length);
        row.setOnFocusChangeListener((v,on)->{v.setBackground(shape(on?TEXT:PANEL,0));name.setTextColor(on?BG:TEXT);length.setTextColor(on?PANEL:MUTED);});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,d(108));lp.bottomMargin=d(10);episodeList.addView(row,lp);row.setOnClickListener(v->play(e));
    }if(items.isEmpty())state.setText("Список серий пока пуст");}
    void loadEpisodeFrames(){if(episodeList==null)return;Rect visible=new Rect();for(int i=0;i<episodeList.getChildCount();i++){LinearLayout row=(LinearLayout)episodeList.getChildAt(i);ImageView frame=(ImageView)row.getChildAt(0);if(row.getLocalVisibleRect(visible)){if(frame.getTag()==null)Art.load(this,(String)row.getTag(),frame,false);}else if(frame.getTag()!=null){Art.clear(frame);}}}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getAction()==0&&e.getKeyCode()==KeyEvent.KEYCODE_MENU){Navigation.show(this);return true;}return super.dispatchKeyEvent(e);}
    @Override protected void onDestroy(){alive=false;seasonTicket++;main.removeCallbacksAndMessages(null);network.shutdownNow();super.onDestroy();}
    final class Shade extends View{final Paint p=new Paint();Shade(){super(DetailActivity.this);}protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();p.setShader(new LinearGradient(0,0,w,0,new int[]{BG,0xe6160f10,0x44160f10},new float[]{0,.45f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(new LinearGradient(0,h*.65f,0,h,new int[]{0x00160f10,BG},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);}}
}
