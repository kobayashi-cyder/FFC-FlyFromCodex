package com.ffc.banc888.fly;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A single asynchronous request to the user-selected A1111 server. */
final class A1111Client {
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"BANC888-A1111");t.setDaemon(true);return t;});
    private volatile HttpURLConnection connection;
    private String id,state="idle",image,error;
    private boolean closed;

    synchronized String start(String endpoint,String path,String payload) {
        if(closed)return failure("A1111 client closed");
        if("running".equals(state))return failure("A1111 request busy");
        if(!"/sdapi/v1/txt2img".equals(path)&&!"/sdapi/v1/img2img".equals(path))return failure("unsupported A1111 API path");
        try {
            URI uri=new URI(endpoint);
            if(!("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new Exception("A1111 URL must be HTTP(S), without credentials or query");
            if(payload==null||payload.length()>3*1024*1024)throw new Exception("A1111 payload exceeds limit");
            JSONObject body=new JSONObject(payload);
            if(body.optInt("batch_size",1)!=1||body.optInt("n_iter",1)!=1)throw new Exception("one image per request is required");
            int w=body.optInt("width",512),h=body.optInt("height",512);
            if(w<64||h<64||w>1280||h>1280||(long)w*h>921600||body.optInt("steps",20)<1||body.optInt("steps",20)>60)throw new Exception("A1111 generation settings exceed limits");
            id=UUID.randomUUID().toString();state="running";image=null;error=null;
            String requestId=id,url=endpoint.replaceAll("/+$","")+path;
            worker.execute(()->request(requestId,url,payload));return result(false);
        }catch(Exception e){return failure(e.getMessage());}
    }
    private void request(String requestId,String url,String payload) {
        HttpURLConnection current=null;
        try {
            URL target=new URL(url);
            // Plain HTTP is for localhost/LAN installations; public hosts require TLS.
            if("http".equals(target.getProtocol()))for(InetAddress address:InetAddress.getAllByName(target.getHost()))if(!(address.isLoopbackAddress()||address.isSiteLocalAddress()))throw new Exception("public A1111 servers require HTTPS");
            current=(HttpURLConnection)target.openConnection();current.setInstanceFollowRedirects(false);
            current.setConnectTimeout(10000);current.setReadTimeout(120000);current.setRequestMethod("POST");current.setDoOutput(true);current.setRequestProperty("Content-Type","application/json; charset=utf-8");
            synchronized(this){if(!requestId.equals(id)||!"running".equals(state))return;connection=current;}
            byte[] data=payload.getBytes(StandardCharsets.UTF_8);current.setFixedLengthStreamingMode(data.length);
            try(java.io.OutputStream output=current.getOutputStream()){output.write(data);}
            int status=current.getResponseCode();if(status!=200)throw new Exception("A1111 HTTP "+status+(status==404?": start WebUI with --api":status==401?": authentication required":""));
            long deadline=System.nanoTime()+120000000000L;
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream input=current.getInputStream()){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if(bytes.size()+n>8*1024*1024)throw new Exception("A1111 response exceeds 8 MiB");if(System.nanoTime()>deadline)throw new Exception("A1111 response timed out");bytes.write(buffer,0,n);}}
            JSONObject reply=new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));JSONArray images=reply.optJSONArray("images");
            if(images==null||images.length()<1)throw new Exception("A1111 returned no image");String frame=images.getString(0);
            if(frame.startsWith("data:")){int comma=frame.indexOf(',');if(comma<0)throw new Exception("invalid A1111 image");frame=frame.substring(comma+1);}
            if(frame.length()>3*1024*1024)throw new Exception("A1111 image exceeds limit");
            synchronized(this){if(requestId.equals(id)&&"running".equals(state)){image=frame;state="complete";}}
        }catch(Exception e){synchronized(this){if(requestId.equals(id)&&"running".equals(state)){state="failed";error=e.getMessage()==null?"A1111 connection failed":e.getMessage();}}}
        finally{if(current!=null)current.disconnect();synchronized(this){if(connection==current)connection=null;}}
    }
    synchronized String poll(String requestId) {
        if(requestId==null||!requestId.equals(id))return failure("A1111 request not found");return result(true);
    }
    synchronized String cancel(String requestId) {
        if(requestId==null||!requestId.equals(id))return failure("A1111 request not found");state="cancelled";image=null;
        HttpURLConnection current=connection;if(current!=null)current.disconnect();return result(false);
    }
    synchronized void close(){closed=true;state="cancelled";image=null;HttpURLConnection current=connection;if(current!=null)current.disconnect();worker.shutdownNow();}
    private String result(boolean consume) {
        JSONObject out=new JSONObject();try{out.put("ok",!"failed".equals(state));out.put("requestId",id);out.put("status",state);if(error!=null)out.put("error",error);if(image!=null){out.put("image",image);if(consume)image=null;}}catch(Exception ignored){}return out.toString();
    }
    private String failure(String message){JSONObject out=new JSONObject();try{out.put("ok",false);out.put("error",message);}catch(Exception ignored){}return out.toString();}
}
