package local.krasview.tv;
import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

abstract class TvActivity extends Activity {
    static final int BG=0xff160f10,PANEL=0xff251e20,TEXT=0xfffaf7f7,MUTED=0xffbcb4b6,ACCENT=0xffffffff;
    int d(float v) {return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(Bundle b) {super.onCreate(b);getWindow().setFlags(1024,1024);getWindow().getDecorView().setSystemUiVisibility(5894);}
    TextView text(String s,int size,int color) {TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    GradientDrawable shape(int fill,int border) {GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(d(12));if(border!=0)g.setStroke(d(2),border);return g;}
    TextView button(String s) {TextView t=text(s,16,TEXT);t.setFocusable(true);t.setClickable(true);t.setGravity(Gravity.CENTER);t.setPadding(d(18),d(10),d(18),d(10));t.setBackground(shape(PANEL,0));
        t.setOnFocusChangeListener((v,on)->{v.setBackground(shape(on?0xfffaf7f7:PANEL,0));t.setTextColor(on?BG:TEXT);});return t;}
    android.widget.FrameLayout iconButton(String kind,String label,int size){
        android.widget.FrameLayout box=new android.widget.FrameLayout(this);box.setFocusable(true);box.setClickable(true);box.setContentDescription(label);box.setBackground(shape(0x00160f10,0));
        IconView icon=new IconView(this,kind);box.addView(icon,new android.widget.FrameLayout.LayoutParams(d(size),d(size),Gravity.CENTER));
        box.setOnFocusChangeListener((v,on)->{v.setBackground(shape(on?TEXT:0x00160f10,0));icon.ink(on?BG:TEXT);});return box;
    }
    static String time(long millis) {long seconds=Math.max(0,millis/1000);return String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60);}
}
