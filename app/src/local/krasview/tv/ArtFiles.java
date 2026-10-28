package local.krasview.tv;

import java.io.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Encoded image cache, independently bounded from decoded bitmaps in RAM. */
final class ArtFiles {
    static final long MAX_BYTES=32*1024*1024L;static final int MAX_FILES=256;
    static String key(String url){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));StringBuilder key=new StringBuilder();for(byte b:bytes)key.append(String.format(Locale.ROOT,"%02x",b&255));return key.toString();}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    static void trim(File dir){trim(dir,MAX_BYTES,MAX_FILES);}
    static void trim(File dir,long maxBytes,int maxFiles){File[] abandoned=dir.listFiles(f->f.isFile()&&f.getName().startsWith("download-")&&f.getName().endsWith(".part")&&System.currentTimeMillis()-f.lastModified()>600000);if(abandoned!=null)for(File f:abandoned)f.delete();File[] files=dir.listFiles(f->f.isFile()&&f.getName().matches("[a-f0-9]{64}"));if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long size=0;for(File f:files)size+=f.length();int count=files.length;for(File f:files){if(size<=maxBytes&&count<=maxFiles)break;long length=f.length();if(f.delete()){size-=length;count--;}}}
    static int sample(int width,int height,boolean wide){int maxW=wide?1280:360,maxH=wide?720:540,sample=1;while(width/(long)sample>maxW*2L||height/(long)sample>maxH*2L)sample*=2;return sample;}
}
