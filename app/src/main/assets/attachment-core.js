(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.FFCAttachmentCore=api})(typeof globalThis!=='undefined'?globalThis:this,()=>{
'use strict';
const MAX_FILES=4,MAX_BYTES=128*1024,MAX_TEXT=12000;
function name(value){return String(value||'添付資料').replace(/[\r\n\u0000-\u001f]/g,' ').slice(0,120)}
function decode(info,bytes){
 if(bytes.byteLength>MAX_BYTES)throw new Error(name(info.name)+'：128KB以内のファイルを選んでください。');
 const ext=String(info.name||'').split('.').pop().toLowerCase();
 if(!['txt','md','markdown','csv','tsv','json','jsonl','ndjson','xml','yaml','yml','html','htm','css','js','jsx','ts','tsx','py','java','kt','kts','c','h','cpp','hpp','cs','go','rs','rb','php','sh','sql','log'].includes(ext))throw new Error(name(info.name)+'：今回は文章・JSON・CSV・ソースコードに対応しています。PDF・画像・動画は未対応です。');
 let text;try{text=new TextDecoder('utf-8',{fatal:true}).decode(bytes).replace(/^\uFEFF/,'')}catch{throw new Error(name(info.name)+'：UTF-8形式で保存してから添付してください。')}
 if(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(text))throw new Error(name(info.name)+'：バイナリファイルは読み込めません。');
 return{name:name(info.name),type:String(info.type||'text/plain').slice(0,100),size:bytes.byteLength,text:text.slice(0,MAX_TEXT),truncated:text.length>MAX_TEXT};
}
function format(files){return(files||[]).map(f=>'【添付資料：'+name(f.name)+'】'+(f.truncated?'（先頭部分のみ）':'')+'\n'+String(f.text||'').slice(0,MAX_TEXT)+'\n【添付資料ここまで】').join('\n\n')}
return{MAX_FILES,MAX_BYTES,MAX_TEXT,decode,format,name};
});
