package local.krasview.tv;
import android.content.Context;
import android.graphics.*;
import android.view.View;

final class IconView extends View {
    private final Paint p=new Paint(3);private String kind;private int ink=0xfff6f3f3;
    IconView(Context c,String name){super(c);kind=name;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void kind(String value){kind=value;invalidate();}
    void ink(int value){ink=value;invalidate();}
    protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();float scale=Math.min(getWidth(),getHeight())/32f;canvas.translate((getWidth()-32*scale)/2,(getHeight()-32*scale)/2);canvas.scale(scale,scale);p.setColor(ink);p.setStrokeWidth(2.1f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);Path a=new Path();
        switch(kind){
            case "menu":for(int y:new int[]{8,16,24})canvas.drawLine(4,y,28,y,p);break;
            case "filter":for(int row=0;row<3;row++){int y=7+row*9,x=row==1?21:11;canvas.drawLine(4,y,x-3,y,p);canvas.drawLine(x+3,y,28,y,p);canvas.drawCircle(x,y,3,p);}break;
            case "back":a.moveTo(21,5);a.lineTo(10,16);a.lineTo(21,27);canvas.drawPath(a,p);break;
            case "search":canvas.drawCircle(13,13,9,p);canvas.drawLine(20,20,28,28,p);break;
            case "person":canvas.drawCircle(16,10,5,p);canvas.drawArc(5,17,27,33,180,180,false,p);break;
            case "play":p.setStyle(Paint.Style.FILL);a.moveTo(11,6);a.lineTo(26,16);a.lineTo(11,26);a.close();canvas.drawPath(a,p);break;
            case "pause":p.setStyle(Paint.Style.FILL);canvas.drawRoundRect(8,6,13,26,1,1,p);canvas.drawRoundRect(19,6,24,26,1,1,p);break;
            case "rewind":case "forward":boolean right=kind.equals("forward");p.setStyle(Paint.Style.FILL);for(int i=0;i<2;i++){int x=5+i*12;a.reset();a.moveTo(right?x:x+10,7);a.lineTo(right?x+10:x,16);a.lineTo(right?x:x+10,25);a.close();canvas.drawPath(a,p);}break;
            case "bookmark":case "saved":a.moveTo(8,4);a.lineTo(24,4);a.lineTo(24,28);a.lineTo(16,22);a.lineTo(8,28);a.close();if(kind.equals("saved"))p.setStyle(Paint.Style.FILL);canvas.drawPath(a,p);break;
            case "home":a.moveTo(3,14);a.lineTo(16,3);a.lineTo(29,14);a.moveTo(6,12);a.lineTo(6,28);a.lineTo(26,28);a.lineTo(26,12);canvas.drawPath(a,p);break;
            case "movie":canvas.drawRoundRect(4,10,28,28,2,2,p);canvas.drawRect(4,3,28,10,p);for(int x=10;x<28;x+=8)canvas.drawLine(x,3,x-4,10,p);break;
            case "tv":canvas.drawRoundRect(3,8,29,26,3,3,p);canvas.drawLine(11,3,16,8,p);canvas.drawLine(21,3,16,8,p);canvas.drawLine(11,30,21,30,p);break;
            case "cartoon":canvas.drawCircle(16,16,12,p);canvas.drawCircle(12,13,1,p);canvas.drawCircle(21,13,1,p);canvas.drawArc(10,13,23,24,15,150,false,p);break;
            case "catalog":canvas.drawRoundRect(4,10,28,28,2,2,p);canvas.drawLine(7,5,25,5,p);canvas.drawLine(10,1,22,1,p);break;
            case "anime":canvas.drawLine(4,7,28,7,p);canvas.drawLine(7,12,25,12,p);canvas.drawLine(11,7,8,29,p);canvas.drawLine(21,7,24,29,p);canvas.drawLine(10,20,22,20,p);break;
            case "history":canvas.drawCircle(17,17,11,p);canvas.drawLine(17,10,17,17,p);canvas.drawLine(17,17,23,20,p);canvas.drawLine(3,6,3,14,p);canvas.drawLine(3,14,10,14,p);break;
            case "calendar":canvas.drawRoundRect(4,6,28,29,2,2,p);canvas.drawLine(4,13,28,13,p);canvas.drawLine(10,3,10,9,p);canvas.drawLine(22,3,22,9,p);break;
            case "more":p.setStyle(Paint.Style.FILL);for(int x:new int[]{7,16,25})canvas.drawCircle(x,16,2,p);break;
        }canvas.restore();
    }
}
