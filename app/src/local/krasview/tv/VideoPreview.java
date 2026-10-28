package local.krasview.tv;

/** KrasView's still frame for this video, including revised preview images. */
final class VideoPreview {
    static String url(String key,int revision){
        if(key==null||!key.matches("[A-Za-z0-9_-]{1,128}"))return "";
        return "https://image.krasview.ru/video/"+key+"/360"+(revision>1?"_"+revision:"")+".jpg";
    }
}
