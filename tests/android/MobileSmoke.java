package local.krasview.tv;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Separate instrumentation APK. Synthetic catalog only; never reads account/session data. */
public class MobileSmoke extends Instrumentation {
    void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    void ui(Runnable r){runOnMainSync(r);waitForIdleSync();}
    void shot(String name)throws Exception{
        SystemClock.sleep(600);Bitmap b=getUiAutomation().takeScreenshot();check(b!=null,"Screenshot unavailable");
        File dir=new File(getTargetContext().getExternalFilesDir(null),"mobile-qa");dir.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
    }
    Api.Film film(int i){Api.Film f=new Api.Film();f.id=i+1;f.address="qa_movie_"+i;f.title=i==0?"Долгое название фильма для проверки карточки":"Тестовый фильм "+(i+1);f.year="2025";f.section="movie";f.genre="Приключения · Фантастика";f.image="";f.rating="";return f;}
    void colorPosters(View v){if(v instanceof ImageView)v.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff3c536d,0xff342637}));if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)colorPosters(((ViewGroup)v).getChildAt(i));}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        MainActivity a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        ui(()->{check(a.mobile(),"Wrong app flavor");check(a.root.getWidth()<a.root.getHeight(),"Catalog must be portrait");a.shelves.clear();List<List<Api.Film>> rows=new ArrayList<>();for(int n=0;n<3;n++){List<Api.Film> row=new ArrayList<>();for(int i=0;i<8;i++)row.add(film(i+n*8));rows.add(row);}a.shelves.addAll(MainActivity.homeRows(rows));for(int i=1;i<6;i+=2){a.shelves.get(i).items.addAll(rows.get(i/2).subList(2,6));}a.renderHome();colorPosters(a.root);});
        shot("home");
        ui(()->{a.selectedMenu=1;a.films.clear();for(int i=0;i<12;i++)a.films.add(film(i));a.renderGrid();colorPosters(a.root);});
        ui(()->{check(a.columns()==2,"Expected two phone columns");ArrayList<View> cards=a.cardRows.get(0);check(cards.size()==2,"Grid row count");int[] edge=new int[2];cards.get(1).getLocationOnScreen(edge);check(edge[0]+cards.get(1).getWidth()<=a.root.getWidth(),"Grid overflows phone width");});shot("catalog");
        ui(()->FilterDialog.show(a,new CatalogFilter(),Arrays.asList(new Api.Genre(1,"Приключения","movie")),value->{}));shot("filter");getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);waitForIdleSync();
        DetailActivity detail=(DetailActivity)startActivitySync(new Intent(a,DetailActivity.class).putExtra("address","qa_fixture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ui(()->{detail.alive=false;detail.detail=new Api.Detail();Api.Detail d=detail.detail;d.title="Долгое название фильма для смартфона";d.address="qa_fixture";d.primaryMeta="2025 · Приключения";d.description="Проверка удобства чтения описания, кнопок просмотра, закладок и списка серий на узком экране.";d.background=d.poster="";d.genreTags.add(new Api.Genre(1,"Приключения","movie"));d.genreTags.add(new Api.Genre(2,"Фантастика","movie"));detail.film=film(0);for(int i=0;i<3;i++){Api.Episode e=new Api.Episode();e.id=i+1;e.title=i==0?"Полный фильм":i==1?"Трейлер":"Расширенная версия";e.duration=i==1?90:7200;d.episodes.add(e);}detail.render();colorPosters(detail.root);});shot("detail");
        ui(()->{check(detail.watch.getWidth()>=detail.d(120),"Watch button too narrow");detail.watch.performClick();});shot("versions");
        ui(()->{check(detail.episodeList.getChildCount()==3,"Missing film variants");check(detail.episodeList.getChildAt(0).getWidth()<=detail.getResources().getDisplayMetrics().widthPixels,"Episode row overflows");detail.episodesDialog.dismiss();detail.finish();a.finish();});
        result.putString("stream","PASS: portrait home, 2-column catalog, filter, detail and film variants. Synthetic fixtures; login and playback not covered.\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+e.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
