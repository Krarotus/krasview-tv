package local.krasview.tv;

/** One tap is always 10 seconds. Repeats are paced independently of remote firmware. */
final class SeekRamp {
    static int step(long heldMillis){return heldMillis<1600?10000:heldMillis<3500?20000:heldMillis<7000?30000:60000;}
    static final int INTERVAL_MS=400;
}
