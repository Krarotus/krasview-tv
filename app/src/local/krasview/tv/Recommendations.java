package local.krasview.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Fetches candidates by preferred genres; selection and history stay on the device. */
final class Recommendations {
    static final long ROW_TTL=6*60*60*1000L,SEED_TTL=30*24*60*60*1000L;
    static final int SEED_LIMIT=6,GENRE_LIMIT=3,ROW_LIMIT=12;
    final Context context;final Api api;final SharedPreferences prefs;
    static final class Result {final List<Api.Film> items=new ArrayList<>();String message="";boolean failed;}
    Recommendations(Context c,Api a){context=c;api=a;prefs=c.getSharedPreferences("recommendations",0);}
    static RecommendationRanker.Item item(Api.Film f){return new RecommendationRanker.Item(f.address,f.section,f.genre);}
    static String firstWatch(String section){return section.equals("movie")?"Посмотри фильм — здесь появятся рекомендации по твоей истории.":section.equals("series")?"Посмотри сериал — здесь появятся рекомендации по твоей истории.":"Посмотри аниме — здесь появятся рекомендации по твоей истории.";}
    Result load(String section,List<Api.Film> history,List<Api.Film> initialCandidates,List<Api.Film> popular)throws Exception{
        Result result=new Result();List<Api.Film> seeds=new ArrayList<>();Set<String> excluded=new LinkedHashSet<>();
        StringBuilder stamp=new StringBuilder("genre-v1|").append(section);
        for(Api.Film f:history){excluded.add(f.address);stamp.append('|').append(LocalLibrary.json(f));if(section.equals(f.section)&&seeds.size()<SEED_LIMIT)seeds.add(f);}
        for(Api.Film f:popular){excluded.add(f.address);stamp.append('|').append(f.address);}
        if(seeds.isEmpty()){result.message=firstWatch(section);return result;}
        String signature=stamp.toString();Result cached=cached(section,signature);if(cached!=null){android.util.Log.i("KrasViewTV","Recommendations cache hit; section="+section+" count="+cached.items.size());return cached;}
        long deadline=android.os.SystemClock.uptimeMillis()+25000;
        List<RecommendationRanker.Item> watched=new ArrayList<>();Map<String,Integer> genreIds=new LinkedHashMap<>();boolean incomplete=false;
        for(Api.Film stored:seeds){
            if(android.os.SystemClock.uptimeMillis()>deadline){incomplete=true;break;}
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();Api.Film f=stored;
            if(f.genreTags.isEmpty()){Api.Film saved=seed(stored.address);if(saved!=null)f=saved;else try{f=api.recommendationSeed(stored);if(!f.genreTags.isEmpty())saveSeed(f);}catch(Api.AuthRequired e){throw e;}catch(Exception e){incomplete=true;}}
            watched.add(item(f));for(Map.Entry<Integer,String> tag:f.genreTags.entrySet())genreIds.put(RecommendationRanker.normalize(tag.getValue()),tag.getKey());
        }
        List<String> preferred=RecommendationRanker.preferredGenres(watched,section);
        if(preferred.isEmpty()){result.failed=incomplete;result.message=incomplete?"Не удалось получить жанры из истории. Попробуй ещё раз.":"В истории пока нет жанров для подбора рекомендаций.";return result;}
        LinkedHashMap<String,Api.Film> pool=new LinkedHashMap<>();for(Api.Film f:initialCandidates)pool.put(f.address,f);
        int requests=0;
        for(String genre:preferred){if(android.os.SystemClock.uptimeMillis()>deadline){incomplete=true;break;}Integer id=genreIds.get(genre);if(id==null)continue;if(requests++>=GENRE_LIMIT)break;
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();CatalogFilter filter=new CatalogFilter();filter.genres.put(id,genre);
            try{for(Api.Film f:api.catalog(section,"",1,"top",false,filter).items)pool.putIfAbsent(f.address,f);}catch(Api.AuthRequired e){throw e;}catch(Exception e){incomplete=true;}
        }
        List<RecommendationRanker.Item> ranked=rank(watched,pool,excluded,section);
        if(ranked.size()<ROW_LIMIT&&android.os.SystemClock.uptimeMillis()<=deadline)try{for(Api.Film f:api.catalog(section,"",2,"top").items)pool.putIfAbsent(f.address,f);ranked=rank(watched,pool,excluded,section);}catch(Api.AuthRequired e){throw e;}catch(Exception e){incomplete=true;}
        for(RecommendationRanker.Item choice:ranked)result.items.add(pool.get(choice.key));
        if(result.items.isEmpty()){result.failed=incomplete;result.message=incomplete?"Не удалось загрузить рекомендации. Попробуй ещё раз.":"Пока нет новых рекомендаций по твоей истории.";}
        if(!incomplete)save(section,signature,result);
        android.util.Log.i("KrasViewTV","Recommendations built; section="+section+" seeds="+watched.size()+" count="+result.items.size()+" incomplete="+incomplete);return result;
    }
    static List<RecommendationRanker.Item> rank(List<RecommendationRanker.Item> watched,Map<String,Api.Film> pool,Set<String> excluded,String section){List<RecommendationRanker.Item> items=new ArrayList<>();for(Api.Film f:pool.values())items.add(item(f));return RecommendationRanker.rank(watched,items,excluded,section,ROW_LIMIT);}
    Result cached(String section,String signature){try{JSONObject o=new JSONObject(prefs.getString("row-"+section,"{}"));long age=System.currentTimeMillis()-o.optLong("at");if(age<0||age>ROW_TTL||!signature.equals(o.optString("signature")))return null;JSONArray list=o.optJSONArray("items");if(list==null)return null;Result result=new Result();for(int i=0;i<list.length();i++)result.items.add(LocalLibrary.film(list.getJSONObject(i)));result.message=o.optString("message");return result;}catch(JSONException e){return null;}}
    void save(String section,String signature,Result result)throws JSONException{JSONArray list=new JSONArray();for(Api.Film f:result.items)list.put(LocalLibrary.json(f));JSONObject o=new JSONObject().put("at",System.currentTimeMillis()).put("signature",signature).put("items",list).put("message",result.message);prefs.edit().putString("row-"+section,o.toString()).apply();}
    Api.Film seed(String address){try{JSONObject o=new JSONObject(prefs.getString("seeds","{}")).optJSONObject(address);if(o==null)return null;long age=System.currentTimeMillis()-o.optLong("at");return age>=0&&age<SEED_TTL?LocalLibrary.film(o.getJSONObject("film")):null;}catch(JSONException e){return null;}}
    void saveSeed(Api.Film film)throws JSONException{synchronized(Recommendations.class){JSONObject all=new JSONObject(prefs.getString("seeds","{}"));all.put(film.address,new JSONObject().put("at",System.currentTimeMillis()).put("film",LocalLibrary.json(film)));while(all.length()>60){String oldest=null;long at=Long.MAX_VALUE;Iterator<String> keys=all.keys();while(keys.hasNext()){String key=keys.next();long value=all.getJSONObject(key).optLong("at");if(value<at){at=value;oldest=key;}}if(oldest==null)break;all.remove(oldest);}prefs.edit().putString("seeds",all.toString()).apply();}}
}
