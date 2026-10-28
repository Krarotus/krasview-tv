package local.krasview.tv;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.speech.RecognizerIntent;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends TvActivity {
    final Handler handler=new Handler(Looper.getMainLooper());
    final ExecutorService network=Executors.newFixedThreadPool(3);
    PosterViewport posters;
    Api api;FrameLayout root,drawer,menuButton,searchButton;LinearLayout body,gridRows;ScrollView scroller;
    TextView pageTitle,status,account,timeView,dateView,moreButton;EditText search;
    final ArrayList<View> menu=new ArrayList<>();
    final ArrayList<ArrayList<View>> cardRows=new ArrayList<>();
    final ArrayList<Api.Film> films=new ArrayList<>();final ArrayList<Shelf> shelves=new ArrayList<>();
    final String[] labels={"Главная","Фильмы","Сериалы","Мультфильмы","Каталог","Аниме","Избранное","История","Расписание"};
    final String[] icons={"home","movie","tv","cartoon","catalog","anime","bookmark","history","calendar"};
    boolean alive=true,loading,drawerOpen,searchMode,more;int selectedMenu,page=1,generation,recommendationGeneration,lastRow,lastColumn;
    String historyStamp="",query="",sort="top";long backAt,libraryRevision;AlertDialog pinDialog;int pinGeneration;
    CatalogFilter filter=new CatalogFilter();List<Api.Genre> genreOptions;boolean filterLoading;
    static final class Shelf {String name,section,sort,message="";boolean recommendation,failed;FrameLayout content;List<Api.Film> items=new ArrayList<>(),candidates=new ArrayList<>();Shelf(String n,String s,String o){name=n;section=s;sort=o;}}
    final Runnable clockTick=new Runnable(){public void run(){if(!alive)return;Date now=new Date();String clock=new java.text.SimpleDateFormat("HH:mm",new Locale("ru")).format(now),date=new java.text.SimpleDateFormat("d MMMM yyyy\nEEEE",new Locale("ru")).format(now);if(!clock.contentEquals(timeView.getText()))timeView.setText(clock);if(!date.contentEquals(dateView.getText()))dateView.setText(date);handler.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);api=new Api(this);selectedMenu=Math.max(0,Math.min(8,getIntent().getIntExtra("section",0)));readGenre(getIntent());account=text("Аккаунт",14,TEXT);search=new EditText(this);build();handler.post(clockTick);libraryRevision=LocalLibrary.revision(this);historyStamp=LocalLibrary.historyStamp(this);load(false);if(getIntent().getBooleanExtra("open_search",false))handler.post(()->editSearch());if(getIntent().getBooleanExtra("open_filter",false))handler.post(()->openFilter());}
    void build(){
        root=new FrameLayout(this);root.setBackgroundColor(BG);setContentView(root);
        body=new LinearLayout(this);body.setOrientation(1);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,-1);bp.topMargin=d(74);root.addView(body,bp);
        status=text("",16,MUTED);status.setPadding(d(28),d(8),d(28),0);body.addView(status);
        scroller=new ScrollView(this);scroller.setClipToPadding(false);scroller.setPadding(0,0,0,d(24));body.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));gridRows=new LinearLayout(this);gridRows.setOrientation(1);scroller.addView(gridRows);posters=new PosterViewport(this,scroller);
        drawer=new FrameLayout(this);drawer.setBackgroundColor(BG);drawer.setVisibility(View.GONE);FrameLayout.LayoutParams dp=new FrameLayout.LayoutParams(d(236),-1,Gravity.LEFT);dp.topMargin=d(74);root.addView(drawer,dp);
        ScrollView menuScroll=new ScrollView(this);menuScroll.setVerticalScrollBarEnabled(false);drawer.addView(menuScroll,new FrameLayout.LayoutParams(-1,-1));LinearLayout items=new LinearLayout(this);items.setOrientation(1);items.setPadding(d(18),d(14),d(12),d(18));menuScroll.addView(items);
        for(int i=0;i<labels.length;i++){
            final int index=i;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(d(12),0,d(8),0);row.setFocusable(true);row.setClickable(true);row.setContentDescription(labels[i]);
            IconView icon=new IconView(this,icons[i]);row.addView(icon,new LinearLayout.LayoutParams(d(28),d(28)));TextView label=text(labels[i],20,TEXT);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.leftMargin=d(16);row.addView(label,lp);
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,d(52));rp.bottomMargin=d(2);items.addView(row,rp);menu.add(row);
            row.setOnFocusChangeListener((v,on)->{v.setBackground(shape(on?TEXT:BG,0));icon.ink(on?BG:TEXT);label.setTextColor(on?BG:TEXT);});
            row.setOnClickListener(v->{selectedMenu=index;searchMode=false;query="";sort="top";filter=new CatalogFilter();lastRow=lastColumn=0;closeDrawer(false);load(false);});
            row.setOnKeyListener((v,key,e)->{if(e.getAction()==0&&key==KeyEvent.KEYCODE_DPAD_RIGHT){closeDrawer(true);return true;}return false;});
            if(i==4){LinearLayout fr=new LinearLayout(this);fr.setGravity(Gravity.CENTER_VERTICAL);fr.setPadding(d(12),0,d(8),0);fr.setFocusable(true);fr.setClickable(true);fr.setContentDescription("Фильтр");IconView fi=new IconView(this,"filter");fr.addView(fi,new LinearLayout.LayoutParams(d(28),d(28)));TextView fl=text("Фильтр",20,TEXT);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.leftMargin=d(16);fr.addView(fl,fp);LinearLayout.LayoutParams rp2=new LinearLayout.LayoutParams(-1,d(52));rp2.bottomMargin=d(2);items.addView(fr,rp2);fr.setOnFocusChangeListener((v,on)->{v.setBackground(shape(on?TEXT:BG,0));fi.ink(on?BG:TEXT);fl.setTextColor(on?BG:TEXT);});fr.setOnClickListener(v->{closeDrawer(false);openFilter();});}
        }
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(d(22),d(10),d(22),d(8));FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,d(72),Gravity.TOP);root.addView(top,tp);
        FrameLayout back=iconButton("back","Назад",27);top.addView(back,new LinearLayout.LayoutParams(d(42),d(48)));back.setOnClickListener(v->onBackPressed());
        menuButton=iconButton("menu","Открыть меню",30);top.addView(menuButton,new LinearLayout.LayoutParams(d(48),d(48)));menuButton.setOnClickListener(v->{if(drawerOpen)closeDrawer(true);else openDrawer();});
        pageTitle=text("Главная",25,TEXT);pageTitle.setPadding(d(16),0,0,0);pageTitle.setSingleLine(true);pageTitle.setEllipsize(TextUtils.TruncateAt.END);top.addView(pageTitle,new LinearLayout.LayoutParams(0,-2,1));
        searchButton=iconButton("search","Поиск",28);top.addView(searchButton,new LinearLayout.LayoutParams(d(50),d(48)));searchButton.setOnClickListener(v->editSearch());
        FrameLayout filterButton=iconButton("filter","Фильтр",27);top.addView(filterButton,new LinearLayout.LayoutParams(d(50),d(48)));filterButton.setOnClickListener(v->openFilter());
        FrameLayout profile=iconButton("person","Аккаунт KrasView",27);top.addView(profile,new LinearLayout.LayoutParams(d(50),d(48)));profile.setOnClickListener(v->account());
        timeView=text("",31,TEXT);timeView.setTypeface(null,1);timeView.setPadding(d(18),0,d(12),0);top.addView(timeView);
        dateView=text("",12,TEXT);top.addView(dateView,new LinearLayout.LayoutParams(d(130),-2));menuButton.requestFocus();
    }
    void openDrawer(){if(drawerOpen)return;drawerOpen=true;drawer.setVisibility(View.VISIBLE);body.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);body.animate().translationX(d(236)).setDuration(160).start();menu.get(selectedMenu).requestFocus();}
    void closeDrawer(boolean focus){drawerOpen=false;drawer.setVisibility(View.GONE);body.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);body.animate().translationX(0).setDuration(160).start();if(focus)focusResults();}
    void focusResults(){int row=Math.min(lastRow,cardRows.size()-1);if(row>=0&&cardRows.get(row).isEmpty()){row=nextCardRow(row,1);if(row<0)row=nextCardRow(Math.min(lastRow,cardRows.size()-1),-1);}if(row>=0){ArrayList<View> cards=cardRows.get(row);cards.get(Math.min(lastColumn,cards.size()-1)).requestFocus();}else searchButton.requestFocus();}
    int nextCardRow(int from,int direction){for(int r=from+direction;r>=0&&r<cardRows.size();r+=direction)if(!cardRows.get(r).isEmpty())return r;return -1;}
    void setSearch(String value){query=value;search.setText(value);}
    void hideKeyboard(){if(search!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);}
    void editSearch(){
        if(drawerOpen)closeDrawer(false);search=new EditText(this);search.setSingleLine(true);search.setText(query);search.setHint("Название фильма или сериала");search.setTextColor(TEXT);search.setTextSize(20);search.setPadding(d(22),d(16),d(22),d(16));search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Поиск").setView(search).setPositiveButton("Найти",(x,w)->{hideKeyboard();submitSearch();}).setNeutralButton("Голос",(x,w)->voice()).setNegativeButton("Отмена",null).create();
        search.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){hideKeyboard();dialog.dismiss();submitSearch();return true;}return false;});dialog.show();search.requestFocus();search.setSelection(search.length());
    }
    void submitSearch(){query=search.getText().toString().trim();searchMode=!query.isEmpty();selectedMenu=4;lastRow=lastColumn=0;load(false);}
    void voice(){try{Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU");i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Что найти в KrasView?");startActivityForResult(i,10);}catch(ActivityNotFoundException e){Toast.makeText(this,"Голосовой ввод недоступен",Toast.LENGTH_SHORT).show();editSearch();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==10&&result==RESULT_OK&&data!=null){ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(words!=null&&!words.isEmpty()){setSearch(words.get(0));submitSearch();}}}
    void readGenre(Intent intent){int id=intent.getIntExtra("genre_id",0);if(id>0){filter.genres.clear();filter.genres.put(id,intent.getStringExtra("genre_name")==null?"Жанр":intent.getStringExtra("genre_name"));}}
    void openFilter(){
        if(drawerOpen)closeDrawer(false);if(filterLoading)return;
        if(genreOptions==null){filterLoading=true;ProgressDialog progress=new ProgressDialog(this);progress.setMessage("Загружаю жанры…");progress.setCancelable(true);progress.show();network.execute(()->{try{List<Api.Genre> options=api.genres();handler.post(()->{filterLoading=false;if(!alive)return;genreOptions=options;boolean show=progress.isShowing();progress.dismiss();if(show)openFilter();});}catch(Exception e){handler.post(()->{filterLoading=false;if(!alive)return;progress.dismiss();Toast.makeText(this,"Не удалось загрузить жанры. Попробуй ещё раз.",Toast.LENGTH_LONG).show();});}});return;}
        CatalogFilter draft=filter.copy();draft.sort=sort;draft.section=selectedMenu==1?"movie":selectedMenu==2?"series":selectedMenu==5?"anime":"all";if(selectedMenu==3)draft.genres.put(9558,"Анимационный");
        FilterDialog.show(this,draft,genreOptions,value->{filter=value;sort=value.sort;selectedMenu=value.section.equals("movie")?1:value.section.equals("series")?2:value.section.equals("anime")?5:4;lastRow=lastColumn=0;load(false);});
    }
    boolean home(){return selectedMenu==0&&!searchMode;}
    void load(boolean append){
        if(append&&loading)return;final long loadStarted=SystemClock.uptimeMillis();int ticket=++generation;loading=true;if(!append){page=1;films.clear();shelves.clear();posters.clear();gridRows.removeAllViews();cardRows.clear();scroller.scrollTo(0,0);}else page++;
        pageTitle.setText(searchMode?"Поиск: "+query:labels[selectedMenu]);status.setVisibility(View.VISIBLE);status.setText("Загрузка…");
        if(!searchMode&&(selectedMenu==6||selectedMenu==7)){films.clear();films.addAll(LocalLibrary.list(this,selectedMenu==6?"bookmarks":"history"));loading=false;renderGrid();return;}
        if(!searchMode&&selectedMenu==8){loading=false;empty("Расписание пока недоступно","К главной",()->{selectedMenu=0;load(false);});return;}
        if(!api.signedIn()){loading=false;empty("Войди в аккаунт, чтобы открыть каталог","Войти",()->login());return;}
        final int mode=selectedMenu,requestedPage=page;final String requestedQuery=query,requestedSort=sort;final boolean isHome=home();final CatalogFilter requestedFilter=filter.copy();
        network.execute(()->{try{
            if(isHome){
                java.io.File cacheFile=new java.io.File(getCacheDir(),"home-v1.bin");HomeCache.Snapshot saved=HomeCache.read(cacheFile);
                if(saved!=null){ArrayList<Shelf> cached=homeRows(readHome(saved));handler.post(()->applyHome(cached,ticket,loadStarted,true));}
                List<List<Api.Film>> fresh=new ArrayList<>();for(String section:HomeCache.SECTIONS)fresh.add(api.catalog(section,"",1,"top").items);
                try{HomeCache.write(cacheFile,writeHome(fresh));}catch(java.io.IOException ignored){android.util.Log.w("KrasViewTV","Home cache could not be saved");}
                ArrayList<Shelf> rows=homeRows(fresh);handler.post(()->{if(!alive||ticket!=generation)return;loading=false;applyHome(rows,ticket,loadStarted,false);});
            }else{String section=mode==1?"movie":mode==2?"series":mode==5?"anime":"all";Api.Page result=api.catalog(section,requestedQuery,requestedPage,requestedSort,mode==3,requestedFilter);
                handler.post(()->{if(!alive||ticket!=generation)return;loading=false;int oldCount=films.size();films.addAll(result.items);more=result.more;renderGrid();if(append){lastRow=oldCount/6;lastColumn=oldCount%6;handler.post(()->focusResults());}});}
        }catch(Exception e){handler.post(()->{if(!alive||ticket!=generation)return;loading=false;if(append)page=Math.max(1,page-1);status.setText("Не удалось загрузить каталог");if(e instanceof Api.AuthRequired){api.logout();empty("Нужен вход в аккаунт","Войти",()->login());}else if(isHome&&!shelves.isEmpty()){status.setVisibility(View.VISIBLE);status.setText("Не удалось обновить главную. Показаны сохранённые подборки.");if(!LocalLibrary.historyStamp(this).equals(historyStamp))refreshRecommendations();}else if(films.isEmpty()&&shelves.isEmpty())empty("Каталог не загрузился","Повторить",()->load(false));});}});
    }
    static List<List<Api.Film>> readHome(HomeCache.Snapshot snapshot){List<List<Api.Film>> rows=new ArrayList<>();for(List<HomeCache.Item> saved:snapshot.rows){List<Api.Film> row=new ArrayList<>();for(HomeCache.Item item:saved){Api.Film f=new Api.Film();f.id=item.id;f.address=item.address;f.title=item.title;f.image=item.image;f.year=item.year;f.section=item.section;f.genre=item.genre;f.rating="";row.add(f);}rows.add(row);}return rows;}
    static HomeCache.Snapshot writeHome(List<List<Api.Film>> rows){HomeCache.Snapshot result=new HomeCache.Snapshot();result.savedAt=System.currentTimeMillis();for(List<Api.Film> row:rows){List<HomeCache.Item> saved=new ArrayList<>();for(Api.Film f:row.subList(0,Math.min(100,row.size()))){HomeCache.Item item=new HomeCache.Item();item.id=f.id;item.address=f.address;item.title=f.title;item.image=f.image;item.year=f.year;item.section=f.section;item.genre=f.genre;saved.add(item);}result.rows.add(saved);}return result;}
    static ArrayList<Shelf> homeRows(List<List<Api.Film>> data){ArrayList<Shelf> rows=new ArrayList<>();for(int i=0;i<3;i++){String section=HomeCache.SECTIONS[i];Shelf popular=new Shelf(i==0?"Популярные фильмы":i==1?"Популярные сериалы":"Популярное аниме",section,"top");List<Api.Film> items=data.get(i);popular.items.addAll(items.subList(0,Math.min(12,items.size())));Shelf rec=new Shelf(i==0?"Рекомендуем фильмы":i==1?"Рекомендуем сериалы":"Рекомендуем аниме",section,"top");rec.recommendation=true;rec.message="Подбираем по истории просмотров…";rec.candidates.addAll(items);rows.add(popular);rows.add(rec);}return rows;}
    static boolean sameCatalog(List<Api.Film> a,List<Api.Film> b){if(a.size()!=b.size())return false;for(int i=0;i<a.size();i++){Api.Film x=a.get(i),y=b.get(i);if(x.id!=y.id||!Objects.equals(x.address,y.address)||!Objects.equals(x.title,y.title)||!Objects.equals(x.image,y.image)||!Objects.equals(x.year,y.year)||!Objects.equals(x.genre,y.genre))return false;}return true;}
    void applyHome(ArrayList<Shelf> rows,int ticket,long started,boolean cached){if(!alive||ticket!=generation||!home())return;status.setVisibility(View.GONE);boolean changed=false,first=shelves.isEmpty();if(first){shelves.addAll(rows);renderHome();changed=true;}else for(int i=0;i<rows.size();i+=2){Shelf popular=shelves.get(i),rec=shelves.get(i+1);if(!sameCatalog(rec.candidates,rows.get(i+1).candidates)){popular.items.clear();popular.items.addAll(rows.get(i).items);rec.candidates.clear();rec.candidates.addAll(rows.get(i+1).candidates);renderShelfItems(popular,i);changed=true;}}
        if(changed)refreshRecommendations();if(first)root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){root.getViewTreeObserver().removeOnPreDrawListener(this);android.util.Log.i("KrasViewTV","Home ready; cached="+cached+" elapsed_ms="+(SystemClock.uptimeMillis()-started));return true;}});
    }
    void empty(String message,String action,Runnable run){status.setVisibility(View.GONE);posters.clear();gridRows.removeAllViews();cardRows.clear();LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(d(32),d(70),d(32),0);gridRows.addView(box);TextView label=text(message,25,TEXT);box.addView(label);TextView b=button(action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(d(250),d(52));lp.topMargin=d(22);box.addView(b,lp);b.setOnClickListener(v->run.run());}
    void renderHome(){final int requestedRow=lastRow,requestedColumn=lastColumn;moreButton=null;status.setVisibility(View.GONE);posters.clear();gridRows.removeAllViews();cardRows.clear();
        for(int r=0;r<shelves.size();r++){Shelf shelf=shelves.get(r);final int rowIndex=cardRows.size();cardRows.add(new ArrayList<>());
            LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(d(28),d(18),d(28),d(14));gridRows.addView(header);TextView name=text(shelf.name,25,TEXT);header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            if(shelf.recommendation){TextView note=text("По истории просмотров",13,MUTED);header.addView(note);}else{TextView extra=button("Ещё");header.addView(extra,new LinearLayout.LayoutParams(d(76),d(38)));extra.setOnClickListener(v->{selectedMenu=shelf.section.equals("movie")?1:shelf.section.equals("series")?2:shelf.section.equals("anime")?5:4;sort=shelf.sort;lastRow=lastColumn=0;load(false);});}
            shelf.content=new FrameLayout(this);gridRows.addView(shelf.content,new LinearLayout.LayoutParams(-1,-2));renderShelfItems(shelf,rowIndex);
        }
        handler.post(()->{if(!drawerOpen){lastRow=requestedRow;lastColumn=requestedColumn;focusResults();}});
    }
    void renderShelfItems(Shelf shelf,int rowIndex){View anchor=getCurrentFocus();int selectedColumn=lastColumn;boolean restore=shelf.content.hasFocus();Object selectedTag=restore&&anchor!=null?anchor.getTag():null;int[] before=new int[2];if(anchor!=null)anchor.getLocationOnScreen(before);shelf.content.removeAllViews();ArrayList<View> rowCards=cardRows.get(rowIndex);rowCards.clear();
        if(shelf.items.isEmpty()){LinearLayout box=new LinearLayout(this);box.setGravity(Gravity.CENTER_VERTICAL);box.setPadding(d(28),d(8),d(28),d(24));TextView info=text(shelf.message.isEmpty()?"Список пока пуст":shelf.message,16,MUTED);box.addView(info,new LinearLayout.LayoutParams(0,-2,1));shelf.content.addView(box,new FrameLayout.LayoutParams(-1,-2));if(shelf.failed){TextView retry=button("Повторить");box.addView(retry,new LinearLayout.LayoutParams(d(130),d(44)));rowCards.add(retry);retry.setOnFocusChangeListener((v,on)->{v.setBackground(shape(on?TEXT:PANEL,0));retry.setTextColor(on?BG:TEXT);if(on){lastRow=rowIndex;lastColumn=0;}});wireCardNavigation(retry,rowIndex,0);retry.setOnClickListener(v->refreshRecommendations());}}
        else{HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setPadding(d(28),d(8),d(28),d(20));shelf.content.addView(scroll);LinearLayout row=new LinearLayout(this);row.setClipChildren(false);scroll.addView(row);
            for(int i=0;i<shelf.items.size();i++){View card=card(shelf.items.get(i),rowIndex,i,d(188),true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(d(188),-2);lp.rightMargin=d(18);row.addView(card,lp);rowCards.add(card);}}
        if(restore&&!drawerOpen){if(selectedTag!=null)for(int i=0;i<rowCards.size();i++)if(selectedTag.equals(rowCards.get(i).getTag())){selectedColumn=i;break;}lastRow=rowIndex;lastColumn=selectedColumn;focusResults();}else if(anchor!=null&&anchor.isAttachedToWindow()){anchor.requestFocus();scroller.post(()->{if(anchor==getCurrentFocus()&&anchor.isAttachedToWindow()){int[] after=new int[2];anchor.getLocationOnScreen(after);scroller.scrollBy(0,after[1]-before[1]);}});}
    }
    void refreshRecommendations(){if(!home()||shelves.isEmpty())return;historyStamp=LocalLibrary.historyStamp(this);final List<Api.Film> history=LocalLibrary.list(this,"history");final int request=++recommendationGeneration,ticket=generation;
        for(int index=0;index<shelves.size();index++){Shelf shelf=shelves.get(index);if(!shelf.recommendation)continue;final int rowIndex=index;final List<Api.Film> popular=new ArrayList<>(shelves.get(index-1).items);shelf.failed=false;Set<String> watched=new HashSet<>();for(Api.Film f:history)watched.add(f.address);for(Api.Film f:popular)watched.add(f.address);shelf.items.removeIf(f->watched.contains(f.address));shelf.message="Подбираем по истории просмотров…";renderShelfItems(shelf,rowIndex);
            network.execute(()->{Recommendations.Result result;try{result=new Recommendations(this,api).load(shelf.section,history,shelf.candidates,popular);}catch(Exception e){result=new Recommendations.Result();result.failed=true;result.message="Не удалось загрузить рекомендации. Попробуй ещё раз.";}final Recommendations.Result value=result;
                handler.post(()->{if(!alive||ticket!=generation||request!=recommendationGeneration||!home())return;shelf.items.clear();shelf.items.addAll(value.items);shelf.message=value.message;shelf.failed=value.failed;renderShelfItems(shelf,rowIndex);});});
        }
    }
    void renderGrid(){status.setVisibility(!home()&&selectedMenu!=6&&selectedMenu!=7&&filter.active()?View.VISIBLE:View.GONE);if(status.getVisibility()==View.VISIBLE)status.setText(filter.summary());posters.clear();gridRows.removeAllViews();cardRows.clear();moreButton=null;
        if(films.isEmpty()){String message=selectedMenu==6?"Пока нет закладок":selectedMenu==7?"История появится после просмотра":searchMode?"Ничего не найдено":"Здесь пока пусто";empty(message,searchMode?"Изменить запрос":"К главной",()->{if(searchMode)editSearch();else{selectedMenu=0;load(false);}});return;}
        int columns=6;int width=(getResources().getDisplayMetrics().widthPixels-d(56+5*18))/columns;
        TextView label=text(searchMode?"Результаты поиска":labels[selectedMenu],25,TEXT);label.setPadding(d(28),d(18),0,d(20));gridRows.addView(label);
        for(int start=0;start<films.size();start+=columns){int rowIndex=cardRows.size();ArrayList<View> rowCards=new ArrayList<>();cardRows.add(rowCards);LinearLayout row=new LinearLayout(this);row.setPadding(d(28),d(8),d(28),d(24));row.setClipChildren(false);gridRows.addView(row);
            for(int col=0;col<columns&&start+col<films.size();col++){View card=card(films.get(start+col),rowIndex,col,width,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,-2);lp.rightMargin=d(18);row.addView(card,lp);rowCards.add(card);}}
        if(more&&selectedMenu!=6&&selectedMenu!=7){moreButton=button("Показать ещё");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(d(250),d(50));lp.leftMargin=d(28);gridRows.addView(moreButton,lp);moreButton.setOnClickListener(v->load(true));}
        handler.post(()->{if(!drawerOpen)focusResults();});
    }
    View card(Api.Film film,int rowIndex,int column,int width,boolean horizontal){
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(d(3),d(3),d(3),d(3));box.setFocusable(true);box.setClickable(true);box.setContentDescription(film.title+", "+film.year);box.setTag(film.address);
        FrameLayout art=new FrameLayout(this);art.setPadding(d(2),d(2),d(2),d(2));art.setBackground(shape(PANEL,0));box.addView(art,new LinearLayout.LayoutParams(-1,(int)(width*1.49f)));
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setClipToOutline(true);image.setBackground(shape(PANEL,0));art.addView(image,new FrameLayout.LayoutParams(-1,-1));posters.add(image,film.image);
        TextView name=text(film.title,18,TEXT);name.setPadding(0,d(14),0,0);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);box.addView(name,new LinearLayout.LayoutParams(-1,d(62)));TextView year=text(film.year,14,MUTED);box.addView(year);if(film.genre!=null&&!film.genre.isEmpty()){TextView genres=text(film.genre.replace('\n',' ').replaceAll("\\s*,\\s*"," · "),12,MUTED);genres.setMaxLines(1);genres.setEllipsize(TextUtils.TruncateAt.END);genres.setPadding(0,d(4),0,0);box.addView(genres,new LinearLayout.LayoutParams(-1,d(23)));}
        box.setOnFocusChangeListener((v,on)->{art.setBackground(shape(on?TEXT:PANEL,0));v.setScaleX(on?1.025f:1);v.setScaleY(on?1.025f:1);if(on){lastRow=rowIndex;lastColumn=column;}});
        box.setOnClickListener(v->details(film));
        wireCardNavigation(box,rowIndex,column);return box;
    }
    void wireCardNavigation(View box,int rowIndex,int column){box.setOnKeyListener((v,key,event)->{if(event.getAction()!=0)return false;switch(key){
            case KeyEvent.KEYCODE_DPAD_LEFT:if(column==0)openDrawer();else cardRows.get(rowIndex).get(column-1).requestFocus();return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:if(column+1<cardRows.get(rowIndex).size())cardRows.get(rowIndex).get(column+1).requestFocus();return true;
            case KeyEvent.KEYCODE_DPAD_UP:{int above=nextCardRow(rowIndex,-1);if(above<0)searchButton.requestFocus();else{ArrayList<View> cards=cardRows.get(above);cards.get(Math.min(column,cards.size()-1)).requestFocus();}return true;}
            case KeyEvent.KEYCODE_DPAD_DOWN:{int below=nextCardRow(rowIndex,1);if(below>=0){ArrayList<View> cards=cardRows.get(below);cards.get(Math.min(column,cards.size()-1)).requestFocus();}else if(moreButton!=null)moreButton.requestFocus();return true;}
        }return false;});
    }
    void details(Api.Film film){Intent i=new Intent(this,DetailActivity.class);i.putExtra("address",film.address);startActivity(i);}
    void account() {
        if(!api.signedIn()) { login(); return; }
        new AlertDialog.Builder(this).setTitle("Аккаунт KrasView").setMessage("Сеанс сохранён только на этом устройстве.").setPositiveButton("Выйти",(dlg,w)->{api.logout(); account.setText("Войти"); load(false);}).setNegativeButton("Закрыть",null).show();
    }
    void login() {
        new AlertDialog.Builder(this).setTitle("Как войти в KrasView?")
            .setItems(new String[]{"Вход с телефона по коду","Электронная почта и пароль"},(dialog,index)->{ if(index==0) pinLogin(); else passwordLogin(); })
            .setNegativeButton("Пока посмотреть оформление",null).show();
    }
    void pinLogin() {
        final int ticket=++pinGeneration;
        LinearLayout form=new LinearLayout(this); form.setOrientation(1); form.setGravity(Gravity.CENTER_HORIZONTAL); form.setPadding(d(28),d(10),d(28),d(16));
        TextView info=text("QR откроет страницу привязки. Войди на сайте, затем вручную введи код с телевизора.",16,TEXT); info.setGravity(Gravity.CENTER); form.addView(info,new LinearLayout.LayoutParams(d(540),-2));
        ImageView qr=new ImageView(this); qr.setScaleType(ImageView.ScaleType.FIT_CENTER); qr.setBackgroundColor(Color.WHITE); qr.setPadding(d(8),d(8),d(8),d(8));
        LinearLayout.LayoutParams imageLp=new LinearLayout.LayoutParams(d(200),d(200)); imageLp.topMargin=d(16); form.addView(qr,imageLp);
        TextView codeLabel=text("Получаю код…",32,ACCENT); codeLabel.setLetterSpacing(.16f); codeLabel.setPadding(0,d(10),0,d(6)); form.addView(codeLabel);
        TextView tip=text("atoto.ru/pin — без цифр в адресе. Код вводится в поле «Код».",14,MUTED); tip.setGravity(Gravity.CENTER); form.addView(tip);
        TextView google=button("Отдельный вход через Google"); LinearLayout.LayoutParams glp=new LinearLayout.LayoutParams(-1,d(44)); glp.topMargin=d(10); form.addView(google,glp);
        TextView state=text("",13,MUTED); state.setPadding(0,d(8),0,0); form.addView(state);
        pinDialog=new AlertDialog.Builder(this).setTitle("Привязка телевизора к KrasView").setView(form)
            .setPositiveButton("Новый код",null).setNegativeButton("Отмена",null).create();
        final AlertDialog dialog=pinDialog;
        final boolean[] googleQr={false};
        google.setOnClickListener(v->{
            if(googleQr[0]) {
                try { qr.setImageBitmap(Qr.image(Qr.LINK,d(220))); } catch(Exception ignored) {}
                info.setText("Вручную введи код с телевизора в поле «Код» на странице привязки.");
                tip.setText("atoto.ru/pin — без цифр в адресе."); google.setText("Отдельный вход через Google"); googleQr[0]=false; return;
            }
            google.setEnabled(false);
            network.execute(()->{ try { String url=api.googleLoginUrl(); Bitmap bitmap=Qr.image(url,d(220));
                handler.post(()->{ if(!alive||ticket!=pinGeneration||!dialog.isShowing())return; google.setEnabled(true); qr.setImageBitmap(bitmap);
                    info.setText("Этот QR открывает страницу Google. После входа нажми «К привязке» на телевизоре.");
                    tip.setText("Завершение Google-входа зависит от сервиса KrasView."); google.setText("К привязке телевизора"); googleQr[0]=true;
                });
            } catch(Exception e) { handler.post(()->{ if(alive&&ticket==pinGeneration&&dialog.isShowing()) { google.setEnabled(true); state.setText("Ссылка Google недоступна. Используй вход, доступный на сайте."); } }); } });
        });
        dialog.setOnDismissListener(v->{ if(ticket==pinGeneration) pinGeneration++; });
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{ dialog.dismiss(); pinLogin(); })); dialog.show();
        network.execute(()->{
            try {
                org.json.JSONObject result=api.generatePin(); String code=result.getString("code");
                Bitmap bitmap=Qr.image(Qr.LINK,d(220));
                handler.post(()->{ if(!alive||ticket!=pinGeneration||!dialog.isShowing())return; if(!googleQr[0])qr.setImageBitmap(bitmap); codeLabel.setText(code); state.setText("Жду подтверждения на телефоне…"); pollPin(code,ticket,dialog,state,System.currentTimeMillis()); });
            } catch(Exception e) { handler.post(()->{ if(alive&&ticket==pinGeneration&&dialog.isShowing()) { codeLabel.setText("Нет связи"); state.setText("Не удалось получить код. Нажми «Новый код»."); } }); }
        });
    }
    void pollPin(String code,int ticket,AlertDialog dialog,TextView state,long started) {
        if(!alive||ticket!=pinGeneration||!dialog.isShowing())return;
        if(System.currentTimeMillis()-started>5*60_000) { state.setText("Время ожидания истекло. Нажми «Новый код»."); return; }
        handler.postDelayed(()->{
            if(!alive||ticket!=pinGeneration||!dialog.isShowing())return;
            network.execute(()->{
                try { boolean ready=api.checkPin(code);
                    handler.post(()->{ if(!alive||ticket!=pinGeneration||!dialog.isShowing())return;
                        if(ready) { dialog.dismiss(); account.setText("Аккаунт"); load(false); }
                        else pollPin(code,ticket,dialog,state,started);
                    });
                } catch(Exception e) { handler.post(()->{
                    if(!alive||ticket!=pinGeneration||!dialog.isShowing())return;
                    if(e instanceof Api.PinExpired) state.setText("Сервер сообщил, что код больше не действует. Нажми «Новый код».");
                    else if(e instanceof Api.SessionStoreException) state.setText("Вход подтверждён, но не удалось сохранить сеанс на стике.");
                    else { state.setText("Не удалось проверить подтверждение. Повторяю проверку…"); pollPin(code,ticket,dialog,state,started); }
                }); }
            });
        },3500);
    }
    void passwordLogin() {
        LinearLayout form=new LinearLayout(this); form.setOrientation(1); form.setPadding(d(26),d(8),d(26),d(10));
        TextView info=text("Вход в аккаунт atoto.ru / KrasView. Пароль отправляется сервису по HTTPS и не сохраняется.",15,MUTED); form.addView(info);
        EditText email=new EditText(this); email.setSingleLine(true); email.setHint("Электронная почта"); email.setInputType(33); form.addView(email);
        EditText password=new EditText(this); password.setSingleLine(true); password.setHint("Пароль"); password.setInputType(129); form.addView(password);
        TextView error=text("",14,0xffffad9d); form.addView(error);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Войти в KrasView").setView(form).setPositiveButton("Войти",null).setNegativeButton("Пока посмотреть оформление",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(-1).setOnClickListener(v->{
            String address=email.getText().toString().trim(),secret=password.getText().toString();
            if(address.isEmpty()||secret.isEmpty()) { error.setText("Введи электронную почту и пароль"); return; }
            dialog.getButton(-1).setEnabled(false); error.setText("Выполняется вход…");
            network.execute(()->{ try { api.login(address,secret); handler.post(()->{ if(!alive)return; password.setText(""); dialog.dismiss(); account.setText("Аккаунт"); load(false); }); }
                catch(Exception ex) { handler.post(()->{ if(alive&&dialog.isShowing()) { dialog.getButton(-1).setEnabled(true); error.setText("Не удалось войти. Проверь данные и соединение с сервисом."); } }); }
            });
        })); dialog.show();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getAction()==0){if(e.getKeyCode()==KeyEvent.KEYCODE_SEARCH){editSearch();return true;}if(e.getKeyCode()==KeyEvent.KEYCODE_MENU){if(drawerOpen)closeDrawer(true);else openDrawer();return true;}}return super.dispatchKeyEvent(e);}
    @Override public void onBackPressed(){if(drawerOpen){closeDrawer(true);return;}if(searchMode||selectedMenu!=0){searchMode=false;selectedMenu=0;query="";sort="top";filter=new CatalogFilter();lastRow=lastColumn=0;load(false);menuButton.requestFocus();return;}if(System.currentTimeMillis()-backAt<1800){super.onBackPressed();return;}backAt=System.currentTimeMillis();menuButton.requestFocus();Toast.makeText(this,"Ещё раз «Назад» — выйти",Toast.LENGTH_SHORT).show();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.hasExtra("section")){selectedMenu=Math.max(0,Math.min(8,intent.getIntExtra("section",0)));searchMode=false;query="";sort="top";filter=new CatalogFilter();readGenre(intent);lastRow=lastColumn=0;if(drawerOpen)closeDrawer(false);load(false);}if(intent.getBooleanExtra("open_search",false))editSearch();if(intent.getBooleanExtra("open_filter",false))openFilter();}
    @Override protected void onResume(){super.onResume();if(posters!=null)posters.resume();if(api!=null){long revision=LocalLibrary.revision(this);if(revision!=libraryRevision){libraryRevision=revision;if(selectedMenu==6||selectedMenu==7)load(false);else if(home()&&!loading&&!LocalLibrary.historyStamp(this).equals(historyStamp))refreshRecommendations();}}}
    @Override protected void onPause(){if(posters!=null)posters.pause();super.onPause();}
    @Override protected void onStop(){if(posters!=null)posters.releaseImages();super.onStop();}
    @Override public void onTrimMemory(int level){super.onTrimMemory(level);if(level==TRIM_MEMORY_RUNNING_LOW||level==TRIM_MEMORY_RUNNING_CRITICAL||level>=TRIM_MEMORY_BACKGROUND)Art.trimMemory();}
    @Override protected void onDestroy(){if(posters!=null)posters.destroy();alive=false;generation++;pinGeneration++;handler.removeCallbacksAndMessages(null);network.shutdownNow();super.onDestroy();}
}
