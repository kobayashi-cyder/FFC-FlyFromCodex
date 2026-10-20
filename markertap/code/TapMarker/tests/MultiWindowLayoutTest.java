package jp.tapmarker;
public class MultiWindowLayoutTest {
 public static void main(String[] args){int checks=0;for(int n=1;n<=32;n++){int[][] cells=new int[n][];for(int i=0;i<n;i++){cells[i]=MultiWindowLayout.cell(1080,2400,n,i);int[] b=cells[i];if(b[0]<0||b[1]<0||b[2]>1080||b[3]>2400||b[2]<=b[0]||b[3]<=b[1])throw new AssertionError("bounds");checks++;for(int j=0;j<i;j++){int[] a=cells[j];if(b[0]<a[2]&&a[0]<b[2]&&b[1]<a[3]&&a[1]<b[3])throw new AssertionError("overlap");checks++;}}}if(!MultiWindowLayout.splitLabel("分割画面")||!MultiWindowLayout.splitLabel("Split screen")||MultiWindowLayout.splitLabel("すべて消去")||MultiWindowLayout.splitLabel("アプリ情報"))throw new AssertionError("menu");checks+=4;System.out.println("MultiWindowLayoutTest: "+checks+" checks passed");}
}
