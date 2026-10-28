package local.krasview.tv;

import java.io.*;
import java.nio.file.*;

public final class ArtFilesTest {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        check(ArtFiles.key("https://example.invalid/a.jpg").matches("[a-f0-9]{64}"),"Private safe filename");check(!ArtFiles.key("a").equals(ArtFiles.key("b")),"Different images must not collide");
        check(ArtFiles.sample(270,400,false)==1&&ArtFiles.sample(6000,9000,false)>=16,"Decode near target size");check(ArtFiles.sample(3840,2160,true)==2,"Limit background decode");
        File dir=Files.createTempDirectory("art-cache-test").toFile();try{File old=new File(dir,ArtFiles.key("old")),fresh=new File(dir,ArtFiles.key("fresh")),active=new File(dir,"download-active.part"),abandoned=new File(dir,"download-abandoned.part");Files.write(old.toPath(),new byte[10]);old.setLastModified(1000);Files.write(fresh.toPath(),new byte[10]);fresh.setLastModified(2000);Files.write(active.toPath(),new byte[1]);Files.write(abandoned.toPath(),new byte[1]);abandoned.setLastModified(1000);
            ArtFiles.trim(dir,10,10);check(!old.exists()&&fresh.exists(),"Evict least recently accessed image by byte budget");check(active.exists()&&!abandoned.exists(),"Keep active writes and remove abandoned partials");ArtFiles.trim(dir,100,0);check(!fresh.exists()&&active.exists(),"File count limit");
        }finally{for(File f:dir.listFiles())f.delete();dir.delete();}
        System.out.println("PASS: image keys, decode limits, LRU disk budgets and interrupted downloads");
    }
}
