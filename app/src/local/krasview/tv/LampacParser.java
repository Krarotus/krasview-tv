package local.krasview.tv;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.io.IOException;
import java.util.*;

/** Lampac's data-json/link/call/play contract, without running online.js or HTML scripts. */
final class LampacParser {
    static void error(JSONObject value)throws IOException{
        if(value.optBoolean("rch"))throw new IOException("Этот провайдер требует браузерного подключения. Выбери другой.");
        if(value.optBoolean("accsdb"))throw new IOException("Сервер требует авторизацию. Проверь токен в настройках источников.");
        if(value.has("error")&&!value.isNull("error")&&!value.optString("error").isEmpty()&&!value.optString("error").equals("false"))throw new IOException("Провайдер не предоставил данные. Выбери другой или попробуй позже.");
    }
    static Media.Browse browse(String text,URI base,boolean providers)throws Exception{
        Media.Browse out=new Media.Browse();String data=text.trim();
        if(data.startsWith("{")){JSONObject o=new JSONObject(data);error(o);if(o.has("html"))data=o.getString("html");else if(o.optJSONArray("online")!=null)return providerList(o.getJSONArray("online"),base);else if(o.has("url")){add(out,o,o.optString("title","Видео"),base,false);return out;}else throw new IOException("Неподдерживаемый ответ Lampac.");}
        if(data.startsWith("[")){JSONArray values=new JSONArray(data);if(providers)return providerList(values,base);for(int i=0;i<Math.min(200,values.length());i++){JSONObject o=values.optJSONObject(i);if(o!=null)add(out,o,o.optString("title",o.optString("name","Видео")),base,false);}return out;}
        for(Element e:Jsoup.parse(data).select(".videos__item[data-json], .videos__button[data-json]")){
            if(out.items.size()>=200)break;String raw=e.attr("data-json");if(raw.length()>65536)continue;
            JSONObject o;try{o=new JSONObject(raw);}catch(JSONException ex){continue;}
            if(e.hasAttr("s"))o.put("season",e.attr("s"));if(e.hasAttr("e"))o.put("episode",e.attr("e"));
            add(out,o,e.text(),base,e.hasClass("videos__button"));
        }
        if(out.items.isEmpty())throw new IOException("Нет поддерживаемых вариантов. Возможно, провайдер недоступен или требует браузерный плагин.");return out;
    }
    private static Media.Browse providerList(JSONArray values,URI base)throws Exception{Media.Browse out=new Media.Browse();for(int i=0;i<Math.min(200,values.length());i++){JSONObject o=values.optJSONObject(i);if(o==null||!o.optBoolean("show",true))continue;if(o.optBoolean("rch")){out.notice="Провайдеры с браузерным подключением пока не поддерживаются.";continue;}String url=o.optString("url");if(url.isEmpty())continue;JSONObject folder=new JSONObject().put("method","link").put("url",SourceUrls.server(base,url));add(out,folder,o.optString("name","Провайдер"),base,false);}return out;}
    private static void add(Media.Browse out,JSONObject value,String label,URI base,boolean voice)throws Exception{
        String method=value.optString("method");Media.Kind kind=method.equals("link")?Media.Kind.FOLDER:method.equals("call")?Media.Kind.CALL:method.equals("play")?Media.Kind.PLAY:null;if(kind==null)return;
        String url=value.optString("url");if(url.isEmpty())return;
        try{value.put("url",kind==Media.Kind.PLAY?SourceUrls.media(base,url.split(" or ")[0]):SourceUrls.server(base,url));}catch(IOException ex){out.notice="Некоторые неподдерживаемые ссылки пропущены.";return;}
        if(label.isEmpty())label=value.optString("title","Видео");if(voice)value.put("voice_name",label);if(!value.has("title"))value.put("title",label);
        if(value.toString().length()>65536)return;Media.Item item=new Media.Item(label,value.toString(),kind);item.key=value.optString("season")+":"+value.optString("episode")+":"+value.optString("voice_name")+":"+label;out.items.add(item);
    }
    static Media.Playback playback(JSONObject value,URI base,String voice)throws Exception{
        error(value);Media.Playback out=new Media.Playback();out.title=value.optString("title","");out.duration=value.optInt("duration");
        String name=value.optString("voice_name",value.optString("translate",voice));if(!name.isEmpty())out.audioNames.add(name);
        JSONObject headers=value.optJSONObject("headers");if(headers!=null)for(String key:new String[]{"Referer","Origin","User-Agent"}){String h=headers.optString(key,headers.optString(key.toLowerCase(Locale.ROOT),""));if(!h.isEmpty()&&h.length()<4096&&!h.contains("\r")&&!h.contains("\n"))out.headers.put(key,h);}
        JSONObject quality=value.optJSONObject("quality");if(quality==null)quality=value.optJSONObject("qualitys");HashSet<String> seen=new HashSet<>();
        if(quality!=null){Iterator<String> keys=quality.keys();while(keys.hasNext()&&out.sources.size()<30){String label=keys.next();addSource(out,base,quality.optString(label),label,seen,value.optString("type"));}Collections.sort(out.sources,(a,b)->Integer.compare(b.height,a.height));out.selectableSources=!out.sources.isEmpty();}
        String primary=value.optString("url",value.optString("stream",""));String normalized="";if(!primary.isEmpty())try{normalized=SourceUrls.media(base,primary.split(" or ")[0]);}catch(IOException ignored){}
        boolean found=false;for(int i=0;i<out.sources.size();i++)if(out.sources.get(i).url.equals(normalized)){out.defaultSource=i;found=true;break;}
        if(!found&&!normalized.isEmpty()){out.defaultSource=out.sources.size();addSource(out,base,normalized,out.selectableSources?"Авто":"Исходное",seen,value.optString("type"));found=true;}
        if(!found)for(int i=0;i<out.sources.size();i++)if(out.sources.get(i).height<=1080){out.defaultSource=i;break;}
        if(out.sources.isEmpty())throw new IOException("Источник не предоставил поддерживаемую ссылку на видео.");return out;
    }
    private static void addSource(Media.Playback out,URI base,String value,String label,Set<String> seen,String type){for(String part:value.split(" or "))try{String url=SourceUrls.media(base,part);if(!seen.add(url))continue;Media.Source source=new Media.Source(url,label);try{source.height=Integer.parseInt(label.replaceAll("[^0-9]",""));}catch(Exception ignored){}if(type.equalsIgnoreCase("hls")||type.contains("mpegurl"))source.mimeType="application/x-mpegURL";else if(type.equalsIgnoreCase("dash")||type.contains("dash+xml"))source.mimeType="application/dash+xml";out.sources.add(source);}catch(IOException ignored){}}
}
