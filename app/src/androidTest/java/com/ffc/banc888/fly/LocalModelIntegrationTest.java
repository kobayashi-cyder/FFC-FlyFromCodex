package com.ffc.banc888.fly;
import static org.junit.Assert.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class LocalModelIntegrationTest {
 @Test public void localHttpModelReturnsBoundedChatText() throws Exception {
  LocalModelClient client=new LocalModelClient();AtomicReference<String> failure=new AtomicReference<>();
  try(ServerSocket server=new ServerSocket(0)){
   Thread fixture=new Thread(()->{try(Socket socket=server.accept()){
    BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));String request=reader.readLine();if(!request.startsWith("POST /v1/chat/completions "))throw new Exception("wrong path");int length=0;String line;while(!(line=reader.readLine()).isEmpty())if(line.toLowerCase().startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());char[] data=new char[length];int read=0;while(read<length){int n=reader.read(data,read,length-read);if(n<0)throw new Exception("truncated payload");read+=n;}JSONObject body=new JSONObject(new String(data));if(!"fixture".equals(body.getString("model")))throw new Exception("wrong model");byte[] reply="{\"choices\":[{\"message\":{\"content\":\"fixture response\"}}]}".getBytes(StandardCharsets.UTF_8);socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+reply.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(reply);
   }catch(Exception e){failure.set(e.toString());}});fixture.setDaemon(true);fixture.start();
   JSONObject start=new JSONObject(client.start("http://127.0.0.1:"+server.getLocalPort()+"/v1/chat/completions","{\"model\":\"fixture\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}],\"stream\":false,\"max_tokens\":32}"));assertTrue(start.toString(),start.getBoolean("ok"));String id=start.getString("requestId");JSONObject response=null;long deadline=System.currentTimeMillis()+5000;while(System.currentTimeMillis()<deadline){response=new JSONObject(client.poll(id));if(!"running".equals(response.optString("status")))break;Thread.sleep(20);}assertNull(failure.get());assertNotNull(response);assertEquals(response.toString(),"complete",response.getString("status"));assertEquals("fixture response",response.getString("text"));assertFalse(new JSONObject(client.start("http://user:secret@localhost/v1/chat/completions","{}" )).getBoolean("ok"));
  }finally{client.close();}
 }
}
