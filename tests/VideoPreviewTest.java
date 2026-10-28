package local.krasview.tv;

public final class VideoPreviewTest {
    public static void main(String[] args){
        if(!VideoPreview.url("frame_key",1).endsWith("/frame_key/360.jpg")||!VideoPreview.url("frame_key",3).endsWith("/frame_key/360_3.jpg"))throw new AssertionError("Preview revision");
        for(String key:new String[]{null,"","../invalid","https://example.invalid/frame"})if(!VideoPreview.url(key,1).isEmpty())throw new AssertionError("Invalid frame key");
        System.out.println("PASS: per-video preview URL, revisions and invalid keys");
    }
}
