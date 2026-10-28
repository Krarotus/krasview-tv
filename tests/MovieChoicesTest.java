package local.krasview.tv;
import java.nio.file.*;
import java.util.*;
public final class MovieChoicesTest {
    public static void main(String[] args)throws Exception{
        String page=new String(Files.readAllBytes(Paths.get(args.length>0?args[0]:"tests/fixtures/movie-gallery.html")),java.nio.charset.StandardCharsets.UTF_8);
        List<MovieChoices.Entry> items=MovieChoices.parse(page);
        if(items.size()!=2||items.get(0).id!=479455||items.get(0).duration!=6074||!items.get(0).title.equals("Фильм")||items.get(1).id!=278615||items.get(1).duration!=141||!items.get(1).title.equals("Трейлер"))throw new AssertionError("Wrong Ralph gallery");
        if(!items.get(0).image.equals("https://image.krasview.ru/video/160_fixture_film/360_3.jpg")||!items.get(1).image.equals("https://image.krasview.ru/video/fixture_trailer/360.jpg"))throw new AssertionError("Per-video preview images");
        String noisy="<aside><li id='v-123'><a href='/video/123-other'>Recommendation</a></li></aside>"+page;
        if(MovieChoices.parse(noisy).size()!=2)throw new AssertionError("Recommendations must not enter the movie list");
        if(MovieChoices.parse("<div id='sidebar'><ul class='video-gallery'><li id='v-1'>noise</li></ul></div>").size()!=0)throw new AssertionError("Wrong gallery scope");
        if(MovieChoices.parse("<div id='movie-content'><div>Player</div></div><aside><ul class='video-gallery'><li id='v-12'><a href='/video/12-other'></a><div itemprop='name'>Related</div></li></ul></aside>").size()!=0)throw new AssertionError("Gallery outside movie content");
        if(!MovieChoices.shorterThanExpected(6074,90000)||MovieChoices.shorterThanExpected(6074,6073500)||MovieChoices.shorterThanExpected(141,140884)||MovieChoices.shorterThanExpected(0,90000))throw new AssertionError("Duration guard");
        System.out.println("PASS: Ralph film/trailer IDs, labels, durations, gallery isolation and short-preview guard");
    }
}
