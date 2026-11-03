package android.view;
public class View {
 public static final int VISIBLE=0, INVISIBLE=4, GONE=8;
 private int visibility=VISIBLE;
 public void setVisibility(int next){visibility=next;}
 public int getVisibility(){return visibility;}
}
