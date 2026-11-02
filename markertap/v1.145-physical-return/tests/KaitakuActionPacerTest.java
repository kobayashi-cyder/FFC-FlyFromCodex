package jp.tapmarker;

public final class KaitakuActionPacerTest {
  private static int checks;
  private static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
  public static void main(String[] args){
    Object engine=new Object(),other=new Object();
    float[] button={.30f,.49f,.70f,.53f};
    check(KaitakuActionPacer.allow(engine,"coin-chance",button,10000),"first candidate immediate");
    check(!KaitakuActionPacer.allow(engine,"coin-chance",button,10600),"duplicate under 750ms suppressed");
    check(KaitakuActionPacer.allow(engine,"coin-chance",button,10750),"first retry allowed");
    check(!KaitakuActionPacer.allow(engine,"coin-chance",button,12200),"second retry too soon");
    check(KaitakuActionPacer.allow(engine,"coin-chance",button,12350),"second retry at 1600ms");
    check(!KaitakuActionPacer.allow(engine,"coin-chance",button,15000),"third retry too soon");
    check(KaitakuActionPacer.allow(engine,"coin-chance",button,15350),"third retry at 3000ms");
    check(!KaitakuActionPacer.allow(engine,"coin-chance",button,19000),"bounded backoff");
    check(KaitakuActionPacer.allow(engine,"coin-chance",button,19850),"retry after bounded wait");
    check(KaitakuActionPacer.allow(other,"coin-chance",button,10001),"independent engine not blocked");
    check(KaitakuActionPacer.allow(engine,"tap-boost",button,19900),"different modal immediate");
    check(KaitakuActionPacer.allow(engine,"tap-boost",button,5),"clock reset immediate");
    check(!KaitakuActionPacer.allow(engine,"tap-boost",button,5),"same tick only once");
    KaitakuActionPacer.reset(engine);
    check(KaitakuActionPacer.allow(engine,"tap-boost",button,6),"modal cleared reset immediately");
    check(!KaitakuActionPacer.allow(engine,"tap-boost",new float[]{.30f,.49f,.70f,.95f},8),"invalid geometry blocked");
    check(!KaitakuActionPacer.allow(null,"tap-boost",button,8),"null engine blocked");
    check(!KaitakuActionPacer.allow(engine,"tap-boost",null,8),"missing target blocked");
    check(KaitakuActionPacer.allow(engine,"tap-boost",new float[]{.34f,.49f,.74f,.53f},10),"new button geometry immediate");
    System.out.println("KaitakuActionPacer: "+checks+" checks passed");
  }
}
