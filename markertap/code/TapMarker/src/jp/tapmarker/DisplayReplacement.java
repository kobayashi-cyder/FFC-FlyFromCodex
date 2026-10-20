package jp.tapmarker;
import java.util.function.BooleanSupplier;

/** Ordered replacement of exactly one owned virtual display. */
final class DisplayReplacement {
 interface Host {void release(int id)throws Exception;int create()throws Exception;void launch(int id)throws Exception;}
 static int replace(int oldId,BooleanSupplier valid,Host host)throws Exception{
  if(oldId<=0)throw new IllegalArgumentException("本体画面は再作成できません");
  int created=-1;
  try{
   require(valid);host.release(oldId);require(valid);created=host.create();
   if(created<=0||created==oldId)throw new IllegalStateException("新しい仮想画面IDを確認できません");
   require(valid);host.launch(created);require(valid);return created;
  }catch(Exception failure){if(created>0)try{host.release(created);}catch(Exception cleanup){failure.addSuppressed(cleanup);}throw failure;}
 }
 private static void require(BooleanSupplier valid){if(!valid.getAsBoolean())throw new IllegalStateException("画面の再作成が中止されました");}
 private DisplayReplacement(){}
}
