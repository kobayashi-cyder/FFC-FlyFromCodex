package jp.tapmarker;
import java.security.*;import java.security.cert.*;import java.io.*;import java.util.Arrays;
public final class CertificateDerTest {
 public static void main(String[] args)throws Exception{
  KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);KeyPair keys=generator.generateKeyPair();
  X509Certificate cert=CertificateDer.create(keys);cert.verify(keys.getPublic());cert.checkValidity();
  X509Certificate reread=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(cert.getEncoded()));
  if(!Arrays.equals(keys.getPublic().getEncoded(),reread.getPublicKey().getEncoded()))throw new AssertionError("public key roundtrip");
  if(reread.getVersion()!=3||!"SHA256withRSA".equalsIgnoreCase(reread.getSigAlgName()))throw new AssertionError("certificate structure");
  KeyPair other=generator.generateKeyPair();boolean rejected=false;try{reread.verify(other.getPublic());}catch(SignatureException expected){rejected=true;}
  if(!rejected)throw new AssertionError("wrong signing key accepted");
  System.out.println("CertificateDerTest: certificate roundtrip, signature, validity and wrong-key rejection passed");
 }
}
