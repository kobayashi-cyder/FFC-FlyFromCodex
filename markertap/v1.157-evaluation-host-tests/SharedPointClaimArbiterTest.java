package jp.tapmarker;
public final class SharedPointClaimArbiterTest {
  static int n;
  static void ok(boolean a,String why){n++;if(!a)throw new AssertionError(why);}
  public static void main(String[]args){
    Object a=new Object(),b=new Object();
    ok(SharedPointClaimArbiter.begin(a,"receipt-A",100),"first lane owns the receipt window");
    ok(SharedPointClaimArbiter.allowed(a,"receipt-A",200),"original receipt valid");
    ok(!SharedPointClaimArbiter.begin(b,"receipt-B",300),"another lane cannot simultaneously claim shared pt");
    ok(!SharedPointClaimArbiter.allowed(a,"receipt-A",400),"overlap taints the first receipt too");
    ok(!SharedPointClaimArbiter.allowed(b,"receipt-B",400),"no second receipt");
    SharedPointClaimArbiter.release(b,"receipt-B");
    ok(!SharedPointClaimArbiter.allowed(a,"receipt-A",500),"other lane cannot clear conflict");
    SharedPointClaimArbiter.release(a,"receipt-A");
    ok(SharedPointClaimArbiter.begin(b,"receipt-B",600),"new unambiguous episode");
    ok(SharedPointClaimArbiter.allowed(b,"receipt-B",700),"new claim can finish");
    ok(!SharedPointClaimArbiter.allowed(b,"receipt-B",40000),"expired receipt cannot credit late");
    ok(SharedPointClaimArbiter.begin(a,"receipt-C",40001),"expired lease can be replaced");
    SharedPointClaimArbiter.release(a,"receipt-C");
    ok(!SharedPointClaimArbiter.allowed(a,"receipt-C",40002),"release prevents replay");
    System.out.println("SharedPointClaimArbiterTest: "+n+" checks passed");
  }
}
