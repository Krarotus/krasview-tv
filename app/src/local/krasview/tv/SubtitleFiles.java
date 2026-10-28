package local.krasview.tv;

/** The service's subtitle-file layout; no scripts or arbitrary server URLs. */
final class SubtitleFiles {
    static String mime(String name){String n=name.toLowerCase(java.util.Locale.ROOT);if(n.endsWith(".srt"))return "application/x-subrip";if(n.endsWith(".ass")||n.endsWith(".ssa"))return "text/x-ssa";if(n.endsWith(".vtt"))return "text/vtt";return "";}
    static String url(String server,int cache,String key,String name){
        if(server==null)server="";
        if(key==null||!key.matches("[A-Za-z0-9_-]{1,128}")||name==null||!name.matches("[A-Za-z0-9_-]+\\.(?i:srt|ass|ssa|vtt)")||mime(name).isEmpty())return "";
        if(server.isEmpty())return cache>0&&cache<100000?"https://c"+cache+".krasview.ru/"+key+"/"+name:"";
        return server.matches("[A-Za-z0-9-]{1,32}")?"https://media"+server+".krasview.ru/video/"+key+"/"+name:"";
    }
    static String label(String label,int index){if(label.equalsIgnoreCase("Forced"))return "Надписи и переводы (Forced)";if(label.equalsIgnoreCase("Full"))return "Полные (Full)";return label.isEmpty()?"Субтитры "+(index+1):label;}
}
