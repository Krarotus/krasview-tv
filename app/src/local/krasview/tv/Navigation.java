package local.krasview.tv;
import android.app.Dialog;
import android.content.Intent;
import android.view.*;
import android.widget.*;

final class Navigation {
    static final String[] LABELS={"Главная","Фильмы","Сериалы","Мультфильмы","Каталог","Аниме","Избранное","История","Расписание"};
    static final String[] ICONS={"home","movie","tv","cartoon","catalog","anime","bookmark","history","calendar"};
    static void go(TvActivity host,int section,boolean search){Intent i=new Intent(host,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);i.putExtra("section",section);i.putExtra("open_search",search);host.startActivity(i);}
    static void genre(TvActivity host,int id,String name){Intent i=new Intent(host,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);i.putExtra("section",4);i.putExtra("genre_id",id);i.putExtra("genre_name",name);host.startActivity(i);}
    static void filter(TvActivity host){Intent i=new Intent(host,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);i.putExtra("section",4);i.putExtra("open_filter",true);host.startActivity(i);}
    static void show(TvActivity host){
        Dialog dialog=new Dialog(host);LinearLayout column=new LinearLayout(host);column.setOrientation(1);column.setPadding(host.d(18),host.d(24),host.d(14),host.d(20));column.setBackgroundColor(TvActivity.BG);
        TextView close=host.button("←  Закрыть меню");column.addView(close,new LinearLayout.LayoutParams(-1,host.d(48)));close.setOnClickListener(v->dialog.dismiss());
        TextView sources=host.button("Источники");column.addView(sources,new LinearLayout.LayoutParams(-1,host.d(48)));sources.setOnClickListener(v->{dialog.dismiss();host.startActivity(new Intent(host,SourcesActivity.class));});
        for(int index=0;index<LABELS.length;index++){final int item=index;TextView b=host.button(LABELS[index]);b.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,host.d(52));p.topMargin=host.d(2);column.addView(b,p);b.setOnClickListener(v->{dialog.dismiss();go(host,item,false);});if(index==4){TextView f=host.button("Фильтр");f.setGravity(Gravity.CENTER_VERTICAL);column.addView(f,p);f.setOnClickListener(v->{dialog.dismiss();filter(host);});}}
        ScrollView scroll=new ScrollView(host);scroll.addView(column);dialog.setContentView(scroll);dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(host.d(250),-1);dialog.getWindow().setGravity(Gravity.LEFT|Gravity.TOP);dialog.show();dialog.getWindow().setLayout(host.d(250),-1);close.requestFocus();
    }
}
