package jp.tapmarker;
public final class EditHistoryTest {
 static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  EditHistory h=new EditHistory(3);String[] saved={"b"};
  h.record("farm","a","b");h.record("coin","x","y");
  check(h.canUndo("farm")&&h.canUndo("coin")&&!h.canUndo("shopping"),"history is app scoped");
  String result=h.restore("farm","b",false,v->{saved[0]=v;return true;});
  check("a".equals(result)&&"a".equals(saved[0])&&!h.canUndo("farm")&&h.canRedo("farm"),"undo writes before moving stacks");
  check(h.canUndo("coin"),"another app history is retained");
  result=h.restore("farm","a",true,v->{saved[0]=v;return true;});check("b".equals(result)&&h.canUndo("farm")&&!h.canRedo("farm"),"redo restores exact text");
  try{h.restore("farm","b",false,v->false);throw new AssertionError("write must fail");}catch(IllegalStateException expected){checks++;}
  check(h.canUndo("farm")&&!h.canRedo("farm"),"failed write leaves both history stacks unchanged");
  check("a".equals(h.restore("farm","b",false,v->true)),"retry still restores original");
  h.record("farm","a","new");check(!h.canRedo("farm"),"new edit clears only that app redo");
  h.record("farm","new","new");check("a".equals(h.restore("farm","new",false,v->true)),"no-op save does not add history");
  EditHistory bounded=new EditHistory(3);for(int i=0;i<5;i++)bounded.record("app","v"+i,"v"+(i+1));String current="v5";for(int i=4;i>=2;i--){current=bounded.restore("app",current,false,v->true);check(("v"+i).equals(current),"latest entries retained");}
  check(bounded.restore("app",current,false,v->true)==null,"history limit enforced");
  String unicode="報酬を閉じる\\n状態 F52A と Chromebook";
  check(unicode.equals(EditHistory.unpack(EditHistory.pack(unicode))),"compressed checkpoint preserves UTF-8 and line boundaries");
  try{EditHistory.unpack(new byte[]{1,2,3});throw new AssertionError("reject broken checkpoint");}catch(IllegalStateException expected){checks++;}
  EditHistory games=new EditHistory();
  games.record(GameTarget.FARM,"farm original","farm edited");
  games.record(GameTarget.BALLOON,"balloon original","balloon edited");
  check("farm original".equals(games.restore(GameTarget.FARM,"farm edited",false,v->true)),"embedded farm undo uses its own target identity");
  check(games.canUndo(GameTarget.BALLOON)&&!games.canUndo(GameTarget.USAPO),"farm undo neither consumes balloon nor aliases the host package");
  check("balloon original".equals(games.restore(GameTarget.BALLOON,"balloon edited",false,v->true)),"balloon undo restores balloon settings only");
  check(games.canRedo(GameTarget.FARM)&&games.canRedo(GameTarget.BALLOON),"both same-host games retain separate redo histories");
  System.out.println("EditHistoryTest: "+checks+" checks passed");
 }
}
