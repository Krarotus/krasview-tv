package local.krasview.tv;
class SeekRampTest {
 public static void main(String[] args){
  if(SeekRamp.step(0)!=10000||SeekRamp.step(1599)!=10000)throw new AssertionError("A tap must be 10 seconds");
  int previous=0;for(int ms=0;ms<20000;ms+=100){int value=SeekRamp.step(ms);if(value<previous||value>60000)throw new AssertionError("Acceleration must be bounded and monotonic");previous=value;}
  if(SeekRamp.step(2000)<=SeekRamp.step(0)||SeekRamp.step(4000)<=SeekRamp.step(2000))throw new AssertionError("Holding must gradually accelerate");
  if(SeekRamp.INTERVAL_MS<250)throw new AssertionError("Do not follow uncontrolled remote repeat frequency");
  if(SeekRamp.target(10000,-20000,100000)!=0||SeekRamp.target(90000,60000,100000)!=99000||SeekRamp.target(0,10000,0)!=0)throw new AssertionError("Seek bounds");
  if(!SeekRamp.offset(80000).equals("+1:20")||!SeekRamp.offset(-40000).equals("−0:40")||!SeekRamp.offset(3723000).equals("+1:02:03"))throw new AssertionError("Signed seek indicator");
  System.out.println("PASS: 10-second tap, bounded acceleration and paced repeats");
 }
}
