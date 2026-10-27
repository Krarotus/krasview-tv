package local.krasview.tv;
import java.util.*;

interface MediaSource {
    enum Capability {CATALOG,PLAYBACK,BROWSE,ACCOUNT}
    String id();String name();Set<Capability> capabilities();boolean configured();
    Media.Browse browse(Media.Query query,String reference)throws Exception;
    Media.Playback resolve(Media.Query query,String reference)throws Exception;
}
