(() => {
'use strict';
if(window.__BANC_NATIVE_WRAPPED)return;
const token=String(window.__BANC_NATIVE_TOKEN||'');
const denied=()=>JSON.stringify({ok:false,error:'native bridge unavailable'});
window.AndroidVoice={
 status:()=>window.__BancVoice?__BancVoice.status(token):denied(),
 requestMicPermission:()=>window.__BancVoice?__BancVoice.requestMicPermission(token):false,
 startListening:(language)=>window.__BancVoice?__BancVoice.startListening(token,String(language||'ja-JP')):false,
 stopListening:()=>window.__BancVoice?__BancVoice.stopListening(token):false,
 speak:(text,language,rate,pitch)=>window.__BancVoice?__BancVoice.speak(token,String(text||''),String(language||'ja-JP'),Number(rate||1),Number(pitch||1)):false
};
window.AndroidFiles={
 createDocx:(title,body,filename)=>window.__BancFiles?__BancFiles.createDocx(token,String(title||''),String(body||''),String(filename||'')):denied(),
 shareText:(text,filename,mime)=>window.__BancFiles?__BancFiles.shareText(token,String(text||''),String(filename||''),String(mime||'text/plain')):denied()
};
window.AndroidResearch={
 searchWikipedia:(query,limit)=>window.__BancResearch?__BancResearch.searchWikipedia(token,String(query||''),Number(limit||3)):denied(),
 searchCrossref:(query,limit)=>window.__BancResearch?__BancResearch.searchCrossref(token,String(query||''),Number(limit||3)):denied()
};
window.AndroidDev={
 status:()=>window.__BancDev?__BancDev.status(token):denied(),
 syncAndReload:()=>window.__BancDev?__BancDev.syncAndReload(token):false,
 reload:()=>window.__BancDev?__BancDev.reload(token):false,
 useBundledAndReload:()=>window.__BancDev?__BancDev.useBundledAndReload(token):false,
 rollbackAndReload:()=>window.__BancDev?__BancDev.rollbackAndReload(token):false
};
window.AndroidDiagnostics={
 status:()=>window.__BancDiagnostics?__BancDiagnostics.status(token):denied()
};
window.__BANC_NATIVE_WRAPPED=true;
})();