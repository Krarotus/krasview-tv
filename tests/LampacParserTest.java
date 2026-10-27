package local.krasview.tv;
import java.net.URI;
import java.io.IOException;
import org.json.*;

public class LampacParserTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    interface Action {void run()throws Exception;}
    static void rejected(Action action)throws Exception{try{action.run();throw new AssertionError("Expected rejection");}catch(IOException expected){}}
    public static void main(String[] args)throws Exception{
        URI base=SourceUrls.endpoint("https://lampac.example/");
        check(SourceUrls.endpoint("http://10.2.3.4:9118").getPort()==9118);
        check(SourceUrls.endpoint("http://172.16.0.2:9118").getHost().equals("172.16.0.2"));
        rejected(()->SourceUrls.endpoint("http://8.8.8.8:9118"));
        rejected(()->SourceUrls.endpoint("https://user:pass@lampac.example"));
        rejected(()->SourceUrls.endpoint("https://lampac.example?token=secret"));
        rejected(()->SourceUrls.server(base,"https://other.example/call"));
        rejected(()->SourceUrls.server(SourceUrls.endpoint("https://lampac.example/prefix"),"/outside"));
        rejected(()->SourceUrls.media(base,"javascript:alert(1)"));
        check(SourceUrls.server(base,"/lite/test").equals("https://lampac.example/lite/test"));
        Media.Browse providers=LampacParser.browse("[{\"name\":\"Fixture\",\"url\":\"/lite/test\"},{\"name\":\"Relay\",\"url\":\"/lite/relay\",\"rch\":true}]",base,true);
        check(providers.items.size()==1&&!providers.notice.isEmpty());
        Media.Browse folders=LampacParser.browse("<div class='videos__button' data-json='{&quot;method&quot;:&quot;link&quot;,&quot;url&quot;:&quot;/lite/test?s=1&quot;}'>Studio A</div><script>alert(1)</script>",base,false);
        check(folders.items.size()==1&&folders.items.get(0).kind==Media.Kind.FOLDER);
        check(new JSONObject(folders.items.get(0).reference).getString("voice_name").equals("Studio A"));
        Media.Browse episodes=LampacParser.browse("<div class='videos__button active' data-json='{&quot;method&quot;:&quot;link&quot;,&quot;active&quot;:true,&quot;url&quot;:&quot;/lite/test?s=1&quot;}'>Studio A</div><div class='videos__item' data-json='{&quot;method&quot;:&quot;play&quot;,&quot;url&quot;:&quot;https://video.example/1.mp4&quot;}'>Episode 1</div>",base,false);
        check(episodes.items.size()==1&&episodes.notice.equals("Озвучка: Studio A"));
        check(new JSONObject(episodes.items.get(0).reference).getString("voice_name").equals("Studio A"));
        Media.Playback p=LampacParser.playback(new JSONObject("{\"title\":\"Fixture\",\"translate\":\"Studio A\",\"url\":\"https://video.example/720.mp4\",\"quality\":{\"720p\":\"https://video.example/720.mp4\",\"1080p\":\"https://video.example/1080.mp4\"},\"headers\":{\"Referer\":\"https://video.example/\",\"Authorization\":\"not-forwarded\"}}"),base,"");
        check(p.sources.size()==2&&p.selectableSources&&p.defaultSource==1&&p.sources.get(0).height==1080);
        check(p.audioNames.get(0).equals("Studio A")&&p.headers.size()==1);
        rejected(()->LampacParser.playback(new JSONObject("{\"rch\":true}"),base,""));
        rejected(()->LampacParser.playback(new JSONObject("{\"url\":\"http://public.example/video.mp4\"}"),base,""));
        check(SourceJson.query(SourceJson.query(new Media.Query())).section.equals("movie"));
        System.out.println("PASS: Lampac data contract, quality, voices, and URL/credential boundaries");
    }
}
