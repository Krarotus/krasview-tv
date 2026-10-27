package local.krasview.tv;
import java.net.URI;
import java.io.IOException;

/** Keeps API calls and server credentials on the configured Lampac origin. */
final class SourceUrls {
    static URI endpoint(String value)throws IOException{
        try{URI uri=new URI(value.trim());if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getFragment()!=null)throw new Exception();if(!"https".equalsIgnoreCase(uri.getScheme())&&!("http".equalsIgnoreCase(uri.getScheme())&&localHost(uri.getHost())))throw new Exception();return new URI(uri.toString().replaceAll("/+$",""));}catch(Exception e){throw new IOException("Укажи HTTPS-адрес сервера. Для домашней сети разрешён HTTP на частном IP, localhost или домене .local.");}
    }
    static boolean localHost(String host){
        if("localhost".equalsIgnoreCase(host)||"127.0.0.1".equals(host)||host.toLowerCase(java.util.Locale.ROOT).endsWith(".local"))return true;
        // Numeric LAN addresses only: do not resolve arbitrary names into private addresses.
        String[] parts=host.split("\\.");if(parts.length!=4)return false;int[] n=new int[4];
        try{for(int i=0;i<4;i++){if(!parts[i].matches("[0-9]{1,3}"))return false;n[i]=Integer.parseInt(parts[i]);if(n[i]>255)return false;}}catch(NumberFormatException e){return false;}
        return n[0]==10||(n[0]==172&&n[1]>=16&&n[1]<=31)||(n[0]==192&&n[1]==168);
    }
    static boolean sameOrigin(URI a,URI b){return a.getScheme().equalsIgnoreCase(b.getScheme())&&a.getHost()!=null&&a.getHost().equalsIgnoreCase(b.getHost())&&port(a)==port(b);}
    private static int port(URI u){return u.getPort()>=0?u.getPort():"https".equalsIgnoreCase(u.getScheme())?443:80;}
    static String server(URI base,String value)throws IOException{try{URI u=base.resolve(value);String prefix=base.getPath();if(!sameOrigin(base,u)||u.getUserInfo()!=null||u.getFragment()!=null||!(u.getPath().equals(prefix)||u.getPath().startsWith(prefix.endsWith("/")?prefix:prefix+"/")))throw new Exception();return u.toString();}catch(Exception e){throw new IOException("Источник вернул ссылку за пределами настроенного сервера.");}}
    static String media(URI base,String value)throws IOException{try{URI u=base.resolve(value.trim());if(u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null)throw new Exception();if(!"https".equalsIgnoreCase(u.getScheme())&&!("http".equalsIgnoreCase(u.getScheme())&&localHost(u.getHost())))throw new Exception();return u.toString();}catch(Exception e){throw new IOException("Источник вернул неподдерживаемую ссылку на видео.");}}
}
