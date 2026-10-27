package jp.tapmarker;
import java.util.*;

/** A game profile is distinct from the Android package used for launch and input. */
final class GameTarget {
 static final String USAPO="com.kurashiru.usapo",OMISE="com.kurashiru.usagi.omise",BOX="com.kurashiru.box.merge";
 static final String FARM=USAPO+"#farm",BALLOON=USAPO+"#balloon";
 static final String[] ADDED_APPS={OMISE,BOX,USAPO,KaitakuPreset.APP};
 static String packageName(String target){if(FARM.equals(target)||BALLOON.equals(target))return USAPO;if(target.indexOf('#')>=0)throw new IllegalArgumentException("対象ゲームが不明です");return target;}
 static String key(String app,String game){if(!USAPO.equals(app))return app;if("farm".equals(game))return FARM;if("balloon".equals(game))return BALLOON;return app;}
 static String mode(String target){return FARM.equals(target)?"farm":BALLOON.equals(target)?"balloon":"";}
 static String label(String target){return KaitakuPreset.APP.equals(target)?"クラシル開拓うさぎ":FARM.equals(target)?"うさポ：農園うさぎ":BALLOON.equals(target)?"うさポ：風船パンチ":OMISE.equals(target)?"うさぎのお店屋さん":BOX.equals(target)?"ボックスマージ":PoipoiPreset.APP.equals(target)?"ポイポイうさぎ":"";}
 static boolean added(String app){return Arrays.asList(ADDED_APPS).contains(app);}
 static String[] profiles(String app){return USAPO.equals(app)?new String[]{FARM,BALLOON}:new String[]{app};}
 static boolean dailyOrderSupported(String[] targets){Set<String> games=new HashSet<>();for(String target:targets)if(USAPO.equals(packageName(target)))games.add(target);return games.size()<=1;}
 static boolean dailyDefault(String target,boolean configured,String activeUsapo){return configured&&(!USAPO.equals(packageName(target))||target.equals(activeUsapo));}
 static boolean entryCompleted(PhaseEngine engine){PhaseEngine.Watch w=engine.rule(engine.lastCompletedRule);return w!=null&&(w.action==PhaseEngine.CENTER||w.action==PhaseEngine.CURVE||w.action==PhaseEngine.MARKERS||w.action==PhaseEngine.BACK);}
}
