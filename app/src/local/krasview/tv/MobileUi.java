package local.krasview.tv;

import android.view.*;
import android.widget.*;
import android.text.TextUtils;

/** Touch layouts reuse the same catalog, library, cache and playback implementations. */
final class MobileUi {
    static void main(MainActivity a){
        a.root=new FrameLayout(a);a.root.setBackgroundColor(TvActivity.BG);a.setContentView(a.root);
        a.body=new LinearLayout(a);a.body.setOrientation(1);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,-1);bp.topMargin=a.d(64);bp.bottomMargin=a.d(68);a.root.addView(a.body,bp);
        a.status=a.text("",14,TvActivity.MUTED);a.status.setPadding(a.d(16),a.d(8),a.d(16),a.d(8));a.body.addView(a.status);
        a.scroller=new ScrollView(a);a.scroller.setClipToPadding(false);a.scroller.setPadding(0,0,0,a.d(20));a.body.addView(a.scroller,new LinearLayout.LayoutParams(-1,0,1));
        a.gridRows=new LinearLayout(a);a.gridRows.setOrientation(1);a.scroller.addView(a.gridRows);a.posters=new PosterViewport(a,a.scroller);
        // The mobile menu is a modal sheet; it never shifts the catalog off screen.
        a.drawer=new FrameLayout(a);a.drawer.setVisibility(View.GONE);
        LinearLayout top=new LinearLayout(a);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(a.d(8),0,a.d(8),0);top.setBackgroundColor(TvActivity.BG);a.root.addView(top,new FrameLayout.LayoutParams(-1,a.d(64),Gravity.TOP));
        a.menuButton=a.iconButton("menu","Разделы каталога",24);top.addView(a.menuButton,new LinearLayout.LayoutParams(a.d(48),a.d(48)));a.menuButton.setOnClickListener(v->Navigation.show(a));
        a.pageTitle=a.text("Главная",22,TvActivity.TEXT);a.pageTitle.setTypeface(null,1);a.pageTitle.setSingleLine(true);a.pageTitle.setEllipsize(TextUtils.TruncateAt.END);top.addView(a.pageTitle,new LinearLayout.LayoutParams(0,-2,1));
        a.searchButton=a.iconButton("search","Поиск",23);top.addView(a.searchButton,new LinearLayout.LayoutParams(a.d(48),a.d(48)));a.searchButton.setOnClickListener(v->a.editSearch());
        FrameLayout filter=a.iconButton("filter","Фильтр",23);top.addView(filter,new LinearLayout.LayoutParams(a.d(48),a.d(48)));filter.setOnClickListener(v->a.openFilter());
        LinearLayout bottom=new LinearLayout(a);bottom.setGravity(Gravity.CENTER_VERTICAL);bottom.setPadding(a.d(4),a.d(4),a.d(4),a.d(4));bottom.setBackgroundColor(TvActivity.PANEL);a.root.addView(bottom,new FrameLayout.LayoutParams(-1,a.d(68),Gravity.BOTTOM));
        String[] icons={"home","search","bookmark","history","person"},names={"Главная","Поиск","Закладки","История","Профиль"};
        for(int i=0;i<names.length;i++){final int tab=i;LinearLayout item=new LinearLayout(a);item.setOrientation(1);item.setGravity(Gravity.CENTER);item.setClickable(true);item.setContentDescription(names[i]);
            IconView icon=new IconView(a,icons[i]);item.addView(icon,new LinearLayout.LayoutParams(a.d(23),a.d(23)));TextView label=a.text(names[i],10,TvActivity.TEXT);label.setPadding(0,a.d(5),0,0);item.addView(label);bottom.addView(item,new LinearLayout.LayoutParams(0,-1,1));
            item.setOnClickListener(v->{if(tab==1)a.editSearch();else if(tab==4)a.account();else{a.selectedMenu=tab==0?0:tab==2?6:7;a.searchMode=false;a.query="";a.sort="top";a.filter=new CatalogFilter();a.lastRow=a.lastColumn=0;a.load(false);}});
        }
        // Kept for the shared TV clock callback, which is not scheduled for mobile.
        a.timeView=a.text("",12,TvActivity.TEXT);a.dateView=a.text("",12,TvActivity.TEXT);
    }

    static void detail(DetailActivity a){
        a.root.removeAllViews();ScrollView page=new ScrollView(a);page.setFillViewport(true);a.root.addView(page,new FrameLayout.LayoutParams(-1,-1));LinearLayout flow=new LinearLayout(a);flow.setOrientation(1);flow.setPadding(0,0,0,a.d(24));page.addView(flow);
        FrameLayout hero=new FrameLayout(a);flow.addView(hero,new LinearLayout.LayoutParams(-1,a.d(254)));
        ImageView backdrop=new ImageView(a);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);backdrop.setAlpha(.35f);hero.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));Art.load(a,a.detail.background.isEmpty()?a.detail.poster:a.detail.background,backdrop,true);
        ImageView poster=new ImageView(a);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);poster.setBackground(a.shape(TvActivity.PANEL,0));poster.setClipToOutline(true);FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(a.d(122),a.d(183),Gravity.BOTTOM|Gravity.LEFT);pp.leftMargin=a.d(20);pp.bottomMargin=a.d(16);hero.addView(poster,pp);Art.load(a,a.detail.poster,poster,false);
        TextView back=a.button("← Назад");FrameLayout.LayoutParams backLp=new FrameLayout.LayoutParams(-2,a.d(48),Gravity.TOP|Gravity.LEFT);backLp.leftMargin=a.d(12);backLp.topMargin=a.d(8);hero.addView(back,backLp);back.setOnClickListener(v->a.finish());
        LinearLayout info=new LinearLayout(a);info.setOrientation(1);info.setPadding(a.d(20),a.d(16),a.d(20),0);flow.addView(info);
        TextView name=a.text(a.title(),28,TvActivity.TEXT);name.setTypeface(null,1);info.addView(name);
        TextView meta=a.text(a.detail.primaryMeta,14,TvActivity.MUTED);meta.setPadding(0,a.d(10),0,a.d(14));info.addView(meta);
        HorizontalScrollView genres=new HorizontalScrollView(a);genres.setHorizontalScrollBarEnabled(false);LinearLayout tags=new LinearLayout(a);genres.addView(tags);info.addView(genres,new LinearLayout.LayoutParams(-1,a.d(48)));
        for(Api.Genre g:a.detail.genreTags){TextView chip=a.button(g.name);chip.setTextSize(13);chip.setSingleLine(true);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,a.d(44));cp.rightMargin=a.d(8);tags.addView(chip,cp);chip.setOnClickListener(v->Navigation.genre(a,g.id,g.name));}
        LinearLayout actions=new LinearLayout(a);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,a.d(54));ap.topMargin=a.d(20);ap.bottomMargin=a.d(24);info.addView(actions,ap);
        TextView watch=a.button("▶  Смотреть");watch.setBackground(a.shape(TvActivity.TEXT,0));watch.setTextColor(TvActivity.BG);actions.addView(watch,new LinearLayout.LayoutParams(0,-1,1));a.watch=watch;watch.setOnClickListener(v->a.watch());
        TextView bookmark=a.button(LocalLibrary.bookmarked(a,a.address)?"В закладках":"В закладки");bookmark.setTextSize(14);LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(0,-1,1);bl.leftMargin=a.d(10);actions.addView(bookmark,bl);a.bookmark=bookmark;bookmark.setOnClickListener(v->bookmark.setText(LocalLibrary.toggle(a,a.film)?"В закладках":"В закладки"));
        TextView label=a.text("О фильме",19,TvActivity.TEXT);label.setTypeface(null,1);info.addView(label);TextView description=a.text(a.detail.description.isEmpty()?"Описание пока отсутствует.":a.detail.description,16,TvActivity.MUTED);description.setLineSpacing(a.d(5),1);description.setPadding(0,a.d(12),0,0);info.addView(description);
    }

    static void player(PlayerActivity a){
        a.timeline.setMinimumHeight(a.d(48));a.timeline.getLayoutParams().height=a.d(48);
        // Keep the screen awake only during playback; Activity pause still releases the decoder.
        a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout buttons=new LinearLayout(a);buttons.setGravity(Gravity.CENTER);a.controls.addView(buttons,1,new LinearLayout.LayoutParams(-1,a.d(52)));
        String[] names={"← Назад","−10 с","Пауза / ▶","+10 с","Настройки"};
        for(int i=0;i<names.length;i++){final int action=i;TextView b=a.button(names[i]);b.setTextSize(13);b.setPadding(a.d(8),0,a.d(8),0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,a.d(48),1);p.leftMargin=a.d(4);p.rightMargin=a.d(4);buttons.addView(b,p);if(action==2)a.touchPlay=b;
            b.setOnClickListener(v->{if(action==0)a.finish();else if(action==1)a.relative(-10000);else if(action==2)a.toggle();else if(action==3)a.relative(10000);else a.settings();});}
        GestureDetector taps=new GestureDetector(a,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(android.view.MotionEvent e){return true;}
            @Override public boolean onSingleTapConfirmed(android.view.MotionEvent e){if(a.controls.getVisibility()==View.VISIBLE){a.controls.setVisibility(View.GONE);a.main.removeCallbacks(a.hide);}else a.showControls();return true;}
            @Override public boolean onDoubleTap(android.view.MotionEvent e){int direction=e.getX()<a.root.getWidth()/2f?-1:1;a.relative(direction*10000);a.seekBadge.setText(direction<0?"−10 с":"+10 с");a.seekBadge.setVisibility(View.VISIBLE);a.main.removeCallbacks(a.hideSeekBadge);a.main.postDelayed(a.hideSeekBadge,1000);return true;}
        });
        a.surface.setOnTouchListener((v,e)->taps.onTouchEvent(e));
        a.captions.setOnTouchListener((v,e)->taps.onTouchEvent(e));
    }
}
