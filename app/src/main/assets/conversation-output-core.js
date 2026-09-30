(function(root,factory){
  const api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;
  root.FFCConversationOutput=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
  'use strict';
  const clean=s=>String(s||'').replace(/\s+/g,' ').trim();
  function routeInfo(route,excelCode){
    route=route||{};
    const codes=(route.threadIds||[]).map(id=>excelCode?excelCode(id):String(id)).filter(Boolean);
    const confidence=Math.max(0,Math.min(1,+route.confidence||0));
    return{
      codes,
      target:codes.join(', ')||'—',
      reason:String(route.reason||'unknown'),
      confidence,
      label:'Routing → '+(codes.join(', ')||'—')+' · '+String(route.reason||'unknown')+' · '+Math.round(confidence*100)+'%'
    };
  }
  function isRouteOnly(text){
    const t=clean(text).toLowerCase();
    if(!t)return true;
    if(/^(voice|routing|route)\s*(→|->|:)/i.test(t))return true;
    if(/^スレッド\s*[a-z]+(?:へ|に)?(?:ルーティング|振り分け)/i.test(t))return true;
    return false;
  }
  function display(route,reply,excelCode){
    const r=routeInfo(route,excelCode),body=String(reply||'').trim();
    return{route:r,reply:body,combined:r.label+'\n\n🪰 ハエ\n'+(body||'…')};
  }
  function speech(route,reply,excelCode,{includeRoute=true}={}){
    const r=routeInfo(route,excelCode),body=String(reply||'').trim();
    if(!includeRoute)return body;
    const prefix=r.codes.length?'スレッド'+r.codes.join('、')+'。':'';
    return clean(prefix+body);
  }
  return{clean,routeInfo,isRouteOnly,display,speech};
});