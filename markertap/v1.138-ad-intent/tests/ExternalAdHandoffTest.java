package jp.tapmarker;

/** Independent regression: no Android system dependencies. */
public final class ExternalAdHandoffTest {
 static int assertions;
 static void expect(boolean result){assertions++;if(!result)throw new AssertionError("assertion "+assertions);}
 static final String GAME="com.kurashiru.nouen.usagi", BROWSER="com.android.chrome", STORE="com.android.vending";
 public static void main(String[] args){
  ExternalAdHandoff h=new ExternalAdHandoff();
  expect(h.observe(GAME,GAME,true,0)==ExternalAdHandoff.Decision.OWN_APP);
  expect(h.observe(GAME,"",true,10)==ExternalAdHandoff.Decision.WAIT);
  expect(h.observe(GAME,"com.android.systemui",true,15)==ExternalAdHandoff.Decision.WAIT);
  expect(h.observe(GAME,BROWSER,true,100)==ExternalAdHandoff.Decision.WAIT);
  expect(h.observe(GAME,BROWSER,true,600)==ExternalAdHandoff.Decision.WAIT);
  expect(h.observe(GAME,BROWSER,true,1300)==ExternalAdHandoff.Decision.READY);
  expect(h.observe(GAME,GAME,true,1500)==ExternalAdHandoff.Decision.OWN_APP);
  expect(h.observe(GAME,BROWSER,true,1510)==ExternalAdHandoff.Decision.WAIT);
  expect(h.observe(GAME,STORE,true,1650)==ExternalAdHandoff.Decision.WAIT);
  expect(h.observe(GAME,STORE,true,2850)==ExternalAdHandoff.Decision.READY);
  expect(h.observe(GAME,STORE,true,2000)==ExternalAdHandoff.Decision.WAIT); // backwards time invalidates episode
  expect(h.observe(GAME,BROWSER,true,2100)==ExternalAdHandoff.Decision.WAIT); // destination changes
  expect(h.observe(GAME,BROWSER,true,4601)==ExternalAdHandoff.Decision.WAIT); // observation gap
  expect(h.observe(GAME,BROWSER,false,4602)==ExternalAdHandoff.Decision.READY); // non-ad legacy
  h.reset();
  expect(h.observe(GAME,BROWSER,true,4700)==ExternalAdHandoff.Decision.WAIT);
  expect(h.observe(GAME,BROWSER,true,4700)==ExternalAdHandoff.Decision.WAIT); // duplicate time
  expect(h.observe(GAME,BROWSER,true,5900)==ExternalAdHandoff.Decision.READY);
  expect(h.observe(GAME,"jp.tapmarker.lite",true,6000)==ExternalAdHandoff.Decision.WAIT);
  expect(h.observe(GAME,BROWSER,true,6100)==ExternalAdHandoff.Decision.WAIT);
  expect(h.observe("com.kurashiru.fue.usagi",BROWSER,true,6120)==ExternalAdHandoff.Decision.WAIT);
  expect(h.observe("com.kurashiru.fue.usagi",BROWSER,true,7320)==ExternalAdHandoff.Decision.READY);
  expect(h.observe("",BROWSER,true,7330)==ExternalAdHandoff.Decision.WAIT);
  System.out.println("ExternalAdHandoffTest: "+assertions+" invariants passed");
 }
}
