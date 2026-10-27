package local.krasview.tv;
public class SubtitleFilesTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        check(SubtitleFiles.url("7",0,"abc_123","0.ass").equals("https://media7.krasview.ru/video/abc_123/0.ass"));
        check(SubtitleFiles.url("",12,"abc_123","1.srt").equals("https://c12.krasview.ru/abc_123/1.srt"));
        check(SubtitleFiles.url(null,12,"abc_123","1.srt").equals("https://c12.krasview.ru/abc_123/1.srt"));
        check(SubtitleFiles.mime("1.srt").equals("application/x-subrip"));check(SubtitleFiles.mime("0.ass").equals("text/x-ssa"));check(SubtitleFiles.mime("0.ssa").equals("text/x-ssa"));check(SubtitleFiles.mime("0.vtt").equals("text/vtt"));
        check(SubtitleFiles.url("7",0,"abc","../0.srt").isEmpty());check(SubtitleFiles.url("7.evil.example/",0,"abc","0.srt").isEmpty());check(SubtitleFiles.url("",0,"abc","0.srt").isEmpty());check(SubtitleFiles.url("7",0,"abc","script.js").isEmpty());
        check(SubtitleFiles.label("Full",0).contains("Полные"));check(SubtitleFiles.label("English",1).equals("English"));
        System.out.println("PASS: subtitle origins, provider file layouts, formats, labels and path boundaries");
    }
}
