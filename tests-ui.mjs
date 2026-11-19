import {spawn} from 'node:child_process';
import {createServer} from 'node:http';
import {mkdtemp,readFile,writeFile,mkdir} from 'node:fs/promises';
import path from 'node:path';import os from 'node:os';import {setTimeout as delay} from 'node:timers/promises';
const tmp=await mkdtemp(path.join(os.tmpdir(),'sp-phone-ui-'));
const server=createServer(async(req,res)=>{try{const p=req.url==='/'?'index.html':req.url.slice(1);if(!['index.html','app.js','style.css','dark.css','local.css','icon.svg'].includes(p)){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',p.endsWith('.js')?'text/javascript':p.endsWith('.css')?'text/css':p.endsWith('.svg')?'image/svg+xml':'text/html');res.end(await readFile('app/src/main/assets/'+p));}catch{res.writeHead(500);res.end();}});
await new Promise(r=>server.listen(43859,'127.0.0.1',r));
const child=spawn('C:/Program Files/Google/Chrome/Application/chrome.exe',['--user-data-dir='+path.join(tmp,'profile'),'--headless','--disable-background-networking','--disable-sync','--no-first-run','--no-default-browser-check','--remote-debugging-pipe','about:blank'],{windowsHide:true,stdio:['ignore','ignore','ignore','pipe','pipe']});
let seq=0,buf='',pending=new Map();child.stdio[4].on('data',c=>{buf+=c;let i;while((i=buf.indexOf('\0'))>=0){const r=JSON.parse(buf.slice(0,i));buf=buf.slice(i+1);if(pending.has(r.id)){pending.get(r.id)(r);pending.delete(r.id)}}});
function cdp(method,params={},sessionId){return new Promise((r,j)=>{const id=++seq;const timer=setTimeout(()=>j(Error('timeout '+method)),15000);pending.set(id,data=>{clearTimeout(timer);data.error?j(Error(data.error.message)):r(data.result)});child.stdio[3].write(JSON.stringify({id,method,params,sessionId})+'\0')})}
try{
const {targetId}=await cdp('Target.createTarget',{url:'about:blank'});const {sessionId}=await cdp('Target.attachToTarget',{targetId,flatten:true});
const ev=async expression=>{const r=await cdp('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true},sessionId);if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value};
const until=async e=>{for(let i=0;i<100;i++){if(await ev(e))return;await delay(100);}throw Error('Check failed '+e+' '+await ev("document.body.innerText+' ERR '+JSON.stringify(window.testErrors)"));};
await cdp('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true},sessionId);
await cdp('Page.enable',{},sessionId);
await cdp('Page.addScriptToEvaluateOnNewDocument',{source:`
window.testErrors=[];window.addEventListener('error',e=>testErrors.push(e.message));window.addEventListener('unhandledrejection',e=>testErrors.push(String(e.reason)));window.requests=[];window.testJobs=[];window.failedCover=false;window.Phone={request(id,method,input){
 const b=JSON.parse(input);requests.push([method,b]);let data={};
 if(method==='catalogue')data={items:Array.from({length:6},(_,i)=>({title:['Trở về thập niên 80','Khánh Dư Niên 3','Vua thợ săn tái sinh','Cực phẩm gia đinh','Thần đồng thức tỉnh','Nữ hoàng Halloween'][i],url:'https://huangguoai.com/video/'+(i+1)+'/',count:i+3,cover:'https://pic.drleao.cn/'+i+'.png'})),next:{home:2}};
 if(method==='image'||method==='localThumbnail'){
  if(b.url?.endsWith('/1.png')&&!window.failedCover){window.failedCover=true;setTimeout(()=>window.nativeReply(id,null,'Lỗi ảnh thử nghiệm'),10);return;}
  data={data:'data:image/svg+xml;base64,'+btoa('<svg xmlns="http://www.w3.org/2000/svg" width="160" height="200"><rect width="160" height="200" fill="#31513e"/><circle cx="80" cy="75" r="30" fill="#8ad9b7"/><path d="M35 145h90" stroke="#8ad9b7" stroke-width="8"/></svg>')};
 }
 if(method==='settings')data={token:'',encoder:'H.265 bằng phần cứng',version:'1.1.0',libraryMode:'device',downloadsEnabled:!!window.gateUnlocked,passwordSet:localStorage.getItem('testPasswordSet')==='true'};
 if(method==='downloadFeatures'){
  if(b.enabled&&window.rejectUnlock){setTimeout(()=>window.nativeReply(id,null,'Mật khẩu không đúng.'),10);return;}
  window.gateUnlocked=!!b.enabled&&!window.cancelUnlock;
  if(window.gateUnlocked)localStorage.setItem('testPasswordSet','true');
  data={enabled:window.gateUnlocked};
 }
 if(['checkUpdates','updateStatus'].includes(method))data=window.testUpdate||{state:'current',message:'Đang dùng bản mới nhất.',busy:false,available:false};
 if(method==='downloadUpdate')data=window.testUpdate={...window.testUpdate,state:'downloading',message:'Đang tải APK · 50%',busy:true,progress:50};
 if(method==='cancelUpdate')data=window.testUpdate={...window.testUpdate,state:'available',message:'Đã hủy tải cập nhật.',busy:false};
 if(method==='installUpdate'){window.installRequested=true;data=window.testUpdate;}
 if(method==='chooseFolder')data={cancelled:!!window.cancelFolder};
 if(method==='localList'){
  const all=[{id:'local-1',title:'Tập 1.mp4',durationMs:65000,size:10485760,date:1000},{id:'local-2',title:'Tập 2.mp4',durationMs:125000,size:20971520,date:2000},{id:'local-10',title:'Tập 10.mp4',durationMs:185000,size:31457280,date:3000}].filter(v=>!b.query||v.title.includes(b.query));
  if(b.sort==='newest')all.sort((a,b)=>b.date-a.date);
  const offset=b.offset||0;data={items:all.slice(offset,offset+2),next:offset+2<all.length?offset+2:-1,total:all.length,mode:b.mode,label:b.mode==='folder'?'Phim trong thư mục':'Video trên máy',limited:b.mode!=='folder',message:''};
 }
 if(method==='episodes')data={domains:{main:'huangguoai.com',fast:window.testFastHost||'1bb4a2.ipycpknh.cc'},cover:window.noSeriesCover?'':'https://pic.drleao.cn/series-cover.jpg',title:'Trở về thập niên 80',url:b.site==='fast'?'https://'+(window.testFastHost||'1bb4a2.ipycpknh.cc')+'/video/1/':'https://huangguoai.com/video/1/',author:{id:'156291',name:'黄果官方'},items:Array.from({length:4},(_,i)=>({id:'hg-1-'+(i+1),title:'Trở về thập niên 80 · Tập '+(i+1),duration:['02:05','02:23','1:02:03',''][i],site:b.site}))};
 if(method==='jobs')data=window.testJobs;
 if(method==='enqueue')window.testJobs.push({id:'job-'+window.testJobs.length,mode:b.mode,title:b.title,state:'running',progress:42,message:'Đang xử lý thử nghiệm',results:[]});
 if(method==='list')data={page:1,more:false,items:[{id:'324360',title:'Phim thử nghiệm',duration:'00:02:00'}]};
 if(method==='pick')data=[{id:'a',name:'Tap 01.mp4'},{id:'b',name:'Tap 02.mp4'}];
 setTimeout(()=>window.nativeReply(id,data,null),10);
}};
`},sessionId);
await cdp('Page.navigate',{url:'http://127.0.0.1:43859/'},sessionId);
await until("!document.getElementById('local').hidden && document.querySelectorAll('.local-video').length===2");
await until("document.querySelectorAll('.bottom button:not([hidden])').length===2 && !requests.some(([m])=>m==='catalogue'||m==='list')");
await until("document.getElementById('localSort').value==='newest' && document.querySelector('.local-video strong').textContent==='Tập 10.mp4'");
await ev("document.getElementById('settingsButton').click();document.getElementById('enableDownloads').click()");
await until("downloadsEnabled && document.querySelectorAll('.bottom button:not([hidden])').length===4");
await ev("document.getElementById('closeSettings').click();document.getElementById('navBrowse').click()");
await until("document.querySelectorAll('.poster-card').length===6");
await until("document.querySelector('#posters img[data-failed]')!==null");
await until("document.querySelector('#imageError').textContent.includes('Lỗi ảnh thử nghiệm')");
await ev("document.getElementById('retryImages').click()");
await until("[...document.querySelectorAll('#posters img')].slice(0,4).every(i=>i.complete&&i.naturalWidth>0) && document.getElementById('retryImages').hidden");
await until("getComputedStyle(document.documentElement).backgroundColor==='rgb(16, 24, 20)'");
await mkdir('qa',{recursive:true});
let shot=await cdp('Page.captureScreenshot',{format:'png'},sessionId);await writeFile('qa/mobile-library.png',Buffer.from(shot.data,'base64'));
await ev("document.querySelector('.poster-card').click()");
await until("document.querySelectorAll('.episode').length===4");
await until("document.querySelector('#seriesCover img')?.naturalWidth>0 && document.querySelectorAll('#episodeRows img').length===0");
await until("[...document.querySelectorAll('#episodeRows .desc small')].map(e=>e.textContent).join('|')==='02:05|02:23|1:02:03|Chưa rõ thời lượng'");
for(const width of [360,390]){
 await cdp('Emulation.setDeviceMetricsOverride',{width,height:844,deviceScaleFactor:1,mobile:true},sessionId);
 await until("document.documentElement.scrollWidth<=window.innerWidth");
}
shot=await cdp('Page.captureScreenshot',{format:'png'},sessionId);await writeFile('qa/mobile-series.png',Buffer.from(shot.data,'base64'));
await ev("window.noSeriesCover=true;openSeries('https://huangguoai.com/video/2/')");
await until("!busy && document.getElementById('seriesCover').hidden && !document.querySelector('#seriesCover img')");
await ev("window.noSeriesCover=false;openSeries('https://huangguoai.com/video/1/')");
await until("!busy && document.querySelector('#seriesCover img')?.naturalWidth>0");
await ev("window.testFastHost='rotated-source.example';document.getElementById('seriesFast').click()");
await until("!busy && site==='fast' && document.getElementById('seriesFast').checked && document.getElementById('seriesUrl').value.startsWith('https://rotated-source.example/') && document.getElementById('domain').textContent==='rotated-source.example' && isSeriesLink('https://rotated-source.example/video/1/')");
await ev("document.getElementById('seriesFast').click()");
await until("!busy && site==='main' && document.getElementById('seriesUrl').value.startsWith('https://huangguoai.com/')");
await ev("let picks=document.querySelectorAll('.pick input');picks[2].click();picks[0].click();document.getElementById('downloadSelected').click()");
await until("document.getElementById('batchOrder').textContent.indexOf('Tập 3') < document.getElementById('batchOrder').textContent.indexOf('Tập 1')");
await ev("document.getElementById('mergeDownload').click()");
await until("requests.some(([m,b])=>m==='enqueue'&&b.merge&&b.items.map(v=>v.id).join(',')==='hg-1-3,hg-1-1')");
await until("!document.getElementById('queue').hidden && selected.length===0");
await ev("document.getElementById('navBrowse').click()");
await until("document.querySelectorAll('.pick input:checked').length===0");
await ev("document.getElementById('navTools').click();document.getElementById('mergeTool').click();document.getElementById('pickFiles').click()");
await until("document.getElementById('fileList').children.length===2");
await ev("document.getElementById('fileList').children[1].querySelector('button').click();document.getElementById('deleteOriginals').click();document.getElementById('runFiles').click()");
await until("requests.some(([m,b])=>m==='enqueue'&&b.mode==='merge'&&b.deleteOriginals&&b.items[0].id==='b')");
await until("!document.getElementById('fileDialog').open && !document.getElementById('tools').hidden && document.querySelectorAll('#toolJobs .job').length===1");
await ev("document.getElementById('navTools').click()");
await until("!document.getElementById('tools').hidden");
for(const width of [360,390,768]){
 await cdp('Emulation.setDeviceMetricsOverride',{width,height:844,deviceScaleFactor:1,mobile:true},sessionId);
 await until("document.documentElement.scrollWidth<=window.innerWidth");
}
await cdp('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true},sessionId);
shot=await cdp('Page.captureScreenshot',{format:'png'},sessionId);await writeFile('qa/mobile-tools.png',Buffer.from(shot.data,'base64'));
await ev("document.getElementById('navBrowse').click();document.getElementById('backCatalog').click()");
await until("!busy && !document.getElementById('browse').hidden");
await ev("document.getElementById('ptTab').click();document.getElementById('ptControls').requestSubmit()");
await until("requests.some(([m,b])=>m==='list'&&b.token==='') && document.querySelectorAll('#ptRows .episode').length===1");
await ev("document.getElementById('accountOptions').open=true;document.getElementById('ptToken').value='test-token';document.getElementById('guestMode').click()");
await until("document.getElementById('ptToken').value==='' && !document.getElementById('accountOptions').open");
await ev("document.getElementById('navLocal').click()");
await until("!document.getElementById('local').hidden && document.querySelectorAll('.local-video').length===2");
await until("requests.some(([m,b])=>m==='localList'&&b.requestAccess&&b.refresh)");
await until("[...document.querySelectorAll('.local-cover img')].every(i=>i.complete&&i.naturalWidth>0)");
await ev("document.getElementById('moreLocal').click()");
await until("document.querySelectorAll('.local-video').length===3 && document.getElementById('moreLocal').hidden");
await ev("document.querySelectorAll('.local-video')[1].click()");
await until("requests.some(([m,b])=>m==='localPlay'&&b.id==='local-2'&&b.sort==='newest'&&b.query==='')");
await ev("document.getElementById('localQuery').value='10';document.getElementById('localSearch').requestSubmit()");
await until("document.querySelectorAll('.local-video').length===1 && document.getElementById('localCount').textContent==='1 video'");
await ev("document.getElementById('localQuery').value='not submitted';document.querySelector('.local-video').click()");
await until("requests.some(([m,b])=>m==='localPlay'&&b.id==='local-10'&&b.query==='10')");
await ev("window.cancelFolder=true;document.getElementById('chooseVideoFolder').click()");
await delay(100);await until("localModeView==='device'");
await ev("window.cancelFolder=false;document.getElementById('chooseVideoFolder').click()");
await until("localModeView==='folder' && document.getElementById('localLocation').textContent.includes('Phim trong thư mục') && document.getElementById('grantVideoAccess').hidden");
for(const width of [360,390,768]){
 await cdp('Emulation.setDeviceMetricsOverride',{width,height:844,deviceScaleFactor:1,mobile:true},sessionId);
 await until("document.documentElement.scrollWidth<=window.innerWidth && document.querySelectorAll('.bottom button').length===4");
}
await cdp('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true},sessionId);
shot=await cdp('Page.captureScreenshot',{format:'png'},sessionId);await writeFile('qa/mobile-local-player.png',Buffer.from(shot.data,'base64'));
await ev("document.getElementById('navBrowse').click();document.querySelector('#ptRows .episode-actions button').click()");
await until("requests.some(([m,b])=>m==='play'&&b.playlist?.length===1&&b.id==='324360')");
if(await ev("testErrors.length"))throw Error(await ev("JSON.stringify(testErrors)"));
await ev("document.getElementById('navQueue').click();document.getElementById('settingsButton').click();document.getElementById('enableDownloads').click()");
await until("!downloadsEnabled && document.getElementById('navBrowse').hidden && document.getElementById('navQueue').hidden && !document.getElementById('local').hidden");
await ev("document.getElementById('closeSettings').click();document.getElementById('navTools').click()");
await until("!document.getElementById('tools').hidden && document.querySelectorAll('#toolJobs .job').length===1 && document.querySelector('#toolJobs progress').value===42");
await ev("document.getElementById('navLocal').click()");
shot=await cdp('Page.captureScreenshot',{format:'png'},sessionId);await writeFile('qa/mobile-local-player.png',Buffer.from(shot.data,'base64'));
await cdp('Page.reload',{},sessionId);
await until("typeof localInitialized!=='undefined' && localInitialized && !document.getElementById('local').hidden && document.querySelectorAll('.bottom button:not([hidden])').length===2");
await until("!requests.some(([m])=>m==='catalogue'||m==='list')");
await ev("document.getElementById('settingsButton').click();document.getElementById('enableDownloads').click()");
await until("downloadsEnabled");
await cdp('Page.reload',{},sessionId);
await until("typeof localInitialized!=='undefined' && localInitialized && document.querySelectorAll('.bottom button:not([hidden])').length===2 && !downloadsEnabled");
await ev("document.getElementById('settingsButton').click();window.rejectUnlock=true;document.getElementById('enableDownloads').click()");
await until("!document.getElementById('enableDownloads').disabled && !downloadsEnabled && document.getElementById('featureStatus').textContent.includes('không đúng')");
await ev("window.rejectUnlock=false;window.cancelUnlock=true;document.getElementById('enableDownloads').click()");
await until("!document.getElementById('enableDownloads').disabled && !downloadsEnabled && !document.getElementById('enableDownloads').checked");
await ev("window.cancelUnlock=false;document.getElementById('enableDownloads').click()");
await until("downloadsEnabled && document.querySelectorAll('.bottom button:not([hidden])').length===4");
if(await ev("testErrors.length"))throw Error(await ev("JSON.stringify(testErrors)"));
await until("requests.some(([m,b])=>m==='checkUpdates'&&b.force===false)");
await ev("window.testUpdate={state:'available',message:'Có bản 1.2.0.',version:'1.2.0',size:47185920,notes:'Cải thiện trình phát và cập nhật.',available:true,busy:false};if(!document.getElementById('settings').open)document.getElementById('settingsButton').click();document.getElementById('checkUpdate').click()");
await until("!document.getElementById('updateBanner').hidden && !document.getElementById('downloadUpdate').hidden && document.getElementById('updateNotes').textContent.includes('Cải thiện')");
await ev("document.getElementById('downloadUpdate').click()");
await until("document.getElementById('updateProgress').value===50 && !document.getElementById('cancelUpdate').hidden && document.getElementById('checkUpdate').disabled");
await ev("document.getElementById('cancelUpdate').click()");
await until("!document.getElementById('downloadUpdate').hidden && document.getElementById('cancelUpdate').hidden && document.getElementById('updateMessage').textContent.includes('Đã hủy')");
await ev("window.testUpdate={...window.testUpdate,state:'ready',message:'Đã kiểm tra APK.',progress:100};refreshUpdate()");
await until("!document.getElementById('installUpdate').hidden && document.getElementById('downloadUpdate').hidden");
await ev("document.getElementById('installUpdate').click()");
await until("window.installRequested===true && !document.getElementById('installUpdate').disabled");
await ev("document.getElementById('closeSettings').click();document.getElementById('showUpdate').click()");
await until("document.getElementById('settings').open");
for(const width of [360,390]){
 await cdp('Emulation.setDeviceMetricsOverride',{width,height:844,deviceScaleFactor:1,mobile:true},sessionId);
 await until("document.documentElement.scrollWidth<=window.innerWidth");
}
shot=await cdp('Page.captureScreenshot',{format:'png'},sessionId);await writeFile('qa/mobile-update.png',Buffer.from(shot.data,'base64'));
const icon=await readFile('artwork/icon.svg','utf8');
await cdp('Emulation.setDeviceMetricsOverride',{width:256,height:256,deviceScaleFactor:1,mobile:false},sessionId);
await cdp('Page.navigate',{url:'data:text/html,'+encodeURIComponent('<style>body{margin:0}svg{width:256px;height:256px;display:block}</style>'+icon)},sessionId);
await until("document.querySelector('svg')!==null");
shot=await cdp('Page.captureScreenshot',{format:'png'},sessionId);await writeFile('artwork/icon-preview.png',Buffer.from(shot.data,'base64'));
console.log('PASS layout 360/390/768; default player/tools; password-gated optional tabs, cancel/error/restart; local list/search/folder/pagination; playlist arguments; tool progress; batch selection/order');
}finally{await cdp('Browser.close').catch(()=>{});child.kill();server.close();}
