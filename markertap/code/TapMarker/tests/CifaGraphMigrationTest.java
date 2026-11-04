package jp.tapmarker;
/** Migration assurance: old data is read-only; dense network gets fresh weights. */
public final class CifaGraphMigrationTest {
 static int checks;
 static void eq(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
 public static void main(String[] args){
  PhaseEngine e=CifaAutonomyTest.engine();
  String scope=GameTarget.FARM;
  String legacy=BancGraphIdentity.of(scope,e);
  eq(legacy.equals(CifaGraphIdentity.legacyOf(scope,e)),"must find same legacy graph memory without using its runtime");
  String replacement=CifaGraphIdentity.of(scope,e);
  eq(!replacement.equals(legacy),"new dense model must not reuse historical weight namespace");
  long epoch=e.epoch;e.enter("ready",2000);
  eq(e.epoch!=epoch&&legacy.equals(CifaGraphIdentity.legacyOf(scope,e))&&replacement.equals(CifaGraphIdentity.of(scope,e)),"observation state must not invalidate stored skill scope");
  e.rule("input").threshold=.002f;
  eq(!replacement.equals(CifaGraphIdentity.of(scope,e)),"different graph contract invalidates imported skill scope");
  eq(!legacy.equals(CifaGraphIdentity.legacyOf(scope,e)),"legacy key changes with graph contract");
  System.out.println("CifaGraphMigrationTest: "+checks+" migration and graph isolation checks passed");
 }
}
