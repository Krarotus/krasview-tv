package local.krasview.tv;
import java.util.*;

final class CatalogFilter {
    final LinkedHashMap<Integer,String> genres=new LinkedHashMap<>();
    int from=1900,to=Calendar.getInstance().get(Calendar.YEAR)+1;
    String section="all",sort="top";
    CatalogFilter copy(){CatalogFilter f=new CatalogFilter();f.genres.putAll(genres);f.from=from;f.to=to;f.section=section;f.sort=sort;return f;}
    boolean active(){return !genres.isEmpty()||from!=1900||to!=Calendar.getInstance().get(Calendar.YEAR)+1;}
    String years(){return from==1900&&to==Calendar.getInstance().get(Calendar.YEAR)+1?"Все годы":from==to?Integer.toString(from):from+"–"+to;}
    String summary(){ArrayList<String> parts=new ArrayList<>(genres.values());if(!years().equals("Все годы"))parts.add(years());return android.text.TextUtils.join(" · ",parts);}
}
