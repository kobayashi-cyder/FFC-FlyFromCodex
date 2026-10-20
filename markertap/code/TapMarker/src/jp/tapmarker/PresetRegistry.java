package jp.tapmarker;
/** One list for app selection, templates and display-local recovery. */
final class PresetRegistry {
 static final String[] APPS={UsagiPreset.APP,FarmPreset.APP,CoinPreset.APP,ShoppingPreset.APP,BoxPreset.APP,PoipoiPreset.APP};
 static boolean supports(String app){for(String known:APPS)if(known.equals(app))return true;return false;}
 static String prefix(String app){return UsagiPreset.APP.equals(app)?UsagiPreset.PREFIX:FarmPreset.APP.equals(app)?FarmPreset.P:CoinPreset.APP.equals(app)?CoinPreset.P:ShoppingPreset.APP.equals(app)?ShoppingPreset.P:BoxPreset.APP.equals(app)?BoxPreset.P:PoipoiPreset.APP.equals(app)?PoipoiPreset.P:"";}
 static String verify(String app){String p=prefix(app);return p.isEmpty()?"":p+"verify";}
 static String asset(String app){return UsagiPreset.APP.equals(app)?"usagi-preset.json":CoinPreset.APP.equals(app)?"coin-preset.json":ShoppingPreset.APP.equals(app)?"shopping-preset.json":BoxPreset.APP.equals(app)?"box-preset.json":PoipoiPreset.APP.equals(app)?"poipoi-preset.json":"farm-preset.json";}
}
