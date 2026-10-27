package local.krasview.tv;
import android.app.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.concurrent.*;

public class SourcesActivity extends TvActivity {
    final ExecutorService network=Executors.newSingleThreadExecutor();final Handler main=new Handler(Looper.getMainLooper());boolean alive=true;
    @Override public void onCreate(Bundle b){super.onCreate(b);render();}
    void render(){LinearLayout column=new LinearLayout(this);column.setOrientation(1);column.setBackgroundColor(BG);column.setPadding(d(32),d(24),d(32),d(28));setContentView(column);TextView back=button("←  Назад");column.addView(back,new LinearLayout.LayoutParams(d(160),d(48)));back.setOnClickListener(v->finish());TextView title=text("Источники",30,TEXT);title.setPadding(0,d(22),0,d(18));column.addView(title);
        for(MediaSource source:SourceRegistry.all(this)){TextView name=text(source.name(),23,TEXT);name.setPadding(0,d(16),0,d(8));column.addView(name);column.addView(text(source.capabilities().contains(MediaSource.Capability.CATALOG)?"Каталог и просмотр · подключён":source.configured()?"Источник просмотра · настроен":"Источник просмотра · сервер не указан",16,MUTED));if(source.id().equals("lampac")){TextView setup=button("Настроить Lampac NG");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(d(360),d(52));lp.topMargin=d(16);column.addView(setup,lp);setup.setOnClickListener(v->setup());setup.requestFocus();}}
        TextView note=text("Для Lampac нужен работающий сервер. Фильмы хранить на нём не требуется.",18,MUTED);note.setPadding(0,d(28),0,0);column.addView(note);
    }
    void setup(){JSONObject saved;try{saved=new SourceSettings(this).read();}catch(Exception e){saved=new JSONObject();}LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(d(24),d(8),d(24),d(16));EditText endpoint=new EditText(this);endpoint.setTextColor(TEXT);endpoint.setSingleLine(true);endpoint.setText(saved.optString("lampac_url"));endpoint.setHint("https://адрес-сервера");endpoint.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);form.addView(endpoint,new LinearLayout.LayoutParams(d(650),d(54)));EditText token=new EditText(this);token.setTextColor(TEXT);token.setSingleLine(true);token.setHint("Токен сервера — если требуется");token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setText(saved.optString("lampac_token"));form.addView(token,new LinearLayout.LayoutParams(-1,d(54)));TextView status=text("Пустой адрес отключает Lampac. Для домашнего сервера можно указать HTTP и его локальный IP.",15,MUTED);status.setPadding(0,d(12),0,0);form.addView(status);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Lampac NG").setView(form).setPositiveButton("Сохранить",null).setNeutralButton("Проверить",null).setNegativeButton("Отмена",null).create();dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{String url=endpoint.getText().toString().trim();if(!url.isEmpty())url=SourceUrls.endpoint(url).toString();String credential=token.getText().toString().trim();if(credential.length()>4096)throw new Exception("Слишком длинный токен.");new SourceSettings(this).save(new JSONObject().put("lampac_url",url).put("lampac_token",url.isEmpty()?"":credential));dialog.dismiss();render();}catch(Exception e){status.setText(e.getMessage()==null?"Не удалось сохранить настройки.":e.getMessage());}});
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{final String url=endpoint.getText().toString().trim(),credential=token.getText().toString().trim();try{SourceUrls.endpoint(url);}catch(Exception e){status.setText(e.getMessage());return;}status.setText("Проверяю сервер…");dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);network.execute(()->{String result;try{result=LampacSource.probe(url,credential);}catch(Exception e){result="Не удалось подключиться. Проверь адрес, доступность сервера и токен.";}final String message=result;main.post(()->{if(alive&&dialog.isShowing()){status.setText(message);dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);}});});});endpoint.requestFocus();
    }
    @Override protected void onDestroy(){alive=false;main.removeCallbacksAndMessages(null);network.shutdownNow();super.onDestroy();}
}
