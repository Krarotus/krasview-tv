package local.krasview.tv;
import android.content.Context;
import java.util.*;

final class SourceRegistry {
    static CatalogSource catalog(Context context){return new KrasViewSource(new Api(context));}
    static List<MediaSource> all(Context context){return Arrays.asList(catalog(context),new LampacSource(context));}
    static List<MediaSource> playback(Context context){ArrayList<MediaSource> out=new ArrayList<>();for(MediaSource source:all(context))if(source.capabilities().contains(MediaSource.Capability.PLAYBACK)&&source.configured())out.add(source);return out;}
    static MediaSource find(Context context,String id){for(MediaSource source:all(context))if(source.id().equals(id))return source;throw new IllegalArgumentException("Неизвестный источник");}
}
