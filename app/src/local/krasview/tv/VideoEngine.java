package local.krasview.tv;

import android.content.Context;
import android.view.SurfaceView;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.common.text.CueGroup;
import androidx.media3.ui.SubtitleView;
import java.util.*;

/** DASH and MP4 playback share one engine, so quality changes keep audio and position. */
final class VideoEngine {
    interface Listener {void ready();void size(int w,int h);void buffering(boolean value);void ended();void failed();void firstFrame();}
    static final class Choice {
        final TrackGroup group;final int index,ordinal;final String label;
        Choice(TrackGroup g,int i,int o,String l){group=g;index=i;ordinal=o;label=l;}
    }
    private final ExoPlayer player;private final List<String> audioNames;private final List<Api.Subtitle> subtitleFiles;private final SubtitleView subtitleView;
    private boolean notified,subtitleFrameLogged;private Choice videoOverride;
    VideoEngine(Context context,SurfaceView surface,SubtitleView captions,List<String> names,List<Api.Subtitle> subtitles,Listener listener){
        audioNames=names;subtitleFiles=subtitles;subtitleView=captions;
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent("KrasView-TV/0.7 (Android)").setConnectTimeoutMs(12000).setReadTimeoutMs(20000).setDefaultRequestProperties(Collections.singletonMap("Referer","https://hlamer.ru/"));
        player=new ExoPlayer.Builder(context).setMediaSourceFactory(new DefaultMediaSourceFactory(context).setDataSourceFactory(http)).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setPreferredAudioLanguage("ru").setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build());
        player.setVideoSurfaceView(surface);
        player.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){listener.buffering(state==Player.STATE_BUFFERING);if(state==Player.STATE_READY&&!notified){notified=true;listener.ready();}if(state==Player.STATE_ENDED)listener.ended();}
            @Override public void onVideoSizeChanged(VideoSize s){android.util.Log.i("KrasViewTV","Video size="+s.width+"x"+s.height);listener.size(s.width,s.height);}
            @Override public void onPlayerError(PlaybackException e){android.util.Log.w("KrasViewTV","Playback engine error code="+e.errorCode);listener.failed();}
            @Override public void onRenderedFirstFrame(){listener.firstFrame();}
            @Override public void onCues(CueGroup group){subtitleView.setCues(group.cues);if(!group.cues.isEmpty()&&!subtitleFrameLogged){subtitleFrameLogged=true;android.util.Log.i("KrasViewTV","Subtitle cues rendered; count="+group.cues.size());}}
        });
    }
    void open(String url,long position){ArrayList<MediaItem.SubtitleConfiguration> tracks=new ArrayList<>();for(int i=0;i<subtitleFiles.size();i++){Api.Subtitle s=subtitleFiles.get(i);tracks.add(new MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(s.url)).setMimeType(s.mime).setLabel(s.label).setId("external-subtitle-"+i).build());}player.setMediaItem(new MediaItem.Builder().setUri(url).setSubtitleConfigurations(tracks).build());if(position>0)player.seekTo(position);player.prepare();}
    void start(){player.play();}void pause(){player.pause();}boolean isPlaying(){return player.getPlayWhenReady()&&player.getPlaybackState()!=Player.STATE_ENDED;}
    int getCurrentPosition(){return (int)Math.min(Integer.MAX_VALUE,Math.max(0,player.getCurrentPosition()));}
    int getDuration(){return (int)Math.min(Integer.MAX_VALUE,Math.max(0,player.getDuration()));}
    void seekTo(int value){player.seekTo(value);}void release(){player.release();subtitleView.setCues(Collections.emptyList());}
    List<Choice> textChoices(){ArrayList<Choice> result=new ArrayList<>();int ordinal=0;for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==C.TRACK_TYPE_TEXT)for(int i=0;i<group.length;i++){int order=ordinal++;if(!group.isTrackSupported(i))continue;Format f=group.getTrackFormat(i);String name=f.label;if(name==null||name.trim().isEmpty()){name=language(f.language);if(name.isEmpty())name="Субтитры "+(order+1);}result.add(new Choice(group.getMediaTrackGroup(),i,order,name));}return result;}
    boolean textEnabled(){return !player.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);}
    void selectText(Choice choice){subtitleFrameLogged=false;TrackSelectionParameters.Builder b=player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,choice==null);if(choice!=null)b.setOverrideForType(new TrackSelectionOverride(choice.group,choice.index));else subtitleView.setCues(Collections.emptyList());player.setTrackSelectionParameters(b.build());}
    List<Choice> audioChoices(){
        ArrayList<Choice> result=new ArrayList<>();int ordinal=0;
        for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==C.TRACK_TYPE_AUDIO){
            for(int i=0;i<group.length;i++){
                Format f=group.getTrackFormat(i);int sourceIndex=ordinal++;
                if(f.language!=null&&f.language.matches("au[1-9][0-9]*"))try{sourceIndex=Integer.parseInt(f.language.substring(2))-1;}catch(NumberFormatException ignored){}
                if(!group.isTrackSupported(i))continue;
                String name=sourceIndex<audioNames.size()?audioNames.get(sourceIndex):"";
                if(name.isEmpty()&&f.label!=null&&!f.label.trim().isEmpty())name=f.label.trim();
                String language=language(f.language);
                if(name.isEmpty())name="Дорожка "+(sourceIndex+1)+(language.isEmpty()?"":" · "+language);
                result.add(new Choice(group.getMediaTrackGroup(),i,sourceIndex,name));
            }
        }
        return result;
    }
    static String language(String value){
        if(value==null||value.isEmpty()||value.equals("und")||value.startsWith("au"))return "";
        switch(value){case "rus":case "ru":return "Русский";case "eng":case "en":return "Английский";case "ukr":case "uk":return "Украинский";default:return new Locale(value).getDisplayLanguage(new Locale("ru"));}
    }
    List<Choice> videoChoices(){
        ArrayList<Choice> result=new ArrayList<>();HashSet<String> seen=new HashSet<>();
        for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==C.TRACK_TYPE_VIDEO)for(int i=0;i<group.length;i++)if(group.isTrackSupported(i)){
            Format f=group.getTrackFormat(i);String name=resolution(f.width,f.height);if(!seen.add(name))continue;
            result.add(new Choice(group.getMediaTrackGroup(),i,Math.max(f.height,Math.round(f.width*9/16f)),name));
        }
        Collections.sort(result,(a,b)->Integer.compare(b.ordinal,a.ordinal));return result;
    }
    static String resolution(int width,int height){int size=Math.max(height,Math.round(width*9/16f));return size>0?size+"p":"Исходное качество";}
    boolean selected(Choice choice){for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getMediaTrackGroup().equals(choice.group))return group.isTrackSelected(choice.index);return false;}
    int qualitySelection(List<Choice> choices){if(videoOverride==null)return 0;for(int i=0;i<choices.size();i++)if(choices.get(i).group.equals(videoOverride.group)&&choices.get(i).index==videoOverride.index)return i+1;return 0;}
    void selectAudio(Choice choice){player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setOverrideForType(new TrackSelectionOverride(choice.group,choice.index)).build());}
    void selectVideo(Choice choice){videoOverride=choice;TrackSelectionParameters.Builder b=player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO);if(choice!=null)b.setOverrideForType(new TrackSelectionOverride(choice.group,choice.index));player.setTrackSelectionParameters(b.build());}
}
