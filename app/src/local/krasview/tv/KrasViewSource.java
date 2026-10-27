package local.krasview.tv;
import java.util.*;

final class KrasViewSource implements CatalogSource {
    private final Api api;
    KrasViewSource(Api value){api=value;}
    public String id(){return "krasview";}public String name(){return "KrasView";}
    public Set<Capability> capabilities(){return Collections.unmodifiableSet(EnumSet.of(Capability.CATALOG,Capability.PLAYBACK,Capability.BROWSE,Capability.ACCOUNT));}
    public boolean configured(){return true;}public boolean signedIn(){return api.signedIn();}
    public Media.Page catalog(String s,String q,int p,String sort,boolean cartoons,CatalogFilter f)throws Exception{return api.catalog(s,q,p,sort,cartoons,f);}
    public Media.Detail detail(String address)throws Exception{return api.detail(address);}
    public List<Media.Episode> season(int id)throws Exception{return api.season(id);}
    public List<Media.Genre> genres()throws Exception{return api.genres();}
    public Media.Browse browse(Media.Query query,String reference)throws Exception{
        Media.Browse out=new Media.Browse();List<Media.Episode> episodes;
        if(reference!=null&&reference.startsWith("season:"))episodes=season(Integer.parseInt(reference.substring(7)));
        else{Media.Detail d=detail(query.address);if(!d.seasons.isEmpty()){for(Media.Season s:d.seasons)out.items.add(new Media.Item(s.title,"season:"+s.id,Media.Kind.FOLDER));return out;}episodes=d.episodes;}
        for(Media.Episode e:episodes)out.items.add(new Media.Item(e.title,Integer.toString(e.id),Media.Kind.PLAY));return out;
    }
    public Media.Playback resolve(Media.Query query,String reference)throws Exception{Media.Playback p=api.playback(Integer.parseInt(reference));p.headers.put("Referer","https://hlamer.ru/");return p;}
}
