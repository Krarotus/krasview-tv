package local.krasview.tv;
import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.widget.ImageView;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

final class Art {
    private static final ExecutorService jobs=Executors.newFixedThreadPool(3);
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final Map<String,Task> pending=new HashMap<>();
    private static final android.util.LruCache<String,Bitmap> cache=new android.util.LruCache<String,Bitmap>(8*1024*1024){protected int sizeOf(String key,Bitmap b){return b.getByteCount();}};
    private static final class Request {
        final String key;final WeakReference<Activity> owner;final WeakReference<ImageView> view;volatile boolean cancelled;
        Request(Activity owner,ImageView view,String key){this.owner=new WeakReference<>(owner);this.view=new WeakReference<>(view);this.key=key;}
        boolean live(){Activity a=owner.get();return !cancelled&&a!=null&&!a.isDestroyed()&&view.get()!=null;}
    }
    static void clear(ImageView target){Object tag=target.getTag();if(tag instanceof Request)((Request)tag).cancelled=true;target.setTag(null);target.setImageDrawable(null);}
    static void load(Activity owner,String url,ImageView target,boolean wide){
        if(url==null||url.isEmpty())return;String key=url+(wide?"#wide":"#poster");Object previous=target.getTag();if(previous instanceof Request&&key.equals(((Request)previous).key)&&!((Request)previous).cancelled)return;
        clear(target);Request request=new Request(owner,target,key);target.setTag(request);Bitmap hit=cache.get(key);if(hit!=null){target.setImageBitmap(hit);return;}
        synchronized(pending){Task task=pending.get(key);if(task!=null){task.requests.add(request);return;}task=new Task(key,url,wide,new File(owner.getCacheDir(),"art-v1"));task.requests.add(request);pending.put(key,task);jobs.execute(task);}
    }
    static void trimMemory(){cache.evictAll();}
    private static final class Task implements Runnable {
        final String key,url;final boolean wide;final File dir;final List<Request> requests=new ArrayList<>();boolean abandoned;
        Task(String key,String url,boolean wide,File dir){this.key=key;this.url=url;this.wide=wide;this.dir=dir;}
        boolean wanted(){synchronized(pending){for(Request r:requests)if(r.live())return true;return false;}}
        @Override public void run(){Bitmap result=null;String source="memory";try{
            if(!wanted()){abandoned=true;return;}result=cache.get(key);if(result==null){File file=new File(dir,ArtFiles.key(url));byte[] bytes=null;
                if(file.isFile())try{bytes=read(new FileInputStream(file));file.setLastModified(System.currentTimeMillis());}catch(InterruptedIOException e){throw e;}catch(IOException ignored){file.delete();}
                if(bytes!=null){source="disk";result=decode(bytes);if(result==null){file.delete();bytes=null;}}
                if(result==null&&wanted()){source="network";bytes=download();if(!wanted()){abandoned=true;return;}result=decode(bytes);if(result!=null)save(file,bytes);}
                if(result!=null){cache.put(key,result);android.util.Log.d("KrasViewTV","Art ready; source="+source+" wide="+wide);}
            }
        }catch(IOException|RuntimeException ignored){}catch(OutOfMemoryError ignored){cache.evictAll();}finally{
            final Bitmap image=result;final List<Request> targets;synchronized(pending){if(pending.get(key)==this)pending.remove(key);targets=new ArrayList<>(requests);}
            main.post(()->{for(Request r:targets){ImageView view=r.view.get();if(!r.live()||view==null||view.getTag()!=r)continue;if(image!=null)view.setImageBitmap(image);else {view.setTag(null);Activity owner=r.owner.get();if(abandoned&&owner!=null)Art.load(owner,url,view,wide);}}});
        }}
        byte[] read(InputStream stream)throws IOException{try(InputStream in=stream;ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){if(!wanted()){abandoned=true;throw new InterruptedIOException();}if(bytes.size()+count>12_000_000)throw new IOException("Image too large");bytes.write(buffer,0,count);}return bytes.toByteArray();}}
        byte[] download()throws IOException{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(10000);try{return read(c.getInputStream());}finally{c.disconnect();}}
        Bitmap decode(byte[] bytes){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);if(o.outWidth<=0||o.outHeight<=0)return null;o.inSampleSize=ArtFiles.sample(o.outWidth,o.outHeight,wide);o.inJustDecodeBounds=false;Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);if(image==null)return null;float scale=Math.min(1,Math.min((wide?1280f:360f)/image.getWidth(),(wide?720f:540f)/image.getHeight()));if(scale<1){Bitmap small=Bitmap.createScaledBitmap(image,Math.max(1,Math.round(image.getWidth()*scale)),Math.max(1,Math.round(image.getHeight()*scale)),true);if(small!=image)image.recycle();image=small;}return image;}
        void save(File file,byte[] bytes){File part=null;try{dir.mkdirs();part=File.createTempFile("download-",".part",dir);try(FileOutputStream out=new FileOutputStream(part)){out.write(bytes);}if(!part.renameTo(file))part.delete();ArtFiles.trim(dir);}catch(IOException ignored){}finally{if(part!=null&&part.exists())part.delete();}}
    }
}
