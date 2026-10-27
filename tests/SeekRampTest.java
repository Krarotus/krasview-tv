package local.krasview.tv;
class SeekRampTest {
 public static void main(String[] args){
  if(SeekRamp.step(0)!=10000||SeekRamp.step(1599)!=10000)throw new AssertionError("A tap must be 10 seconds");
  int previous=0;for(int ms=0;ms<20000;ms+=100){int value=SeekRamp.step(ms);if(value<previous||value>60000)throw new AssertionError("Acceleration must be bounded and monotonic");previous=value;}
  if(SeekRamp.step(2000)<=SeekRamp.step(0)||SeekRamp.step(4000)<=SeekRamp.step(2000))throw new AssertionError("Holding must gradually accelerate");
  if(SeekRamp.INTERVAL_MS<250)throw new AssertionError("Do not follow uncontrolled remote repeat frequency");
  System.out.println("PASS: 10-second tap, bounded acceleration and paced repeats");
 }
}
