package local.krasview.tv;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.Binder;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only launcher feed: published cards only, never session data or raw history. */
public final class RecommendationProvider extends ContentProvider {
    static final Uri URI=Uri.parse("content://local.krasview.tv.recommendations/items");
    static final String[] COLUMNS={"_id","address","title","image","year","section"};
    final ExecutorService jobs=Executors.newSingleThreadExecutor();final AtomicBoolean refreshing=new AtomicBoolean();long lastAttempt;
    @Override public boolean onCreate(){return true;}
    static void changed(Context context){context.getContentResolver().notifyChange(URI,null);}
    void checkCaller(){int uid=Binder.getCallingUid();if(uid==android.os.Process.myUid())return;String[] packages=getContext().getPackageManager().getPackagesForUid(uid);if(packages!=null)for(String name:packages)if(name.equals("io.freemytv"))return;throw new SecurityException("Launcher access only");}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        checkCaller();if(!URI.equals(uri)||selection!=null||args!=null||sort!=null)throw new IllegalArgumentException("Unsupported feed query");
        String[] columns=projection==null?COLUMNS:projection;for(String column:columns)if(!Arrays.asList(COLUMNS).contains(column))throw new IllegalArgumentException("Unsupported column");
        Context c=getContext();List<Api.Film> history=LocalLibrary.list(c,"history");Set<String> watched=new HashSet<>();for(Api.Film film:history)watched.add(film.address);
        List<List<Api.Film>> shelves=new ArrayList<>();boolean stale=false;android.content.SharedPreferences prefs=c.getSharedPreferences("recommendations",0);
        for(String section:new String[]{"movie","series","anime"}){List<Api.Film> row=new ArrayList<>();boolean hasHistory=false;String prefix=Recommendations.historySignature(section,history);for(Api.Film film:history)if(section.equals(film.section))hasHistory=true;
            try{JSONObject cache=new JSONObject(prefs.getString("row-"+section,"{}"));long age=System.currentTimeMillis()-cache.optLong("at");if(hasHistory&&(age<0||age>Recommendations.ROW_TTL||!cache.optString("signature").startsWith(prefix+"|")))stale=true;
                JSONArray items=cache.optJSONArray("items");if(hasHistory&&items!=null)for(int i=0;i<Math.min(12,items.length());i++){Api.Film film=LocalLibrary.film(items.getJSONObject(i));if(!watched.contains(film.address)&&!film.address.isEmpty()&&film.section.equals(section))row.add(film);}
            }catch(JSONException ignored){stale|=hasHistory;}shelves.add(row);
        }
        MatrixCursor cursor=new MatrixCursor(columns);Set<String> included=new HashSet<>();for(int rank=0;rank<12;rank++)for(List<Api.Film> shelf:shelves)if(rank<shelf.size()){Api.Film f=shelf.get(rank);if(!included.add(f.address))continue;MatrixCursor.RowBuilder row=cursor.newRow();for(String column:columns){switch(column){case "_id":row.add(f.id);break;case "address":row.add(f.address);break;case "title":row.add(f.title);break;case "image":row.add(f.image);break;case "year":row.add(f.year);break;case "section":row.add(f.section);break;}}}
        cursor.setNotificationUri(c.getContentResolver(),URI);if(stale)refresh(history);return cursor;
    }
    synchronized void refresh(List<Api.Film> history){long now=android.os.SystemClock.uptimeMillis();if((lastAttempt!=0&&now-lastAttempt<60000)||!refreshing.compareAndSet(false,true))return;lastAttempt=now;
        jobs.execute(()->{try{Api api=new Api(getContext());if(!api.signedIn())return;Recommendations recommendations=new Recommendations(getContext(),api);for(String section:new String[]{"movie","series","anime"}){boolean any=false;for(Api.Film f:history)if(section.equals(f.section))any=true;if(!any)continue;try{Api.Page page=api.catalog(section,"",1,"top");recommendations.load(section,history,page.items,page.items.subList(0,Math.min(12,page.items.size())));}catch(Exception ignored){}}}finally{refreshing.set(false);}});
    }
    @Override public String getType(Uri uri){return "vnd.android.cursor.dir/vnd.local.krasview.recommendation";}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read only");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("Read only");}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("Read only");}
}
