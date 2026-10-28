package local.krasview.tv;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** A public card link can open a title, never a supplied media URL or private activity. */
public final class LauncherLinkActivity extends Activity {
    @Override public void onCreate(Bundle saved){super.onCreate(saved);Uri uri=getIntent().getData();if(uri!=null&&"krasviewtv".equals(uri.getScheme())&&"film".equals(uri.getHost())&&uri.getPathSegments().size()==1){String address=uri.getPathSegments().get(0);if(address.matches("[\\p{L}\\p{N}_.-]{1,256}")){Intent main=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);main.putExtra("section",0);Intent detail=new Intent(this,DetailActivity.class).putExtra("address",address);startActivities(new Intent[]{main,detail});}}finish();}
}
