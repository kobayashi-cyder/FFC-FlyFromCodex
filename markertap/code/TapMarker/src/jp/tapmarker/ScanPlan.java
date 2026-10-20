package jp.tapmarker;
import java.util.*;
/** Read only the current action's dependencies for preflight; routine reads remain state scoped. */
public final class ScanPlan {
 public static List<PhaseEngine.Region> select(PhaseEngine e,String preflight,boolean usagi){
  if(preflight==null||preflight.isEmpty())return e.activeRegions();
  LinkedHashMap<String,PhaseEngine.Region> result=new LinkedHashMap<>();add(e,result,preflight);
  if(AdCloseAutomaton.handles(preflight))for(PhaseEngine.Region r:e.activeRegions())if(!AdCloseAutomaton.handles(r.id))add(e,result,r.id);
  if(usagi&&(preflight.equals(UsagiPreset.PREFIX+"mission-button")||preflight.equals(UsagiPreset.PREFIX+"amount-ready")))add(e,result,UsagiPreset.PREFIX+"game");
  if(usagi&&preflight.equals(UsagiPreset.PREFIX+"amount-ready"))add(e,result,UsagiPreset.PREFIX+"amount-level");
  if(preflight.equals(CoinPreset.P+"get"))add(e,result,CoinPreset.P+"missions");
  if(preflight.equals(CoinNoAdPolicy.CANCEL)&&CoinNoAdPolicy.optional(e.current))add(e,result,e.current);
  if(preflight.equals(PoipoiPreset.P+"get")||preflight.equals(PoipoiPreset.P+"empty"))add(e,result,PoipoiPreset.P+"missions");
  if(preflight.equals(ShoppingPreset.P+"get"))add(e,result,ShoppingPreset.P+"missions");
  if(preflight.equals(ShoppingPreset.P+"empty")||preflight.equals(ShoppingPreset.P+"customer"))add(e,result,ShoppingPreset.P+"shop");
  if(preflight.equals(ShoppingPreset.P+"boost-ready"))add(e,result,ShoppingPreset.P+"home");
  return new ArrayList<>(result.values());
 }
 static void add(PhaseEngine e,Map<String,PhaseEngine.Region> out,String id){PhaseEngine.Region r=e.regions.get(id);if(r!=null)out.put(id,r);}
}
