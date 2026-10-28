package local.krasview.tv;

import java.util.*;

public final class RecommendationRankerTest {
    static RecommendationRanker.Item item(String key,String section,String genres){return new RecommendationRanker.Item(key,section,genres);}
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static List<String> keys(List<RecommendationRanker.Item> items){List<String> keys=new ArrayList<>();for(RecommendationRanker.Item i:items)keys.add(i.key);return keys;}
    public static void main(String[] args){
        List<RecommendationRanker.Item> history=Arrays.asList(item("watched","movie","Детектив · Драма"),item("old-comedy","movie","Комедия"),item("series","series","Боевик"));
        List<RecommendationRanker.Item> pool=Arrays.asList(item("comedy","movie","Комедия"),item("detective","movie","Детектив"),item("both","movie","Детектив, Драма"),item("watched","movie","Детектив"),item("popular","movie","Детектив"),item("wrong-type","series","Детектив"),item("unrelated","movie","Спорт"),item("both","movie","Драма"));
        List<String> chosen=keys(RecommendationRanker.rank(history,pool,new HashSet<>(Arrays.asList("popular")),"movie",12));
        check(chosen.equals(Arrays.asList("both","comedy","detective")),"Genre affinity, stable ties, diversification, history/popular exclusions and deduplication: "+chosen);
        check(RecommendationRanker.rank(history,pool,Collections.emptySet(),"anime",12).isEmpty(),"Do not borrow preferences from other types");
        check(RecommendationRanker.rank(Collections.emptyList(),pool,Collections.emptySet(),"movie",12).isEmpty(),"No fake personalization without history");
        check(RecommendationRanker.rank(history,pool,Collections.emptySet(),"movie",1).size()==1,"Shelf size limit");
        check(RecommendationRanker.rank(history,pool,Collections.emptySet(),"movie",0).isEmpty(),"Empty limit");
        List<RecommendationRanker.Item> recent=Arrays.asList(item("a","movie","Детектив"),item("b","movie","Комедия"));
        check(RecommendationRanker.preferredGenres(recent,"movie").get(0).equals("детектив"),"Recent views have more weight");
        Map<String,Double> once=RecommendationRanker.profile(Collections.singletonList(item("a","movie","Детектив")),"movie");
        check(once.equals(RecommendationRanker.profile(Arrays.asList(item("a","movie","Детектив"),item("a","movie","Детектив")),"movie")),"One title cannot inflate preferences through duplicate history");
        check(RecommendationRanker.genres("  ДРАМА ; Детектив · драма | ПРИКЛЮЧЕНИЯ\nДетектив").size()==3,"Normalize separators, case and duplicate genres");
        check(RecommendationRanker.rank(history,Collections.singletonList(item("","movie","Детектив")),Collections.emptySet(),"movie",12).isEmpty(),"Invalid catalog entries");
        System.out.println("PASS: personal genre ranking, recency, type isolation, cold start, diversity, deduplication and exclusions");
    }
}
