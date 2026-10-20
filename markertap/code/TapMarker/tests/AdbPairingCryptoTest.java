package io.github.muntashirakon.adb;
import java.util.Arrays;
public final class AdbPairingCryptoTest {
 static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
 static PairingAuthCtx[] peers(byte[] a,byte[] b){PairingAuthCtx alice=PairingAuthCtx.createAlice(a),bob=PairingAuthCtx.createBob(b);check(alice!=null&&bob!=null,"SPAKE peers created");byte[] am=alice.getMsg().clone(),bm=bob.getMsg().clone();check(alice.initCipher(bm)&&bob.initCipher(am),"key exchange");return new PairingAuthCtx[]{alice,bob};}
 public static void main(String[] args)throws Exception{
  byte[] password="123456-with-TLS-exporter".getBytes("UTF-8"),message="local ADB public key".getBytes("UTF-8");
  PairingAuthCtx[] p=peers(password,password);
  check(Arrays.equals(message,p[1].decrypt(p[0].encrypt(message))),"client to server exchange");
  check(Arrays.equals(message,p[0].decrypt(p[1].encrypt(message))),"server to client exchange");
  byte[] altered=p[0].encrypt(message);altered[0]^=1;check(p[1].decrypt(altered)==null,"tampering rejected");p[0].destroy();p[1].destroy();
  check(p[0].encrypt(message)==null&&p[1].decrypt(message)==null,"destroyed context cannot encrypt or decrypt");
  p=peers(password,"wrong-code-with-TLS-exporter".getBytes("UTF-8"));check(p[1].decrypt(p[0].encrypt(message))==null,"wrong pairing code rejected");p[0].destroy();p[1].destroy();
  System.out.println("AdbPairingCryptoTest: cross-peer exchange, tampering, wrong-code and destruction checks passed; Android TLS not tested");
 }
}
