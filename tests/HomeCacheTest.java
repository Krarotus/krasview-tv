package local.krasview.tv;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class HomeCacheTest {
    static HomeCache.Snapshot snapshot(){HomeCache.Snapshot s=new HomeCache.Snapshot();s.savedAt=123456;for(String section:HomeCache.SECTIONS){HomeCache.Item i=new HomeCache.Item();i.id=1;i.address=section+"-title";i.title="Название · "+section;i.image="https://example.invalid/poster.jpg";i.year="2026";i.genre="Детектив · Драма";i.section=section;s.rows.add(new ArrayList<>(Arrays.asList(i)));}return s;}
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{File dir=Files.createTempDirectory("home-cache-test").toFile();File file=new File(dir,"home.bin");try{
        check(HomeCache.read(file)==null,"Missing cache");HomeCache.write(file,snapshot());HomeCache.Snapshot read=HomeCache.read(file);check(read!=null&&read.savedAt==123456&&read.rows.size()==3,"Snapshot round trip");check(read.rows.get(1).get(0).title.equals("Название · series")&&read.rows.get(0).get(0).genre.equals("Детектив · Драма"),"Unicode metadata and genres");
        byte[] original=Files.readAllBytes(file.toPath());HomeCache.Snapshot invalid=snapshot();invalid.rows.get(2).clear();try{HomeCache.write(file,invalid);throw new AssertionError("Partial snapshot accepted");}catch(IOException expected){}check(Arrays.equals(original,Files.readAllBytes(file.toPath())),"Failed refresh must preserve previous cache");
        invalid=snapshot();invalid.rows.get(0).get(0).section="series";try{HomeCache.write(file,invalid);throw new AssertionError("Wrong type accepted");}catch(IOException expected){}check(HomeCache.read(file)!=null,"Valid cache must survive wrong row type");
        Files.write(file.toPath(),Arrays.copyOf(original,original.length/2));check(HomeCache.read(file)==null,"Truncated cache must be ignored");
        Files.write(file.toPath(),new byte[]{1,2,3,4});check(HomeCache.read(file)==null,"Unknown format must be ignored");
        HomeCache.write(file,snapshot());try(FileOutputStream append=new FileOutputStream(file,true)){append.write(0);}check(HomeCache.read(file)==null,"Trailing garbage");
        check(dir.listFiles().length==1,"Temporary snapshots must be cleaned up");System.out.println("PASS: home snapshot round trip, genres, corrupt data and atomic preservation on failed refresh");
    }finally{for(File f:dir.listFiles())f.delete();dir.delete();}}
}
