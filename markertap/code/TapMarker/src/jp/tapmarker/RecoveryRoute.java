package jp.tapmarker;

/** Resume the interrupted task, then ask the existing image rules to classify. */
final class RecoveryRoute {
 static String target(String pkg,String state,boolean customized,boolean relative,boolean coinMission){
  if(customized||relative||!PresetRegistry.supports(pkg))return state;
  if(PoipoiPreset.APP.equals(pkg))return PoipoiPreset.recoveryTarget(state);
  if(ShoppingPreset.APP.equals(pkg))return ShoppingPreset.recoveryTarget(state);
  if(CoinPreset.APP.equals(pkg))return CoinPreset.recoveryTarget(coinMission?CoinPreset.P+"mission-ad":state);
  return PresetRegistry.verify(pkg);
 }
 private RecoveryRoute(){}
}
