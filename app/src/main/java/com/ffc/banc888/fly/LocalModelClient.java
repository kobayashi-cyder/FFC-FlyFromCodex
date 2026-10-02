package com.ffc.banc888.fly;

import org.json.JSONObject;
import org.json.JSONArray;
import java.net.URI;
import java.net.URL;
import java.net.InetAddress;
import java.net.HttpURLConnection;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Bounded, optional OpenAI-compatible local/LAN model requests. */
final class LocalModelClient {
    private static final class Job { String id,status="running",text,error; volatile HttpURLConnection connection; }
    private final LinkedHashMap<String,Job> jobs=new LinkedHashMap<>();
    private final ExecutorService executor=Executors.newFixedThreadPool(4);
    private boolean closed;
    synchronized String start(String endpoint,String payload) {
        try {
            if(closed)throw new Exception("model client closed");
            URI uri=new URI(endpoint);
            if(!("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!uri.getPath().endsWith("/chat/completions"))throw new Exception("model endpoint must end with /chat/completions and contain no credentials");
            if(payload==null||payload.length()>65536)throw new Exception("model prompt exceeds limit");
            JSONObject body=new JSONObject(payload);JSONArray messages=body.optJSONArray("messages");
            if(messages==null||messages.length()<1||messages.length()>16||body.optBoolean("stream")||body.optInt("max_tokens",2048)>4096)throw new Exception("invalid model request");
            long running=jobs.values().stream().filter(j->"running".equals(j.status)).count();if(running>=4)throw new Exception("model request busy");
            while(jobs.size()>=8){String idle=null;for(Job j:jobs.values())if(!"running".equals(j.status)){idle=j.id;break;}if(idle==null)throw new Exception("model queue full");jobs.remove(idle);}
            Job job=new Job();job.id=UUID.randomUUID().toString();jobs.put(job.id,job);executor.execute(()->request(job,endpoint,payload));return result(job,false);
        }catch(Exception e){return error(e.getMessage());}
    }
    private void request(Job job,String endpoint,String payload) {
        HttpURLConnection connection=null;
        try {
            URL url=new URL(endpoint);if("http".equals(url.getProtocol()))for(InetAddress address:InetAddress.getAllByName(url.getHost()))if(!address.isLoopbackAddress()&&!address.isSiteLocalAddress())throw new Exception("public model servers require HTTPS");
            connection=(HttpURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(120000);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");
            synchronized(this){if(!"running".equals(job.status))return;job.connection=connection;}
            byte[] data=payload.getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(data.length);try(java.io.OutputStream out=connection.getOutputStream()){out.write(data);}
            int code=connection.getResponseCode();if(code!=200)throw new Exception("model HTTP "+code);
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();long deadline=System.nanoTime()+120000000000L;
            try(InputStream input=connection.getInputStream()){byte[] bytes=new byte[8192];int n;while((n=input.read(bytes))!=-1){if(buffer.size()+n>1048576||System.nanoTime()>deadline)throw new Exception("model response exceeds budget");buffer.write(bytes,0,n);}}
            JSONObject response=new JSONObject(buffer.toString(StandardCharsets.UTF_8.name()));JSONArray choices=response.optJSONArray("choices");if(choices==null||choices.length()==0)throw new Exception("model returned no choices");String text=choices.getJSONObject(0).getJSONObject("message").optString("content","");if(text.trim().isEmpty()||text.length()>131072)throw new Exception("model returned empty or oversized text");
            synchronized(this){if("running".equals(job.status)){job.text=text;job.status="complete";}}
        }catch(Exception e){synchronized(this){if("running".equals(job.status)){job.status="failed";job.error=e.getMessage();}}}
        finally{if(connection!=null)connection.disconnect();job.connection=null;}
    }
    synchronized String poll(String id){Job job=jobs.get(id);return job==null?error("model request not found"):result(job,true);}
    synchronized String cancel(String id){Job job=jobs.get(id);if(job==null)return error("model request not found");job.status="cancelled";job.text=null;if(job.connection!=null)job.connection.disconnect();return result(job,false);}
    synchronized void close(){closed=true;for(Job job:jobs.values()){job.status="cancelled";job.text=null;if(job.connection!=null)job.connection.disconnect();}executor.shutdownNow();}
    private String result(Job j,boolean consume){JSONObject out=new JSONObject();try{out.put("ok",!"failed".equals(j.status));out.put("requestId",j.id);out.put("status",j.status);if(j.text!=null){out.put("text",j.text);if(consume)j.text=null;}if(j.error!=null)out.put("error",j.error);}catch(Exception ignored){}return out.toString();}
    private String error(String text){JSONObject out=new JSONObject();try{out.put("ok",false);out.put("error",text);}catch(Exception ignored){}return out.toString();}
}
