package local.krasview.tv;
import android.app.Activity;
import android.graphics.*;
import android.widget.ImageView;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

final class Art {
    private static final ExecutorService jobs=Executors.newFixedThreadPool(3);
    private static final android.util.LruCache<String,Bitmap> cache=new android.util.LruCache<String,Bitmap>(16*1024*1024){protected int sizeOf(String key,Bitmap b){return b.getByteCount();}};
    static void load(Activity owner,String url,ImageView target,boolean backdrop){
        if(url==null||url.isEmpty())return;String key=url+(backdrop?"#wide":"#poster");target.setTag(key);
        Bitmap hit=cache.get(key);if(hit!=null){target.setImageBitmap(hit);return;}
        jobs.execute(()->{HttpURLConnection c=null;try{
            c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(10000);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>12_000_000)throw new IOException("Image too large");bytes.write(b,0,n);}}
            byte[] encoded=bytes.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);
            options.inJustDecodeBounds=false;options.inSampleSize=1;int wanted=backdrop?1280:360;while(options.outWidth/options.inSampleSize>wanted*2)options.inSampleSize*=2;
            Bitmap image=BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);if(image==null)return;cache.put(key,image);
            owner.runOnUiThread(()->{if(!owner.isDestroyed()&&key.equals(target.getTag()))target.setImageBitmap(image);});
        }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}});
    }
}
