const $=id=>document.getElementById(id);
const pending=new Map();let seq=0;
function call(method,body={}){return new Promise((resolve,reject)=>{const id=String(++seq);pending.set(id,{resolve,reject});Phone.request(id,method,JSON.stringify(body));});}
window.nativeReply=(id,value,error)=>{const p=pending.get(id);if(!p)return;pending.delete(id);error?p.reject(Error(error)):p.resolve(value);};
let downloadsEnabled=false;
const sourceDomains={main:'huangguoai.com',fast:'1bb4a2.ipycpknh.cc'};
function updateDomains(domains){if(domains)for(const key of ['main','fast'])if(typeof domains[key]==='string'&&/^[a-z0-9.-]+$/.test(domains[key]))sourceDomains[key]=domains[key];$('domain').textContent=sourceDomains[site];}
let screen='local',provider='hg',site='main',busy=false,section='home',keyword='',cursor=null,ptPage=0,ptMore=false,series=null;
let catalog=[],videos=[],selected=[],localFiles=[],localMode='compact',jobSignature='';
const imageCache=new Map();
function el(tag,cls,text){const n=document.createElement(tag);if(cls)n.className=cls;if(text!=null)n.textContent=text;return n;}
function note(text){$('notice').textContent=text;}
function show(name){if(!downloadsEnabled&&['browse','episodes','queue'].includes(name))name='local';screen=name;note('');for(const s of ['browse','episodes','local','queue','tools'])$(s).hidden=s!==name;for(const [id,page] of [['navBrowse','browse'],['navLocal','local'],['navQueue','queue'],['navTools','tools']])$(id).classList.toggle('active',name===page||(page==='browse'&&name==='episodes'));selection();window.scrollTo(0,0);if(name==='queue'||name==='tools')refreshJobs();}
function selection(){
 $('selectionBar').hidden=!selected.length||!['browse','episodes'].includes(screen)||(screen==='browse'&&provider!=='pt');
 $('selectionCount').textContent=selected.length+' video đã chọn';
 for(const mark of document.querySelectorAll('.pick b')){const i=selected.indexOf(mark.dataset.id);mark.textContent=i<0?'':String(i+1);}
 $('allEpisodes').checked=videos.length>0&&selected.length===videos.length;
}
function toggle(id,on){if(on&&!selected.includes(id))selected.push(id);else if(!on)selected=selected.filter(x=>x!==id);selection();}
const observer=new IntersectionObserver(entries=>{for(const entry of entries)if(entry.isIntersecting){observer.unobserve(entry.target);loadImage(entry.target);}}, {rootMargin:'200px'});
function imageFailures(){const n=document.querySelectorAll('img[data-failed]').length;$('retryImages').hidden=!n;$('retryImages').textContent='Tải lại ảnh ('+n+')';}
async function loadImage(img){
 const key=img.dataset.site+'|'+img.dataset.url;
 const fail=message=>{img.dataset.failed='1';img.title=message;imageCache.delete(key);imageFailures();$('imageError').textContent='Ảnh chưa hiển thị: '+message;};
 delete img.dataset.failed;
 try{let data=imageCache.get(key);if(!data){data=(await call('image',{url:img.dataset.url,site:img.dataset.site})).data;if(typeof data!=='string'||!data.startsWith('data:image/'))throw Error('Không nhận được thumbnail.');imageCache.set(key,data);if(imageCache.size>20)imageCache.delete(imageCache.keys().next().value);}
  img.onload=()=>{delete img.dataset.failed;imageFailures();if(!document.querySelector('img[data-failed]'))$('imageError').textContent='';};img.onerror=()=>fail('Không hiển thị được thumbnail.');img.src=data;
 }catch(e){fail(e.message);}
}
$('retryImages').onclick=async()=>{const b=$('retryImages');b.disabled=true;await Promise.all([...document.querySelectorAll('img[data-failed]')].map(loadImage));b.disabled=false;imageFailures();};
function image(url){const img=el('img');img.alt='';img.dataset.url=url;img.dataset.site=site;observer.observe(img);return img;}
function renderCatalog(){
 $('posters').replaceChildren();for(const item of catalog){const b=el('button','poster-card');const poster=el('div','poster','▷');if(item.cover)poster.append(image(item.cover));if(item.count)poster.append(el('em','',item.count+' tập'));b.append(poster,el('strong','',item.title));b.onclick=()=>openSeries(item.url);$('posters').append(b);}
 $('catalogCount').textContent=catalog.length+' bộ';$('emptyBrowse').hidden=catalog.length>0;$('more').hidden=!cursor;
}
async function catalogue(append=false){
 if(busy)return;busy=true;note('Đang đọc danh mục…');
 try{const r=await call('catalogue',{site,section,keyword,cursor:append?cursor:undefined});updateDomains(r.domains);cursor=r.next;if(!append)catalog=[];for(const item of r.items)if(!catalog.some(x=>x.url===item.url))catalog.push(item);renderCatalog();note('');}
 catch(e){note(e.message);}finally{busy=false;}
}
function renderVideos(){
 const target=screen==='episodes'?$('episodeRows'):$('ptRows');target.replaceChildren();
 for(const video of videos){
  const row=el('article','episode'),pick=el('label','pick'),checkbox=el('input'),mark=el('b');checkbox.type='checkbox';checkbox.checked=selected.includes(video.id);checkbox.setAttribute('aria-label','Chọn '+video.title);mark.dataset.id=video.id;
  checkbox.onchange=()=>toggle(video.id,checkbox.checked);pick.append(checkbox,mark);row.append(pick);
  if(video.thumb)row.append(image(video.thumb));
  const desc=el('div','desc');desc.append(el('strong','',video.title));const duration=video.duration||(screen==='episodes'?'Chưa rõ thời lượng':'');if(duration||video.date)desc.append(el('small','',[duration,video.date].filter(Boolean).join(' · ')));
  const actions=el('div','episode-actions'),watch=el('button','','▶'),down=el('button','','↓');
  watch.setAttribute('aria-label','Xem '+video.title);down.setAttribute('aria-label','Tải '+video.title);
  watch.onclick=async()=>{watch.disabled=true;note('Đang mở video…');try{await call('play',{...video,playlist:videos,token:$('ptToken').value});note('');}catch(e){note(e.message);}finally{watch.disabled=false;}};
  down.onclick=()=>batch([video]);actions.append(watch,down);row.append(desc,actions);target.append(row);
 }selection();
}
async function openSeries(url,preserve=false){
 if(busy)return;busy=true;note('Đang đọc danh sách tập…');
 try{const r=await call('episodes',{url:url.trim().replace(/\/?$/,'/'),site});updateDomains(r.domains);series=r;videos=r.items;if(!preserve)selected=[];else selected=selected.filter(id=>videos.some(v=>v.id===id));
  const cover=$('seriesCover');for(const img of cover.querySelectorAll('img'))observer.unobserve(img);cover.replaceChildren();cover.hidden=!r.cover;if(r.cover){cover.append(image(r.cover));}imageFailures();
  $('seriesTitle').textContent=r.title;$('seriesUrl').value=r.url;$('episodeCount').textContent=videos.length+' tập · HuangguoAI';
  $('author').hidden=!r.author;$('author').textContent=r.author?'Tác giả · '+r.author.name:'';
  $('seriesFast').checked=site==='fast';show('episodes');renderVideos();note('');
 }catch(e){note(e.message);}finally{busy=false;}
}
async function ptList(append=false){
 if(busy)return;busy=true;note('Đang đọc danh sách…');
 if(!$('ptUrl').value.trim())$('ptUrl').value=$('ptUrl').placeholder;
 try{
  await call('token',{token:$('ptToken').value,remember:$('remember').checked});
  const r=await call('list',{url:$('ptUrl').value,token:$('ptToken').value,keyword:$('ptKeyword').value,page:append?ptPage+1:1});
  ptPage=r.page;ptMore=r.more;if(!append){videos=[];selected=[];}for(const item of r.items)if(!videos.some(v=>v.id===item.id))videos.push(item);
  renderVideos();$('catalogCount').textContent=videos.length+' video';$('more').hidden=!ptMore;$('emptyBrowse').hidden=videos.length>0;note('');
 }catch(e){note(e.message);}finally{busy=false;}
}
function switchProvider(p){
 if(busy)return;provider=p;selected=[];videos=[];show('browse');$('translateTitles').hidden=p!=='hg';$('hgControls').hidden=p!=='hg';$('ptControls').hidden=p!=='pt';$('posters').hidden=p!=='hg';$('ptRows').hidden=p!=='pt';
 $('hgTab').classList.toggle('active',p==='hg');$('ptTab').classList.toggle('active',p==='pt');$('catalogTitle').textContent=p==='hg'?'Bộ phim':'Video';
 $('catalogCount').textContent='';$('ptRows').replaceChildren();$('more').hidden=true;
 if(p==='hg'){renderCatalog();if(!catalog.length)catalogue();}
}
async function setSite(fast){
 if(busy){$('fast').checked=site==='fast';$('seriesFast').checked=site==='fast';return;}const previous=site;site=fast?'fast':'main';$('fast').checked=fast;$('seriesFast').checked=fast;$('domain').textContent=sourceDomains[site];
 catalog=[];cursor=null;
 if(screen==='episodes'){await openSeries(series.url,true);if(series && new URL(series.url).hostname!==sourceDomains[fast?'fast':'main']){site=previous;$('fast').checked=site==='fast';$('seriesFast').checked=site==='fast';$('domain').textContent=sourceDomains[site];}}
 else await catalogue();
}
$('fast').onchange=e=>setSite(e.target.checked);$('seriesFast').onchange=e=>setSite(e.target.checked);
$('hgTab').onclick=()=>switchProvider('hg');$('ptTab').onclick=()=>switchProvider('pt');
$('hgForm').onsubmit=e=>{e.preventDefault();const value=$('hgQuery').value.trim();if(value.startsWith('https://'))openSeries(value);else{keyword=value;section='home';$('catalogTitle').textContent=keyword?'Kết quả tìm kiếm':'Bộ phim';catalogue();}};
function sessionLabel(){$('sessionInfo').textContent=$('ptToken').value.trim()?'Đang dùng token tài khoản.':'Chế độ khách · không cần nhập token.';}
$('ptToken').oninput=sessionLabel;
$('guestMode').onclick=async()=>{try{await call('token',{token:'',remember:false});$('ptToken').value='';$('remember').checked=false;$('accountOptions').open=false;sessionLabel();note('Đã chuyển sang chế độ khách.');}catch(e){note(e.message);}};
$('ptControls').onsubmit=e=>{e.preventDefault();ptList();};
$('ptUrl').onkeydown=e=>{if(e.key==='Tab'&&!e.shiftKey&&!e.target.value){e.target.value=e.target.placeholder;e.preventDefault();$('ptToken').focus();}};
$('categories').onclick=e=>{const s=e.target.dataset.section;if(!s||busy)return;section=s;keyword='';$('hgQuery').value='';$('catalogTitle').textContent=e.target.textContent;for(const b of $('categories').children)b.classList.toggle('active',b===e.target);catalogue();};
$('more').onclick=()=>provider==='hg'?catalogue(true):ptList(true);
$('seriesCover').onclick=()=>{const img=$('seriesCover').querySelector('img[data-failed]');if(img)loadImage(img);};
$('seriesForm').onsubmit=e=>{e.preventDefault();openSeries($('seriesUrl').value);};
function isSeriesLink(value){try{const u=new URL(value);return u.protocol==='https:'&&[...Object.values(sourceDomains),'huangguoai.com','duhgxf.lbvrhyir.cc','1bb4a2.ipycpknh.cc'].includes(u.hostname)&&!u.username&&!u.password&&/^\/video\/[1-9]\d*\//.test(u.pathname);}catch{return false;}}
$('seriesUrl').onpaste=e=>{const url=e.clipboardData.getData('text').trim();if(isSeriesLink(url)){e.preventDefault();$('seriesUrl').value=url;openSeries(url);}};
$('copyLink').onclick=()=>navigator.clipboard.writeText($('seriesUrl').value).then(()=>note('Đã sao chép link.'),()=>note('Nhấn giữ ô link để sao chép.'));
$('backCatalog').onclick=()=>{show('browse');if(!catalog.length)catalogue();};
$('author').onclick=()=>{section='author:'+series.author.id;keyword='';$('catalogTitle').textContent='Phim của '+series.author.name;show('browse');catalogue();};
$('allEpisodes').onchange=e=>{selected=e.target.checked?videos.map(v=>v.id):[];renderVideos();};
let localModeView='device',localItems=[],localNext=-1,localLoading=false,localInitialized=false,localViewOptions={};
const localCache=new Map();
const localObserver=new IntersectionObserver(entries=>{for(const entry of entries)if(entry.isIntersecting){localObserver.unobserve(entry.target);loadLocalThumb(entry.target);}}, {rootMargin:'160px'});
async function loadLocalThumb(img){
 try{
  let data=localCache.get(img.dataset.id);
  if(!data){data=(await call('localThumbnail',{id:img.dataset.id})).data;localCache.set(img.dataset.id,data);if(localCache.size>20)localCache.delete(localCache.keys().next().value);}
  img.onerror=()=>{img.hidden=true;};img.src=data;
 }catch{img.hidden=true;}
}
function localOptions(){return {mode:localModeView,query:$('localQuery').value,sort:$('localSort').value};}
function localDuration(ms){if(!ms)return '';const s=Math.floor(ms/1000);return Math.floor(s/60)+':'+String(s%60).padStart(2,'0');}
function renderLocal(append=false){
 if(!append){localObserver.disconnect();$('localVideos').replaceChildren();}
 const shown=append?$('localVideos').children.length:0;
 for(const item of localItems.slice(shown)){
  const card=el('button','local-video');card.setAttribute('aria-label','Phát '+item.title);
  const cover=el('span','local-cover','▷'),img=el('img');img.alt='';img.dataset.id=item.id;cover.append(img);localObserver.observe(img);
  const info=el('span','local-info');info.append(el('strong','',item.title),el('small','',[localDuration(item.durationMs),item.size?(item.size/1048576).toFixed(1)+' MiB':''].filter(Boolean).join(' · ')));
  card.append(cover,info);card.onclick=async()=>{card.disabled=true;try{await call('localPlay',{...localViewOptions,id:item.id});}catch(e){$('localStatus').textContent=e.message;}finally{card.disabled=false;}};
  $('localVideos').append(card);
 }
 $('moreLocal').hidden=localNext<0;
}
async function loadLocal({refresh=false,append=false,requestAccess=false}={}){
 if(localLoading)return;localLoading=true;$('localStatus').textContent=refresh?'Đang quét video…':'Đang đọc danh sách…';
 for(const id of ['scanDevice','chooseVideoFolder','refreshLocal','moreLocal','grantVideoAccess','localSort'])$(id).disabled=true;
 try{
  const options=append?localViewOptions:localOptions();
  const r=await call('localList',{...options,refresh,requestAccess,offset:append?localNext:0});
  localViewOptions={...options,mode:r.mode};
  localModeView=r.mode;localNext=r.next;localInitialized=true;
  if(!append){localItems=[];if(refresh)localCache.clear();}
  localItems.push(...r.items);$('localCount').textContent=r.total+' video';
  $('localLocation').textContent=r.label+ (r.mode==='folder'?' · gồm thư mục con':'');
  $('grantVideoAccess').hidden=!r.limited;
  $('localStatus').textContent=(r.message||'')+(!r.total?' Chưa tìm thấy video. Thử cấp quyền hoặc chọn thư mục.':'');
  renderLocal(append);
 }catch(e){$('localStatus').textContent=e.message;}
 finally{localLoading=false;for(const id of ['scanDevice','chooseVideoFolder','refreshLocal','moreLocal','grantVideoAccess','localSort'])$(id).disabled=false;}
}
$('navLocal').onclick=()=>{show('local');if(!localInitialized)loadLocal({refresh:true,requestAccess:true});};
$('scanDevice').onclick=()=>{if(localLoading)return;localModeView='device';$('localQuery').value='';loadLocal({refresh:true,requestAccess:true});};
$('chooseVideoFolder').onclick=async()=>{
 if(localLoading)return;
 try{const r=await call('chooseFolder');if(r.cancelled)return;localModeView='folder';$('localQuery').value='';await loadLocal({refresh:true});}
 catch(e){$('localStatus').textContent=e.message;}
};
$('refreshLocal').onclick=()=>loadLocal({refresh:true});
$('grantVideoAccess').onclick=()=>loadLocal({refresh:true,requestAccess:true});
$('moreLocal').onclick=()=>loadLocal({append:true});
$('localSearch').onsubmit=e=>{e.preventDefault();loadLocal();};
$('localSort').onchange=()=>loadLocal();
$('navBrowse').onclick=()=>{show(series&&provider==='hg'?'episodes':'browse');if(provider==='hg'&&!catalog.length&&!series&&downloadsEnabled)catalogue();};$('navQueue').onclick=()=>show('queue');$('navTools').onclick=()=>show('tools');
function batch(items){
 $('batchOrder').replaceChildren(...items.map(v=>el('li','',v.title)));$('mergeDownload').hidden=items.length<2;
 const dialog=$('batchDialog');dialog.querySelector('form').onsubmit=async e=>{
  e.preventDefault();const mode=e.submitter?.value;dialog.close();if(!['merge','separate'].includes(mode))return;
  try{
   for(const group of (mode==='merge'?[items]:items.map(i=>[i])))await call('enqueue',{mode:'download',items:group,merge:mode==='merge',compact:$('compactDownload').checked,token:$('ptToken').value,title:group[0].title+(group.length>1?' · '+group.length+' tập':'')});
   selected=selected.filter(id=>!items.some(i=>i.id===id));renderVideos();show('queue');note('');
  }catch(e){note(e.message);}
 };dialog.showModal();
}
$('downloadSelected').onclick=()=>batch(selected.map(id=>videos.find(v=>v.id===id)).filter(Boolean));
async function refreshJobs(){
 const page=screen;if(!['queue','tools'].includes(page))return;try{
  const all=await call('jobs');if(screen!==page)return;
  const jobs=page==='tools'?all.filter(j=>['compact','merge'].includes(j.mode)):all;
  const signature=page+JSON.stringify(jobs);if(signature===jobSignature)return;jobSignature=signature;
  const target=page==='tools'?$('toolJobs'):$('jobs');target.replaceChildren();
  if(!jobs.length)target.append(el('p','empty',page==='tools'?'Chưa có tác vụ xử lý.':'Chưa có tác vụ. Chọn video để tải hoặc xử lý.'));
  for(const job of jobs.slice().reverse()){
   const card=el('article','job');card.append(el('h3','',job.title),el('p','',job.message));const bar=el('progress');bar.max=100;bar.value=job.progress;card.append(bar);
   if(['running','queued'].includes(job.state)){const b=el('button','','Hủy');b.onclick=()=>call('cancel',{id:job.id}).then(refreshJobs).catch(e=>note(e.message));card.append(b);}
   if(['error','interrupted','cancelled'].includes(job.state)&&!job.results.length){const b=el('button','','Thử lại');b.onclick=()=>call('retry',{id:job.id}).then(refreshJobs).catch(e=>note(e.message));card.append(b);}
   for(const result of job.results){
    if(result.uri){const b=el('button','','Mở · '+result.name);b.onclick=()=>call('open',{uri:result.uri}).catch(e=>note(e.message));card.append(b);}
    else card.append(el('p','',result.name+' · '+result.message));
   }target.append(card);
  }
 }catch(e){note(e.message);}
}
setInterval(refreshJobs,1000);$('clearJobs').onclick=()=>call('clear').then(refreshJobs).catch(e=>note(e.message));
$('clearToolJobs').onclick=()=>call('clear',{toolsOnly:true}).then(refreshJobs).catch(e=>note(e.message));
function fileTool(mode){
 localMode=mode;localFiles=[];$('deleteOriginals').checked=false;$('fileTitle').textContent=mode==='compact'?'Tối ưu video':'Ghép video';
 $('fileHelp').textContent=mode==='compact'?'Nén bằng phần cứng. Ưu tiên H.265, dùng H.264 khi máy chưa hỗ trợ.':'Chọn ít nhất 2 video. Dùng ↑ ↓ để đổi thứ tự ghép.';
 renderFiles();$('fileDialog').showModal();
}
function renderFiles(){
 $('fileList').replaceChildren();localFiles.forEach((file,i)=>{
  const li=el('li','',file.name),actions=el('div','file-buttons');
  const button=(text,disabled,fn)=>{const b=el('button','',text);b.disabled=disabled;b.onclick=()=>{fn();renderFiles();};actions.append(b);};
  if(localMode==='merge'){button('↑',i===0,()=>{[localFiles[i-1],localFiles[i]]=[localFiles[i],localFiles[i-1]];});button('↓',i===localFiles.length-1,()=>{[localFiles[i+1],localFiles[i]]=[localFiles[i],localFiles[i+1]];});}
  button('Bỏ',false,()=>localFiles.splice(i,1));li.append(actions);$('fileList').append(li);
 });$('runFiles').disabled=localFiles.length<(localMode==='merge'?2:1);
}
$('compactTool').onclick=()=>fileTool('compact');$('mergeTool').onclick=()=>fileTool('merge');$('closeFiles').onclick=()=>$('fileDialog').close();
$('pickFiles').onclick=async()=>{try{const files=await call('pick');for(const f of files)if(!localFiles.some(v=>v.id===f.id))localFiles.push(f);renderFiles();}catch(e){$('fileHelp').textContent=e.message;}};
$('runFiles').onclick=async()=>{
 $('runFiles').disabled=true;
 try{for(const group of(localMode==='merge'?[localFiles]:localFiles.map(f=>[f])))await call('enqueue',{mode:localMode,items:group,deleteOriginals:$('deleteOriginals').checked,title:(localMode==='merge'?'Ghép '+group.length+' video · ':'Tối ưu · ')+group[0].name});
  $('fileDialog').close();show('tools');
 }catch(e){$('fileHelp').textContent=e.message;}finally{renderFiles();}
};
$('sourcePage').onclick=()=>call('external',{url:series.url}).catch(e=>note(e.message));
$('translateTitles').onclick=()=>{const text=catalog.map(i=>i.title).join('\n').slice(0,4000);call('external',{url:'https://translate.google.com/?'+new URLSearchParams({sl:'zh-CN',tl:'vi',op:'translate',text})}).catch(e=>note(e.message));};
$('settingsButton').onclick=()=>{$('settings').showModal();refreshUpdate();};$('closeSettings').onclick=()=>$('settings').close();
$('showDiagnostics').onclick=async()=>{try{$('diagnosticText').textContent=(await call('diagnostics')).text;$('diagnosticText').hidden=false;$('copyDiagnostics').hidden=false;}catch(e){note(e.message);}};
$('copyDiagnostics').onclick=()=>call('copyDiagnostics').then(()=>{$('copyDiagnostics').textContent='Đã sao chép';}).catch(e=>note(e.message));
function applyDownloadFeatures(enabled){
 downloadsEnabled=!!enabled;$('enableDownloads').checked=downloadsEnabled;
 $('navBrowse').hidden=!downloadsEnabled;$('navQueue').hidden=!downloadsEnabled;
 if(!downloadsEnabled){
  if(['browse','episodes','queue'].includes(screen))show('local');
  for(const id of ['posters','episodeRows','ptRows','jobs']){for(const img of $(id).querySelectorAll('img'))observer.unobserve(img);$(id).replaceChildren();}
  catalog=[];videos=[];series=null;selected=[];cursor=null;imageCache.clear();jobSignature='';
  $('seriesTitle').textContent='';$('seriesUrl').value='';for(const img of $('seriesCover').querySelectorAll('img'))observer.unobserve(img);$('seriesCover').replaceChildren();$('seriesCover').hidden=true;
  $('catalogCount').textContent='';$('imageError').textContent='';$('retryImages').hidden=true;selection();
 }
}
$('enableDownloads').onchange=async()=>{
 const enabled=$('enableDownloads').checked;$('enableDownloads').disabled=true;
 try{const result=await call('downloadFeatures',{enabled});applyDownloadFeatures(result.enabled);$('featureStatus').textContent=result.enabled?'Đã mở khóa Thư viện nguồn và Hàng đợi.':'Hai tab đang khóa. Trình phát và Công cụ vẫn dùng bình thường.';
  if(result.enabled){const s=await call('settings');$('ptToken').value=s.token;$('remember').checked=!!s.token;$('accountOptions').open=!!s.token;sessionLabel();}
  else{$('ptToken').value='';$('remember').checked=false;sessionLabel();}
 }
 catch(e){$('enableDownloads').checked=downloadsEnabled;$('featureStatus').textContent=e.message;}
 finally{$('enableDownloads').disabled=false;}
};
let updateBusy=false,updateRevision=0,installPending=false;
function renderUpdate(s={}){
 updateBusy=!!s.busy;
 $('updateMessage').textContent=s.message||'Tự kiểm tra bản mới từ GitHub mỗi ngày khi mở app.';
 $('updateBanner').hidden=!s.available;
 $('updateBannerText').textContent=s.state==='ready'?'Bản '+s.version+' đã sẵn sàng cài.':'Có bản cập nhật '+(s.version||'mới')+'.';
 $('checkUpdate').disabled=updateBusy||installPending;
 $('downloadUpdate').hidden=!s.available||s.state==='ready'||updateBusy;
 $('downloadUpdate').textContent='Tải bản cập nhật'+(s.size?' · '+(s.size/1048576).toFixed(1)+' MiB':'');
 $('installUpdate').hidden=s.state!=='ready';$('installUpdate').disabled=installPending;
 $('cancelUpdate').hidden=s.state!=='downloading';
 $('updateProgress').hidden=s.state!=='downloading';$('updateProgress').value=Math.max(0,Math.min(100,Number(s.progress)||0));
 $('updateNotesBox').hidden=!s.available||!s.notes;$('updateNotes').textContent=s.notes||'';
}
async function refreshUpdate(){const revision=++updateRevision;try{const s=await call('updateStatus');if(revision===updateRevision)renderUpdate(s);}catch(e){$('updateMessage').textContent=e.message;}}
async function updateAction(method,body={}){
 const revision=++updateRevision;
 try{const s=await call(method,body);if(revision===updateRevision)renderUpdate(s);}
 catch(e){$('updateMessage').textContent=e.message;}
}
$('showUpdate').onclick=()=>{if(!$('settings').open)$('settings').showModal();$('updatePanel').scrollIntoView({block:'start'});refreshUpdate();};
$('checkUpdate').onclick=()=>updateAction('checkUpdates',{force:true});
$('downloadUpdate').onclick=()=>updateAction('downloadUpdate');
$('cancelUpdate').onclick=()=>updateAction('cancelUpdate');
$('installUpdate').onclick=async()=>{installPending=true;$('installUpdate').disabled=true;try{await updateAction('installUpdate');}finally{installPending=false;refreshUpdate();}};
setInterval(()=>{if(updateBusy||$('settings').open)refreshUpdate();},1000);
document.addEventListener('visibilitychange',()=>{if(!document.hidden)refreshUpdate();});
updateAction('checkUpdates',{force:false});
window.back=()=>{const d=document.querySelector('dialog[open]');if(d){d.close();return;}show('local');};
call('settings').then(s=>{updateDomains(s.domains);$('encoderInfo').textContent=s.encoder;$('appVersion').textContent='Stream Pocket · Android '+(s.version||'Android');if(!localInitialized&&!localLoading)localModeView=s.libraryMode||'device';$('ptToken').value=s.token;$('remember').checked=!!s.token;$('accountOptions').open=!!s.token;sessionLabel();applyDownloadFeatures(s.downloadsEnabled);show('local');loadLocal({refresh:true,requestAccess:true});}).catch(e=>note(e.message));
