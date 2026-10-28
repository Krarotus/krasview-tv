package local.krasview.tv;

/** One tap is always 10 seconds. Repeats are paced independently of remote firmware. */
final class SeekRamp {
    static int step(long heldMillis){return heldMillis<1600?10000:heldMillis<3500?20000:heldMillis<7000?30000:60000;}
    static final int INTERVAL_MS=400;
    static long target(long position,int delta,long duration){return Math.max(0,Math.min(position+delta,Math.max(0,duration-1000)));}
    static String offset(long millis){long seconds=Math.abs(millis)/1000;return (millis<0?"−":"+")+(seconds>=3600?String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60):String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60));}
}
