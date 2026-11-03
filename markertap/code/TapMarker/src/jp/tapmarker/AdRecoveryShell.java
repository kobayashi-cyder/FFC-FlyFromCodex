package jp.tapmarker;
/** Force-stop permission is restricted to the shipped, named mini-game targets. */
final class AdRecoveryShell {
  static boolean known(String pkg){
    if(pkg==null)return false;
    return pkg.equals("com.kurashiru.kaitaku.usagi")||
      pkg.equals(UsagiPreset.APP)||pkg.equals(FarmPreset.APP)||
      pkg.equals(CoinPreset.APP)||pkg.equals(ShoppingPreset.APP)||
      pkg.equals(BoxPreset.APP)||pkg.equals(PoipoiPreset.APP);
  }
  private AdRecoveryShell(){}
}
