package local.krasview.tv;

import android.app.Activity;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Bind images only near the visible scroll window, including nested horizontal rows. */
final class PosterViewport implements ViewTreeObserver.OnScrollChangedListener,ViewTreeObserver.OnGlobalLayoutListener {
    static final class Entry {final ImageView view;final String url;Entry(ImageView v,String u){view=v;url=u;}}
    final Activity owner;final ScrollView viewport;final Handler main=new Handler(Looper.getMainLooper());final List<Entry> entries=new ArrayList<>();
    final Rect window=new Rect(),bounds=new Rect();final int[] xy=new int[2];boolean active=true,scheduled;int lastBound=-1,lastReady=-1,lastTotal=-1;
    final Runnable update=()->{scheduled=false;update();};
    PosterViewport(Activity owner,ScrollView viewport){this.owner=owner;this.viewport=viewport;viewport.getViewTreeObserver().addOnScrollChangedListener(this);viewport.getViewTreeObserver().addOnGlobalLayoutListener(this);}
    void add(ImageView view,String url){entries.add(new Entry(view,url));schedule();}
    void schedule(){if(active&&!scheduled){scheduled=true;main.postDelayed(update,32);}}
    void resume(){active=true;schedule();}
    void pause(){active=false;main.removeCallbacks(update);scheduled=false;}
    void releaseImages(){for(Entry e:entries)if(e.view.getTag()!=null||e.view.getDrawable()!=null)Art.clear(e.view);}
    void clear(){releaseImages();entries.clear();lastBound=lastReady=lastTotal=-1;}
    void destroy(){pause();clear();ViewTreeObserver observer=viewport.getViewTreeObserver();if(observer.isAlive()){observer.removeOnScrollChangedListener(this);observer.removeOnGlobalLayoutListener(this);}}
    @Override public void onScrollChanged(){schedule();}
    @Override public void onGlobalLayout(){schedule();}
    void update(){if(!active||!viewport.getGlobalVisibleRect(window))return;float density=owner.getResources().getDisplayMetrics().density;window.inset(-(int)(206*density),-(int)(80*density));int bound=0,ready=0;
        for(Iterator<Entry> iterator=entries.iterator();iterator.hasNext();){Entry e=iterator.next();ImageView view=e.view;if(!view.isAttachedToWindow()){Art.clear(view);iterator.remove();continue;}if(view.getWidth()==0||view.getHeight()==0)continue;
            view.getLocationOnScreen(xy);bounds.set(xy[0],xy[1],xy[0]+view.getWidth(),xy[1]+view.getHeight());if(Rect.intersects(window,bounds)){if(view.getTag()==null)Art.load(owner,e.url,view,false);if(view.getTag()!=null)bound++;if(view.getDrawable()!=null)ready++;}else if(view.getTag()!=null||view.getDrawable()!=null)Art.clear(view);
        }
        if(bound!=lastBound||ready!=lastReady||lastTotal!=entries.size()){lastBound=bound;lastReady=ready;lastTotal=entries.size();android.util.Log.d("KrasViewTV","Poster window; bound="+bound+" decoded="+ready+" total="+entries.size());}
    }
}
