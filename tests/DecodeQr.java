import java.io.File;
import java.net.URI;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.EnumMap;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;

class DecodeQr {
    public static void main(String[] args) throws Exception {
        BufferedImage image=ImageIO.read(new File(args[0]));
        int w=image.getWidth(),h=image.getHeight();
        int[] pixels=image.getRGB(0,0,w,h,null,0,w);
        EnumMap<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER,Boolean.TRUE);
        Result result=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w,h,pixels))),hints);
        URI uri=URI.create(result.getText());
        if(args[1].equals("binding")) {
            if(!result.getText().equals("https://atoto.ru/pin")) throw new AssertionError("Binding QR must open /pin without a code in the path");
            System.out.println("PASS: displayed binding QR opens https://atoto.ru/pin");
        } else {
            if(!uri.getScheme().equals("https")||!uri.getHost().equals("accounts.google.com")) throw new AssertionError("Google QR must open Google's HTTPS page");
            System.out.println("PASS: displayed Google QR opens accounts.google.com over HTTPS");
        }
    }
}
