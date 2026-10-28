package local.krasview.tv;

import java.util.*;
import java.util.regex.*;

/** Reads only the title's own movie gallery, never recommendations or page scripts. */
final class MovieChoices {
    static final class Entry {int id,duration;String title,image="";}
    static List<Entry> parse(String page){
        ArrayList<Entry> result=new ArrayList<>();
        Matcher section=Pattern.compile("<div\\b[^>]*\\bid\\s*=\\s*(['\"])movie-content\\1",Pattern.CASE_INSENSITIVE).matcher(page);
        if(!section.find())return result;
        int start=page.indexOf('>',section.end())+1,end=-1,depth=1;if(start<=0)return result;
        Matcher divs=Pattern.compile("</?div\\b[^>]*>",Pattern.CASE_INSENSITIVE).matcher(page);divs.region(start,page.length());
        while(divs.find()){if(divs.group().startsWith("</"))depth--;else depth++;if(depth==0){end=divs.start();break;}}
        if(end<start)return result;
        Matcher gallery=Pattern.compile("<ul\\b[^>]*\\bclass\\s*=\\s*(['\"])[^'\"]*\\bvideo-gallery\\b[^'\"]*\\1[^>]*>([\\s\\S]*?)</ul>",Pattern.CASE_INSENSITIVE).matcher(page.substring(start,end));
        if(!gallery.find())return result;
        Matcher items=Pattern.compile("<li\\b[^>]*\\bid\\s*=\\s*(['\"])v-(\\d+)\\1[^>]*>([\\s\\S]*?)</li>",Pattern.CASE_INSENSITIVE).matcher(gallery.group(2));
        HashSet<Integer> seen=new HashSet<>();
        while(items.find()){
            Entry entry=new Entry();try{entry.id=Integer.parseInt(items.group(2));}catch(NumberFormatException e){continue;}
            String item=items.group(3);if(entry.id<=0||!seen.add(entry.id)||!Pattern.compile("/video/"+entry.id+"(?:[-/'\"?#])").matcher(item).find())continue;
            Matcher label=Pattern.compile("<div\\b[^>]*\\bitemprop\\s*=\\s*(['\"])name\\1[^>]*>([\\s\\S]*?)(?:<small\\b|</div>)",Pattern.CASE_INSENSITIVE).matcher(item);
            if(label.find())entry.title=label.group(2).replaceAll("<[^>]*>","").trim();
            if(entry.title==null||entry.title.isEmpty())continue;
            Matcher thumbnail=Pattern.compile("<img\\b[^>]*\\bitemprop\\s*=\\s*(['\"])thumbnailUrl\\1[^>]*>",Pattern.CASE_INSENSITIVE).matcher(item);
            if(thumbnail.find()){
                Matcher src=Pattern.compile("\\bsrc\\s*=\\s*(['\"])(https://image\\.krasview\\.ru/video/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+\\.jpg)\\1",Pattern.CASE_INSENSITIVE).matcher(thumbnail.group());
                if(src.find())entry.image=src.group(2).replaceFirst("/160(?=(_[0-9]+)?\\.jpg$)","/360");
            }
            Matcher duration=Pattern.compile("<div\\b[^>]*\\bclass\\s*=\\s*(['\"])ic\\1[^>]*>\\s*([0-9:]+)\\s*</div>",Pattern.CASE_INSENSITIVE).matcher(item);
            if(duration.find())try{for(String part:duration.group(2).split(":"))entry.duration=Math.addExact(Math.multiplyExact(entry.duration,60),Integer.parseInt(part));}catch(ArithmeticException|NumberFormatException e){entry.duration=0;}
            result.add(entry);
        }
        return result;
    }
    static boolean shorterThanExpected(int expectedSeconds,long actualMs){long expected=expectedSeconds*1000L;return expectedSeconds>0&&actualMs>0&&expected-actualMs>Math.max(30000,expected/20);}
}
