package local.krasview.tv;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Versioned public catalog metadata; failed writes never replace a valid snapshot. */
final class HomeCache {
    static final String[] SECTIONS={"movie","series","anime"};
    static final class Item {int id;String address,title,image,year,section,genre;}
    static final class Snapshot {long savedAt;final List<List<Item>> rows=new ArrayList<>();}
    static Snapshot read(File file){if(!file.isFile()||file.length()>1_000_000)return null;try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))){
        if(in.readInt()!=0x4b565448||in.readInt()!=1)return null;Snapshot result=new Snapshot();result.savedAt=in.readLong();if(result.savedAt<=0)return null;
        for(String section:SECTIONS){int count=in.readInt();if(count<1||count>100)return null;List<Item> row=new ArrayList<>();for(int n=0;n<count;n++){Item item=new Item();item.id=in.readInt();item.address=in.readUTF();item.title=in.readUTF();item.image=in.readUTF();item.year=in.readUTF();item.section=in.readUTF();item.genre=in.readUTF();if(!section.equals(item.section)||item.address.isEmpty())return null;row.add(item);}result.rows.add(row);}
        return in.read()==-1?result:null;
    }catch(IOException|RuntimeException ignored){return null;}}
    static void write(File file,Snapshot snapshot)throws IOException{if(snapshot.rows.size()!=3)throw new IOException("Incomplete home snapshot");File parent=file.getParentFile();if(parent==null)throw new IOException("Missing cache directory");parent.mkdirs();File part=File.createTempFile("home-",".part",parent);try{
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(part)))){out.writeInt(0x4b565448);out.writeInt(1);out.writeLong(snapshot.savedAt);for(int r=0;r<3;r++){List<Item> row=snapshot.rows.get(r);if(row.isEmpty()||row.size()>100)throw new IOException("Invalid row size");out.writeInt(row.size());for(Item item:row){if(!SECTIONS[r].equals(item.section)||item.address==null||item.address.isEmpty())throw new IOException("Invalid row item");out.writeInt(item.id);out.writeUTF(item.address);out.writeUTF(item.title);out.writeUTF(item.image);out.writeUTF(item.year);out.writeUTF(item.section);out.writeUTF(item.genre);}}}
        if(part.length()>1_000_000)throw new IOException("Snapshot too large");try{Files.move(part.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(part.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
    }finally{part.delete();}}
}
