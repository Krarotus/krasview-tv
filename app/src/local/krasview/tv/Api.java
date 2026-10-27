package local.krasview.tv;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.util.*;

final class Api {
    static final String HOST = "https://atoto.ru";
    final Context context;
    final String clientUserAgent;
    String token;
    Api(Context c) { context=c;String version;try{version=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(android.content.pm.PackageManager.NameNotFoundException e){version="dev";}clientUserAgent="App.krasview-tv/"+version;token=readToken(); }
    boolean signedIn() { return token!=null && !token.isEmpty(); }
    static class AuthRequired extends IOException {}
    static class AccessRequired extends IOException {}
    static class PinExpired extends IOException {}
    static class SessionStoreException extends IOException { SessionStoreException(Throwable cause) { super("Local session storage failed",cause); } }
    static class Film {
        int id;
        String title, address, image, year, section, genre, rating;
        boolean demo;
    }
    static class Page { List<Film> items=new ArrayList<>(); boolean more; }
    JSONObject request(String path, JSONObject body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(HOST+path).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(15000);
        c.setRequestProperty("Accept","application/json");
        // The service distinguishes native playback from browser playback by client context.
        // Identify our own Android TV client; never impersonate the official app or copy its session.
        c.setRequestProperty("User-Agent",clientUserAgent);
        c.setRequestProperty("Content-Type","application/json");
        if(signedIn()) c.setRequestProperty("Cookie","token="+URLEncoder.encode(token,"UTF-8"));
        try {
            if(body!=null) {
                c.setRequestMethod("POST"); c.setDoOutput(true);
                c.setRequestProperty("Content-Type","application/json");
                c.setRequestProperty("X-Requested-With","XMLHttpRequest");
                try(OutputStream out=c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int status=c.getResponseCode();
            if(status==401) throw new AuthRequired();
            InputStream input=status>=400?c.getErrorStream():c.getInputStream();
            if(input==null) throw new IOException("Сервис не ответил");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=input) { byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) { if(bytes.size()+n>8_000_000) throw new IOException("Ответ слишком большой"); bytes.write(b,0,n); } }
            String payload=bytes.toString("UTF-8");JSONObject result=payload.trim().startsWith("[")?new JSONObject().put("data",new JSONArray(payload)):new JSONObject(payload);
            if(result.optString("error").equals("pay")||result.optString("error").equals("access")) throw new AccessRequired();
            if(path.startsWith("/api/pin-check/") && (result.optString("error").equals("no pin")||result.optString("error").equals("expired"))) throw new PinExpired();
            if(status>=400 || (result.has("error") && !(path.startsWith("/api/pin-check/") && result.optString("error").equals("pending")))) throw new IOException("Сервис отклонил запрос");
            return result;
        } finally { c.disconnect(); }
    }
    void login(String email,String password) throws Exception {
        JSONObject r=request("/api/login/",new JSONObject().put("email",email).put("password",password));
        String value=r.optString("token","");
        if(value.isEmpty()) throw new IOException("Проверь логин и пароль");
        saveToken(value); token=value;
    }
    JSONObject generatePin() throws Exception { return request("/api/pin-gen",null); }
    String googleLoginUrl() throws Exception {
        String url=request("/api/oauth/google/url",null).getString("url");
        URL parsed=new URL(url);
        if(!parsed.getProtocol().equals("https")||!parsed.getHost().equals("accounts.google.com")) throw new IOException("Неверный адрес входа");
        return url;
    }
    boolean checkPin(String code) throws Exception {
        JSONObject r=request("/api/pin-check/"+URLEncoder.encode(code,"UTF-8"),null);
        if(r.optString("error").equals("pending")) return false;
        String value=r.optString("token","");
        if(value.isEmpty()) throw new IOException("Неполный ответ сервиса привязки");
        saveToken(value); token=value; return true;
    }
    Page catalog(String section,String query,int page,String sort) throws Exception {
        return catalog(section,query,page,sort,false);
    }
    Page catalog(String section,String query,int page,String sort,boolean cartoons) throws Exception {
        return catalog(section,query,page,sort,cartoons,new CatalogFilter());
    }
    Page catalog(String section,String query,int page,String sort,boolean cartoons,CatalogFilter selected) throws Exception {
        JSONArray sections=new JSONArray();
        if(section.equals("all")) { sections.put("movie"); sections.put("series"); sections.put("anime"); } else sections.put(section);
        JSONArray categories=new JSONArray();for(int id:selected.genres.keySet())categories.put(id);if(cartoons&&!selected.genres.containsKey(9558))categories.put(9558);
        JSONObject filter=new JSONObject().put("section",sections).put("query",query).put("sort",sort)
            .put("page",page).put("category",categories).put("country",new JSONArray()).put("studio",new JSONArray())
            .put("years",new JSONArray().put(selected.from).put(selected.to));
        JSONObject r=request("/api/channel/list",filter);
        JSONArray a=r.optJSONArray("data");
        if(a==null) throw new IOException("Формат каталога изменился");
        Page result=new Page(); result.more=r.optJSONObject("meta")!=null && r.optJSONObject("meta").optBoolean("hasMore",false);
        for(int i=0;i<a.length();i++) {
            JSONObject o=a.optJSONObject(i); if(o==null) continue;
            Film f=new Film();
            f.id=o.optInt("id");
            f.title=android.text.Html.fromHtml(o.optString("title_rus",o.optString("title","Без названия")),0).toString();
            f.address=o.optString("address",""); f.year=o.optString("year",""); f.section=o.optString("section","");
            f.genre=android.text.Html.fromHtml(o.optString("genre",""),0).toString(); f.rating=o.optString("kinopoisk","");
            String id=o.optString("id",""),photo=o.optString("photo","");
            f.image=id.isEmpty()||photo.isEmpty()?"":"https://cdn1.atoto.ru/channel/"+id+"/"+photo+".jpg";
            result.items.add(f);
        }
        return result;
    }
    static class Genre {int id;String name,sections;Genre(int i,String n,String s){id=i;name=n;sections=s;}}
    List<Genre> genres() throws Exception {JSONArray data=request("/api/getCategories",null).getJSONArray("data");List<Genre> out=new ArrayList<>();for(int i=0;i<data.length();i++){JSONObject g=data.getJSONObject(i);out.add(new Genre(g.getInt("value"),plain(g.getString("text")),g.optString("section")));}return out;}
    static class Detail {
        int id,lastVideo; String title,description,metadata,poster,address,background,year,section,primaryMeta,genres;
        boolean webRestricted;
        List<Season> seasons=new ArrayList<>(); List<Episode> episodes=new ArrayList<>();
        List<Genre> genreTags=new ArrayList<>();
    }
    static class Season { int id; String title; }
    static class Episode { int id,duration; String title,voice; }
    static class Source { String url,label; Source(String u,String l) { url=u;label=l; } }
    static class Subtitle {String url,label,mime;Subtitle(String u,String l,String m){url=u;label=l;mime=m;}}
    static class Playback { String title; int id,duration; List<Source> sources=new ArrayList<>(); List<String> audioNames=new ArrayList<>();List<Subtitle> subtitles=new ArrayList<>(); }
    static void subtitles(Playback out,JSONObject video){JSONObject extra=video.optJSONObject("extra");JSONArray list=video.optJSONArray("sub_info");if(list==null&&extra!=null)list=extra.optJSONArray("sub_info");if(list==null)return;for(int i=0;i<Math.min(32,list.length());i++){JSONObject track=list.optJSONObject(i);if(track==null)continue;String name=track.optString("name"),url=SubtitleFiles.url(video.isNull("server")?"":video.optString("server"),video.optInt("server_cache"),video.optString("v_key"),name);if(!url.isEmpty())out.subtitles.add(new Subtitle(url,SubtitleFiles.label(plain(track.optString("label")),i),SubtitleFiles.mime(name)));}}
    static void audioNames(Playback out,JSONObject video) {
        Object info=video.opt("audio_info");if(info==null){JSONObject extra=video.optJSONObject("extra");if(extra!=null)info=extra.opt("audio_info");}
        int count=video.optInt("audio",0);if(info instanceof JSONArray)count=Math.max(count,((JSONArray)info).length());else if(info instanceof JSONObject)count=Math.max(count,((JSONObject)info).length());
        for(int i=0;i<Math.min(64,count);i++){String name=info instanceof JSONArray?((JSONArray)info).optString(i,""):info instanceof JSONObject?((JSONObject)info).optString(Integer.toString(i),""):"";out.audioNames.add(plain(name));}
    }
    static String plain(String s) { return android.text.Html.fromHtml(s,0).toString().trim(); }
    Detail detail(String address) throws Exception {
        JSONObject r=request("/api/channel/item/"+URLEncoder.encode(address,"UTF-8"),null),c=r.getJSONObject("channel");
        Detail d=new Detail(); d.address=address; d.id=c.getInt("id"); d.title=plain(c.optString("title"));
        d.description=plain(c.optString("description")); d.lastVideo=c.optInt("last_video_id"); d.webRestricted=r.optBoolean("toPay");
        d.poster="https://cdn1.atoto.ru/channel/"+d.id+"/"+c.optString("photo")+".jpg";
        d.section=c.optString("section");d.background=c.optString("background_image","");if(d.background.matches("[A-Za-z0-9_-]+\\.(jpg|jpeg|png|webp)"))d.background="https://image.krasview.ru/channel/"+d.id+"/"+d.background;else if(!d.background.startsWith("https://")&&!d.background.startsWith("http://"))d.background="";
        JSONObject movie=c.optJSONObject("movie"); List<String> pieces=new ArrayList<>();
        d.year=movie==null?"":movie.optString("year","");
        if(movie!=null) { if(movie.optInt("year")>0)pieces.add(movie.optString("year"));
            JSONArray countries=movie.optJSONArray("country"); if(countries!=null)for(int i=0;i<countries.length();i++)pieces.add(countries.optString(i));
            if(!movie.optString("rated_age").isEmpty())pieces.add(movie.optString("rated_age")); }
        JSONObject genres=r.optJSONObject("genres"); if(genres!=null) { Iterator<String> keys=genres.keys(); while(keys.hasNext())pieces.add(genres.optString(keys.next())); }
        d.metadata=android.text.TextUtils.join("  ·  ",pieces);
        List<String> primary=new ArrayList<>();if(!d.year.isEmpty())primary.add(d.year);JSONArray countries=movie==null?null:movie.optJSONArray("country");if(countries!=null)for(int i=0;i<countries.length();i++)primary.add(countries.optString(i));d.primaryMeta=android.text.TextUtils.join(", ",primary);
        List<String> genreNames=new ArrayList<>();if(genres!=null){Iterator<String> keys=genres.keys();while(keys.hasNext()){String key=keys.next(),name=plain(genres.optString(key));genreNames.add(name);try{d.genreTags.add(new Genre(Integer.parseInt(key),name,"movie,series,anime"));}catch(NumberFormatException ignored){}}}d.genres=android.text.TextUtils.join("  ·  ",genreNames);
        JSONObject seasonResult=request("/api/channel/seasons/"+d.id,null);
        JSONArray seasons=seasonResult.optJSONArray("seasons");
        if(seasons!=null)for(int i=0;i<seasons.length();i++) { JSONObject s=seasons.getJSONObject(i); Season season=new Season(); season.id=s.getInt("id"); season.title=plain(s.optString("title","Сезон "+(i+1))); d.seasons.add(season); }
        JSONArray videos=r.optJSONArray("video"); if(videos!=null)d.episodes=episodes(videos);
        JSONObject movieVideo=r.optJSONObject("movie");
        if(movieVideo!=null && movieVideo.has("v_id"))d.episodes.add(episode(movieVideo));
        if(d.section.equals("movie")){
            try{List<Episode> choices=publicMovieEpisodes(address);if(!choices.isEmpty()){LinkedHashMap<Integer,Episode> merged=new LinkedHashMap<>();for(Episode e:choices)merged.put(e.id,e);for(Episode e:d.episodes)if(!merged.containsKey(e.id))merged.put(e.id,e);d.episodes=new ArrayList<>(merged.values());d.seasons.clear();}}
            catch(Exception e){if(d.episodes.isEmpty())throw new IOException("Не удалось получить список версий фильма",e);}
        }
        if(!d.section.equals("movie")&&d.seasons.isEmpty()&&d.episodes.isEmpty()&&d.lastVideo>0) { Episode e=new Episode(); e.id=d.lastVideo;e.title="Смотреть видео";e.voice="";d.episodes.add(e); }
        return d;
    }
    private List<Episode> publicMovieEpisodes(String address)throws Exception{
        String page=publicPage("https://hlamer.ru/movie/"+URLEncoder.encode(address,"UTF-8"));List<Episode> out=new ArrayList<>();
        for(MovieChoices.Entry item:MovieChoices.parse(page)){Episode e=new Episode();e.id=item.id;e.duration=item.duration;e.title=plain(item.title);e.voice="";out.add(e);}
        if(out.isEmpty()){
            java.util.regex.Matcher match=contentMatcher(page);if(match.find()){JSONObject video=new JSONObject(new String(Base64.decode(match.group(1),Base64.DEFAULT),StandardCharsets.UTF_8));if(address.equals(video.optString("channel"))&&video.optInt("v_id")>0){Episode e=new Episode();e.id=video.getInt("v_id");e.duration=video.optInt("duration");e.title="Фильм";e.voice="";out.add(e);}}
        }
        return out;
    }
    List<Episode> season(int id) throws Exception {
        JSONObject result=request("/api/channel/season/"+id,null);
        JSONArray list=result.optJSONArray("video"); if(list==null)throw new IOException("Не найден список серий"); return episodes(list);
    }
    private static List<Episode> episodes(JSONArray a) throws Exception { List<Episode> out=new ArrayList<>();for(int i=0;i<a.length();i++)out.add(episode(a.getJSONObject(i)));return out; }
    private static Episode episode(JSONObject o) {
        Episode e=new Episode(); e.id=o.optInt("v_id",o.optInt("id"));e.title=plain(o.optString("v_title",o.optString("title","Видео")));e.duration=o.optInt("duration");e.voice="";
        JSONObject extra=o.optJSONObject("extra");if(extra!=null) { Object info=extra.opt("audio_info"); if(info instanceof JSONArray)e.voice=android.text.TextUtils.join(", ",jsonStrings((JSONArray)info)); }
        return e;
    }
    private static List<String> jsonStrings(JSONArray a) {List<String> out=new ArrayList<>();for(int i=0;i<a.length();i++)out.add(a.optString(i));return out;}
    Playback playback(int id) throws Exception {
        try { return apiPlayback(id); }
        catch(AccessRequired e) { return publicPlayback(id); }
    }
    private Playback apiPlayback(int id) throws Exception {
        JSONObject result=request("/api/video/"+id,null);
        if(result.optBoolean("toPay"))throw new AccessRequired();
        JSONObject v=result.optJSONObject("video");if(v==null)throw new IOException("Сервис не выдал видео");
        Playback out=new Playback();out.id=id;out.duration=v.optInt("duration");out.title=plain(v.optString("v_title","Просмотр"));audioNames(out,v);subtitles(out,v);
        for(String key:new String[]{"url","url1","url2"}) {
            String u=v.optString(key,""); if(u.startsWith("//"))u="https:"+u;
            if(u.isEmpty())continue; URL url=new URL(u);
            if(!url.getProtocol().equals("https")&&!url.getProtocol().equals("http"))continue;
            boolean duplicate=false;for(Source source:out.sources)if(source.url.equals(u))duplicate=true;
            if(!duplicate)out.sources.add(new Source(u,url.getPath().toLowerCase(Locale.ROOT).endsWith(".mpd")?"DASH":"MP4"));
        }
        if(out.sources.isEmpty())throw new IOException("Сервис не выдал поддерживаемую ссылку на видео");
        return out;
    }
    private Playback publicPlayback(int id) throws Exception {
        String page=publicPage("https://hlamer.ru/video/"+id);
        // Only decode the content object. The separate ad options and scripts are never executed.
        java.util.regex.Matcher match=contentMatcher(page);
        if(!match.find())throw new AccessRequired();
        String json=new String(Base64.decode(match.group(1),Base64.DEFAULT),StandardCharsets.UTF_8);
        JSONObject video=new JSONObject(json);
        return publicPlaybackData(id,video);
    }
    private static java.util.regex.Matcher contentMatcher(String page){return java.util.regex.Pattern.compile("video_Init\\s*\\(\\s*['\"]([A-Za-z0-9+/=\\r\\n]+)['\"]").matcher(page);}
    private String publicPage(String url)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","KrasView-TV/0.8 (Android)");
        String page;
        try {
            if(c.getResponseCode()!=200)throw new IOException("Страница видео недоступна");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>8_000_000)throw new IOException("Страница слишком большая");bytes.write(b,0,n);}}
            page=bytes.toString("windows-1251");
        }finally{c.disconnect();}
        return page;
    }
    static Playback publicPlaybackData(int id,JSONObject video) throws Exception {
        if(video.optInt("v_id")!=id)throw new IOException("Страница содержит другое видео");
        Playback out=new Playback();out.id=id;out.duration=video.optInt("duration");out.title=plain(video.optString("v_title",video.optString("title","")));audioNames(out,video);subtitles(out,video);
        for(String key:new String[]{"url","url2"}) {
            String value=video.optString(key,"");if(value.startsWith("//"))value="https:"+value;
            if(value.isEmpty())continue;URL parsed=new URL(value);
            if(!parsed.getProtocol().equals("https")&&!parsed.getProtocol().equals("http"))continue;
            boolean duplicate=false;for(Source existing:out.sources)if(existing.url.equals(value))duplicate=true;
            if(!duplicate)out.sources.add(new Source(value,parsed.getPath().toLowerCase(Locale.ROOT).endsWith(".mpd")?"DASH":"MP4"));
        }
        if(out.sources.isEmpty())throw new IOException("Для этого видео пока не найден поддерживаемый источник");
        return out;
    }
    void logout() { token=null; context.getSharedPreferences("auth",0).edit().clear().apply(); }
    private javax.crypto.SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if(ks.containsAlias("catalog-session")) return (javax.crypto.SecretKey)ks.getKey("catalog-session",null);
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder("catalog-session",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return gen.generateKey();
    }
    private void saveToken(String value) throws Exception {
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            String backend="keystore";
            byte[] encrypted;
            try { cipher.init(Cipher.ENCRYPT_MODE,key()); encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)); }
            catch(java.security.GeneralSecurityException | java.security.ProviderException e) {
                android.util.Log.i("KrasViewTV","Android Keystore unavailable; using app-private session key");
                backend="private"; cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,privateKey(true));
                encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            }
            boolean saved=context.getSharedPreferences("auth",0).edit()
                .putString("data",Base64.encodeToString(encrypted,Base64.NO_WRAP))
                .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).putString("backend",backend).commit();
            if(!saved) throw new IOException("Could not write session");
        } catch(Exception e) { throw new SessionStoreException(e); }
    }
    private javax.crypto.SecretKey privateKey(boolean create) throws Exception {
        android.util.AtomicFile file=new android.util.AtomicFile(new File(context.getNoBackupFilesDir(),"session-key"));
        byte[] bytes;
        try { bytes=file.readFully(); }
        catch(FileNotFoundException e) {
            if(!create) throw e;
            bytes=new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
            FileOutputStream out=null;
            try { out=file.startWrite(); out.write(bytes); file.finishWrite(out); }
            catch(IOException writeError) { file.failWrite(out); throw writeError; }
        }
        if(bytes.length!=32) throw new IOException("Invalid local key");
        return new javax.crypto.spec.SecretKeySpec(bytes,"AES");
    }
    private String readToken() {
        try {
            android.content.SharedPreferences p=context.getSharedPreferences("auth",0);
            if(!p.contains("data")) return null;
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            javax.crypto.SecretKey storedKey=p.getString("backend","keystore").equals("private")?privateKey(false):key();
            cipher.init(Cipher.DECRYPT_MODE,storedKey,new GCMParameterSpec(128,Base64.decode(p.getString("iv",""),Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(p.getString("data",""),Base64.NO_WRAP)),StandardCharsets.UTF_8);
        } catch(Exception e) { return null; }
    }
}
