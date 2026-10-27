package local.krasview.tv;
import android.content.Context;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class LampacSource implements MediaSource {
    private final SourceSettings settings;
    LampacSource(Context c){settings=new SourceSettings(c);}
    public String id(){return "lampac";}public String name(){return "Lampac NG";}
    public Set<Capability> capabilities(){return Collections.unmodifiableSet(EnumSet.of(Capability.PLAYBACK,Capability.BROWSE));}
    public boolean configured(){try{SourceUrls.endpoint(settings.read().optString("lampac_url"));return true;}catch(Exception e){return false;}}
    private JSONObject config()throws Exception{JSONObject c=settings.read();SourceUrls.endpoint(c.optString("lampac_url"));return c;}
    public Media.Browse browse(Media.Query query,String reference)throws Exception{
        JSONObject config=config();URI base=SourceUrls.endpoint(config.getString("lampac_url"));String url;
        boolean providers=reference==null||reference.isEmpty();
        if(providers)url=base+"/lite/events";else{JSONObject node=new JSONObject(reference);if(!node.optString("method").equals("link"))throw new IOException("Некорректная папка источника.");url=SourceUrls.server(base,node.getString("url"));}
        url=parameters(url,query);return LampacParser.browse(request(base,url,config.optString("lampac_token")),base,providers);
    }
    public Media.Playback resolve(Media.Query query,String reference)throws Exception{
        if(reference==null||reference.length()>65536)throw new IOException("Некорректное видео источника.");JSONObject config=config();URI base=SourceUrls.endpoint(config.getString("lampac_url"));JSONObject node=new JSONObject(reference),value=node;
        String method=node.optString("method");if(method.equals("call")){value=new JSONObject(request(base,SourceUrls.server(base,node.getString("url")),config.optString("lampac_token")));for(String key:new String[]{"title","quality","headers","voice_name"})if(!value.has(key)&&node.has(key))value.put(key,node.get(key));}
        else if(!method.equals("play"))throw new IOException("Источник не предоставил видео для воспроизведения.");
        return LampacParser.playback(value,base,node.optString("voice_name",""));
    }
    static String parameters(String url,Media.Query query)throws Exception{
        // KrasView IDs are not TMDB/Kinopoisk IDs. Search by names and year only.
        LinkedHashMap<String,String> values=new LinkedHashMap<>();values.put("title",query.title);values.put("original_title",query.originalTitle);values.put("year",query.year);values.put("serial",query.section.equals("series")||query.section.equals("anime")?"1":"0");
        String raw=new URI(url).getRawQuery();Set<String> present=new HashSet<>();if(raw!=null)for(String pair:raw.split("&"))present.add(pair.split("=",2)[0]);for(Map.Entry<String,String> entry:values.entrySet())if(!present.contains(entry.getKey())&&entry.getValue()!=null&&!entry.getValue().isEmpty())url+=(url.contains("?")?"&":"?")+entry.getKey()+"="+URLEncoder.encode(entry.getValue(),"UTF-8");return url;
    }
    static String probe(String endpoint,String token)throws Exception{URI base=SourceUrls.endpoint(endpoint);String result=request(base,base+"/version?type=hash",token).trim();if(result.isEmpty()||result.length()>160||result.contains("<"))throw new IOException("Сервер ответил в неожиданном формате.");return "Сервер доступен";}
    private static String request(URI base,String value,String token)throws Exception{
        String target=SourceUrls.server(base,value);
        for(int redirects=0;redirects<4;redirects++){
            String authenticated=target;if(!token.isEmpty()&&!java.util.regex.Pattern.compile("(?:[?&])token=").matcher(authenticated).find())authenticated+=(authenticated.contains("?")?"&":"?")+"token="+URLEncoder.encode(token,"UTF-8");
            HttpURLConnection c=(HttpURLConnection)new URL(authenticated).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(30000);c.setRequestProperty("Accept","application/json, text/html");c.setRequestProperty("User-Agent","KrasView-TV/0.9");
            try{int status=c.getResponseCode();if(status>=300&&status<400){String location=c.getHeaderField("Location");if(location==null)throw new IOException("Сервер вернул некорректный переход.");target=SourceUrls.server(base,new URI(target).resolve(location).toString());continue;}if(status==401||status==403)throw new IOException("Сервер не разрешил доступ. Проверь токен в настройках источников.");if(status!=200)throw new IOException("Сервер недоступен (HTTP "+status+").");ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>2_000_000)throw new IOException("Слишком большой ответ источника.");bytes.write(b,0,n);}}return bytes.toString("UTF-8");}finally{c.disconnect();}
        }
        throw new IOException("Сервер вернул слишком много переходов.");
    }
}
