package jp.tapmarker;
import java.io.*;import java.math.BigInteger;import java.security.*;import java.security.cert.*;import java.text.SimpleDateFormat;import java.util.*;import javax.security.auth.x500.X500Principal;
/** X.509 DER serialization; signing and key generation use the platform cryptographic provider. */
final class CertificateDer {
 static byte[] der(int tag,byte[]...parts)throws IOException{ByteArrayOutputStream value=new ByteArrayOutputStream();for(byte[] p:parts)value.write(p);byte[] bytes=value.toByteArray();ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(tag);int length=bytes.length;if(length<128)out.write(length);else{int count=0;for(int n=length;n>0;n>>>=8)count++;out.write(128|count);for(int i=count-1;i>=0;i--)out.write(length>>>(8*i));}out.write(bytes);return out.toByteArray();}
 static X509Certificate create(KeyPair pair)throws Exception{
  byte[] algorithm=der(0x30,new byte[]{6,9,42,(byte)134,72,(byte)134,(byte)247,13,1,1,11},der(5));
  byte[] name=new X500Principal("CN=TapMarker local ADB").getEncoded();SimpleDateFormat f=new SimpleDateFormat("yyyyMMddHHmmss'Z'",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));long now=System.currentTimeMillis();
  byte[] validity=der(0x30,der(0x18,f.format(new Date(now-86400000L)).getBytes("US-ASCII")),der(0x18,f.format(new Date(now+315360000000L)).getBytes("US-ASCII")));
  byte[] body=der(0x30,der(0xa0,der(2,new byte[]{2})),der(2,new BigInteger(128,new SecureRandom()).add(BigInteger.ONE).toByteArray()),algorithm,name,validity,name,pair.getPublic().getEncoded());
  Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(pair.getPrivate());signature.update(body);
  X509Certificate cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der(0x30,body,algorithm,der(3,new byte[]{0},signature.sign()))));cert.verify(pair.getPublic());cert.checkValidity();return cert;
 }
}
