package local.krasview.tv;
import java.util.*;

interface CatalogSource extends MediaSource {
    boolean signedIn();
    Media.Page catalog(String section,String query,int page,String sort,boolean cartoons,CatalogFilter filter)throws Exception;
    default Media.Page catalog(String section,String query,int page,String sort)throws Exception{return catalog(section,query,page,sort,false,new CatalogFilter());}
    Media.Detail detail(String address)throws Exception;
    List<Media.Episode> season(int id)throws Exception;
    List<Media.Genre> genres()throws Exception;
}
