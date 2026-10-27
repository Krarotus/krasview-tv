package local.krasview.tv;
import org.json.*;

final class SourceJson {
    static String query(Media.Query q){try{return new JSONObject().put("title",q.title).put("original_title",q.originalTitle).put("year",q.year).put("section",q.section).put("address",q.address).put("catalog_id",q.catalogId).toString();}catch(JSONException e){throw new IllegalArgumentException(e);}}
    static Media.Query query(String value)throws JSONException{JSONObject o=new JSONObject(value==null?"{}":value);Media.Query q=new Media.Query();q.title=o.optString("title");q.originalTitle=o.optString("original_title");q.year=o.optString("year");q.section=o.optString("section","movie");q.address=o.optString("address");q.catalogId=o.optInt("catalog_id");return q;}
    static Media.Query query(Media.Detail d){Media.Query q=new Media.Query();String[] names=d.title.split(" / ",2);q.title=names[0];q.originalTitle=names.length>1?names[1]:"";q.year=d.year;q.section=d.section;q.address=d.address;q.catalogId=d.id;return q;}
}
