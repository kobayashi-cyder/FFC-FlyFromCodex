package jp.tapmarker;

/** Identity of the window that produced a screenshot, independent of unrelated UI events. */
final class WindowSnapshotScope {
 final String pkg;final int id,left,top,right,bottom;
 WindowSnapshotScope(String pkg,int id,int left,int top,int right,int bottom){this.pkg=pkg==null?"":pkg;this.id=id;this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
 boolean valid(){return !pkg.isEmpty()&&id>=0&&right>left&&bottom>top;}
 boolean matches(WindowSnapshotScope other){return valid()&&other!=null&&other.valid()&&pkg.equals(other.pkg)&&id==other.id&&left==other.left&&top==other.top&&right==other.right&&bottom==other.bottom;}
}
