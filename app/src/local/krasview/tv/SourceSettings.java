package local.krasview.tv;
import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.*;
import javax.crypto.spec.*;

/** App-private AES-GCM settings; never part of bookmarks, logs or backups. */
final class SourceSettings {
    private static final Object LOCK=new Object();private final Context context;
    SourceSettings(Context c){context=c.getApplicationContext();}
    private AtomicFile file(String name){return new AtomicFile(new File(context.getNoBackupFilesDir(),name));}
    private javax.crypto.SecretKey key()throws Exception{byte[] value;try{value=file("source-settings-key").readFully();}catch(FileNotFoundException e){value=new byte[32];new SecureRandom().nextBytes(value);write(file("source-settings-key"),value);}if(value.length!=32)throw new IOException("Invalid source settings key");return new SecretKeySpec(value,"AES");}
    JSONObject read()throws Exception{synchronized(LOCK){byte[] data;try{data=file("source-settings.sealed").readFully();}catch(FileNotFoundException e){return new JSONObject();}if(data.length<29||data.length>32768)throw new IOException("Invalid source settings");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,java.util.Arrays.copyOfRange(data,0,12)));return new JSONObject(new String(cipher.doFinal(java.util.Arrays.copyOfRange(data,12,data.length)),StandardCharsets.UTF_8));}}
    void save(JSONObject value)throws Exception{synchronized(LOCK){Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());byte[] encrypted=cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));ByteArrayOutputStream bytes=new ByteArrayOutputStream();bytes.write(cipher.getIV());bytes.write(encrypted);write(file("source-settings.sealed"),bytes.toByteArray());}}
    private static void write(AtomicFile file,byte[] value)throws Exception{FileOutputStream out=null;try{out=file.startWrite();out.write(value);file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}}
}
