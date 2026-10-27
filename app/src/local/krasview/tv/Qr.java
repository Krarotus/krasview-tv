package local.krasview.tv;

import android.graphics.Bitmap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

final class Qr {
    static final String LINK = "https://atoto.ru/pin";
    static Bitmap image(String url,int size) throws Exception {
        BitMatrix matrix=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,size,size);
        int[] pixels=new int[size*size];
        for(int y=0;y<size;y++) for(int x=0;x<size;x++) pixels[y*size+x]=matrix.get(x,y)?0xff000000:0xffffffff;
        return Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
    }
}
