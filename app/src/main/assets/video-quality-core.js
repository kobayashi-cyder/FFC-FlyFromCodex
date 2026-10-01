(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;root.FFCVideoQuality=api})(typeof globalThis!=='undefined'?globalThis:this,function(){
'use strict';
// Small luminance samples: technical quality only, not semantic recognition.
function evaluate(rgba,previous){const n=Math.floor(rgba.length/4);if(!n)return{score:0,pass:false,sample:[]};const sample=new Array(n);let sum=0,squares=0,clipped=0,delta=0;for(let i=0;i<n;i++){const p=i*4,y=(.2126*rgba[p]+.7152*rgba[p+1]+.0722*rgba[p+2])/255;sample[i]=y;sum+=y;squares+=y*y;if(y<.01||y>.99)clipped++;if(previous?.length===n)delta+=Math.abs(y-previous[i])}const mean=sum/n,contrast=Math.sqrt(Math.max(0,squares/n-mean*mean)),change=previous?.length===n?delta/n:0;const score=Math.round(100*Math.max(0,Math.min(1,.4*Math.min(1,contrast/.15)+.3*(1-clipped/n)+.3*(1-Math.min(1,Math.max(0,change-.12)/.5)))));return{score,pass:score>=70,mean,contrast,clipped:clipped/n,change,sample}}
return{evaluate};
});
