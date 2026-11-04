package jp.tapmarker;
/** Compatibility entrypoint for historical UI calls only.
 * All active settings are owned by CIFA; no BANC state is read or written.
 */
@Deprecated
final class BancSettings {
 static void show(AutoPilot pilot){CifaSettings.show(pilot);}
 private BancSettings(){}
}
