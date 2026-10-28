package local.krasview.tv;

import java.util.*;

/** Local genre affinity. History is newest first; catalog order breaks equal scores. */
final class RecommendationRanker {
    static final class Item {
        final String key,section;final Set<String> genres;
        Item(String key,String section,String genres){this.key=key;this.section=section;this.genres=genres(genres);}
    }
    static Set<String> genres(String value){
        Set<String> result=new LinkedHashSet<>();
        if(value!=null)for(String part:value.split("[,;·|\\n]+")){String name=normalize(part);if(!name.isEmpty())result.add(name);}
        return result;
    }
    static String normalize(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("\\s+"," ");}
    static Map<String,Double> profile(List<Item> history,String section){
        Map<String,Double> weights=new LinkedHashMap<>();Set<String> seen=new HashSet<>();int recent=0;
        for(Item item:history){if(!section.equals(item.section)||item.key.isEmpty()||!seen.add(item.key)||item.genres.isEmpty())continue;
            double weight=1.0/(1+recent++*.35)/Math.sqrt(item.genres.size());
            for(String genre:item.genres)weights.put(genre,weights.getOrDefault(genre,0.0)+weight);
        }
        return weights;
    }
    static List<String> preferredGenres(List<Item> history,String section){
        Map<String,Double> weights=profile(history,section);List<String> result=new ArrayList<>(weights.keySet());
        result.sort((a,b)->Double.compare(weights.get(b),weights.get(a)));return result;
    }
    static List<Item> rank(List<Item> history,List<Item> candidates,Set<String> excluded,String section,int limit){
        Map<String,Double> weights=profile(history,section);List<Item> result=new ArrayList<>();if(weights.isEmpty()||limit<=0)return result;
        Set<String> seen=new HashSet<>(excluded);for(Item item:history)seen.add(item.key);
        List<Item> pool=new ArrayList<>();Map<String,Double> scores=new HashMap<>();
        double total=0;for(double weight:weights.values())total+=weight;
        for(Item item:candidates){if(!section.equals(item.section)||item.key.isEmpty()||!seen.add(item.key))continue;
            double score=0;for(String genre:item.genres)score+=weights.getOrDefault(genre,0.0);
            if(score>0){pool.add(item);scores.put(item.key,score/total);}
        }
        while(!pool.isEmpty()&&result.size()<limit){Item best=null;double bestScore=-Double.MAX_VALUE;
            for(Item item:pool){double similar=0;for(Item selected:result){Set<String> overlap=new HashSet<>(item.genres);overlap.retainAll(selected.genres);Set<String> union=new HashSet<>(item.genres);union.addAll(selected.genres);similar=Math.max(similar,union.isEmpty()?0:overlap.size()/(double)union.size());}
                double score=scores.get(item.key)-.12*similar;if(score>bestScore){best=item;bestScore=score;}
            }
            result.add(best);pool.remove(best);
        }
        return result;
    }
}
