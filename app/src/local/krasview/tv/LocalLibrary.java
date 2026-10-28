package local.krasview.tv;

import android.content.Context;
import org.json.*;
import java.util.*;

final class LocalLibrary {
    private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("library",0);}
    static long revision(Context c){return prefs(c).getLong("revision",0);}
    static JSONObject json(Api.Film f){
        JSONObject o=new JSONObject();try{o.put("id",f.id).put("address",f.address).put("title",f.title).put("image",f.image).put("year",f.year).put("section",f.section).put("genre",f.genre);JSONObject tags=new JSONObject();for(Map.Entry<Integer,String> tag:f.genreTags.entrySet())tags.put(Integer.toString(tag.getKey()),tag.getValue());o.put("genres",tags);}catch(JSONException ignored){}return o;
    }
    static Api.Film film(JSONObject o){Api.Film f=new Api.Film();f.id=o.optInt("id");f.address=o.optString("address");f.title=o.optString("title");f.image=o.optString("image");f.year=o.optString("year");f.section=o.optString("section");f.genre=o.optString("genre");f.rating="";JSONObject tags=o.optJSONObject("genres");if(tags!=null){Iterator<String> keys=tags.keys();while(keys.hasNext()){String key=keys.next();try{f.genreTags.put(Integer.parseInt(key),tags.optString(key));}catch(NumberFormatException ignored){}}}return f;}
    static String historyStamp(Context c){return prefs(c).getString("history","[]");}
    private static JSONArray data(Context c,String key){try{return new JSONArray(prefs(c).getString(key,"[]"));}catch(JSONException e){return new JSONArray();}}
    static List<Api.Film> list(Context c,String key){List<Api.Film> out=new ArrayList<>();JSONArray a=data(c,key);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)out.add(film(o));}return out;}
    static boolean bookmarked(Context c,String address){for(Api.Film f:list(c,"bookmarks"))if(f.address.equals(address))return true;return false;}
    static synchronized boolean toggle(Context c,Api.Film f){
        boolean exists=bookmarked(c,f.address);JSONArray next=new JSONArray();if(!exists)next.put(json(f));
        for(Api.Film item:list(c,"bookmarks"))if(!item.address.equals(f.address)&&next.length()<200)next.put(json(item));
        prefs(c).edit().putString("bookmarks",next.toString()).putLong("revision",revision(c)+1).apply();return !exists;
    }
    static synchronized void watched(Context c,String serialized){
        try{Api.Film f=film(new JSONObject(serialized));if(f.address.isEmpty())return;
            JSONArray next=new JSONArray().put(json(f));for(Api.Film item:list(c,"history"))if(!item.address.equals(f.address)&&next.length()<60)next.put(json(item));
            prefs(c).edit().putString("history",next.toString()).putLong("revision",revision(c)+1).apply();RecommendationProvider.changed(c);
        }catch(JSONException ignored){}
    }
}
