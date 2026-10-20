package jp.tapmarker;
import android.content.Context;import android.security.keystore.*;import java.io.*;import java.security.*;import java.security.cert.*;import java.security.spec.PKCS8EncodedKeySpec;import java.util.Arrays;import javax.crypto.*;import javax.crypto.spec.GCMParameterSpec;
/** Software RSA required by ADB; its persisted representation is encrypted with Android Keystore AES. */
final class WirelessKeys {
 final PrivateKey key;final X509Certificate cert;private static WirelessKeys cached;
 WirelessKeys(PrivateKey k,X509Certificate c){key=k;cert=c;}
 PrivateKey transportKey()throws Exception{byte[] encoded=key.getEncoded();try{return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));}finally{Arrays.fill(encoded,(byte)0);}}
 static synchronized WirelessKeys load(Context context)throws Exception{
  if(cached!=null)return cached;KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);String alias="tapmarker-local-adb-storage";
  if(!store.containsAlias(alias)){KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());generator.generateKey();}
  SecretKey wrapping=(SecretKey)store.getKey(alias,null);File file=new File(context.getNoBackupFilesDir(),"local-adb-keys.enc");
  if(file.isFile()){
   try(DataInputStream in=new DataInputStream(new FileInputStream(file))){if(in.readInt()!=1)throw new IOException("接続鍵の形式が無効です");int ivLength=in.readInt();if(ivLength!=12)throw new IOException("接続鍵を読み取れません");byte[] iv=new byte[ivLength];in.readFully(iv);int size=in.readInt();if(size<16||size>16384)throw new IOException("接続鍵の長さが無効です");byte[] ciphertext=new byte[size];in.readFully(ciphertext);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,wrapping,new GCMParameterSpec(128,iv));byte[] plain=cipher.doFinal(ciphertext);
    try{DataInputStream data=new DataInputStream(new ByteArrayInputStream(plain));int n=data.readInt();if(n<=0||n>8192)throw new IOException("接続鍵が無効です");byte[] pk=new byte[n];data.readFully(pk);PrivateKey key;try{key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pk));}finally{Arrays.fill(pk,(byte)0);}X509Certificate cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(data);cert.checkValidity();cached=new WirelessKeys(key,cert);}finally{Arrays.fill(plain,(byte)0);}}
  }else{
   KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);KeyPair pair=generator.generateKeyPair();X509Certificate cert=CertificateDer.create(pair);byte[] pk=pair.getPrivate().getEncoded();byte[] plain;
   try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream data=new DataOutputStream(bytes);data.writeInt(pk.length);data.write(pk);data.write(cert.getEncoded());plain=bytes.toByteArray();}finally{Arrays.fill(pk,(byte)0);}
   Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,wrapping);byte[] ciphertext;try{ciphertext=cipher.doFinal(plain);}finally{Arrays.fill(plain,(byte)0);}
   File temp=new File(context.getNoBackupFilesDir(),"local-adb-keys.tmp");try(FileOutputStream bytes=new FileOutputStream(temp);DataOutputStream out=new DataOutputStream(bytes)){out.writeInt(1);out.writeInt(cipher.getIV().length);out.write(cipher.getIV());out.writeInt(ciphertext.length);out.write(ciphertext);out.flush();bytes.getFD().sync();}if(!temp.renameTo(file))throw new IOException("接続鍵を保存できません");cached=new WirelessKeys(pair.getPrivate(),cert);
  }
  return cached;
 }
}
