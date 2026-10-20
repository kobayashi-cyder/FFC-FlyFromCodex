package jp.tapmarker;
import java.util.*;
/** A side action may use the next free input slot without stopping the main tapper. */
public final class InputPriority {
 private final Map<Integer,Set<Object>> waiting=new HashMap<>();
 public void request(int display,Object owner){waiting.computeIfAbsent(display,key->new HashSet<>()).add(owner);}
 public void release(int display,Object owner){Set<Object> owners=waiting.get(display);if(owners!=null){owners.remove(owner);if(owners.isEmpty())waiting.remove(display);}}
 public boolean allows(int display,boolean sideAction){return sideAction||!waiting.containsKey(display);}
}
