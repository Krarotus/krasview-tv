package local.krasview.tv;
import java.util.*;

/** Provider-independent catalog and playback data. References are opaque to the UI. */
final class Media {
    static class Film {int id;String title,address,image,year,section,genre,rating;boolean demo;}
    static class Page {List<Film> items=new ArrayList<>();boolean more;}
    static class Genre {int id;String name,sections;Genre(int i,String n,String s){id=i;name=n;sections=s;}}
    static class Detail {int id,lastVideo;String title,description,metadata,poster,address,background,year,section,primaryMeta,genres;boolean webRestricted;List<Season> seasons=new ArrayList<>();List<Episode> episodes=new ArrayList<>();List<Genre> genreTags=new ArrayList<>();}
    static class Season {int id;String title;}
    static class Episode {int id,duration;String title,voice;}
    static class Source {String url,label,mimeType="";int height;Source(String u,String l){url=u;label=l;}}
    static class Playback {String title="";int id,duration,defaultSource;boolean selectableSources;List<Source> sources=new ArrayList<>();List<String> audioNames=new ArrayList<>();Map<String,String> headers=new LinkedHashMap<>();}
    static class Query {String title="",originalTitle="",year="",section="movie",address="";int catalogId;}
    enum Kind {FOLDER,CALL,PLAY}
    static class Item {String title,reference,key;Kind kind;Item(String t,String r,Kind k){title=t;reference=r;kind=k;key=t;}}
    static class Browse {List<Item> items=new ArrayList<>();String notice="";}
}
