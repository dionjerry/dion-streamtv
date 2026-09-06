(() => {
  if (window.__dionMediaV3) return;
  window.__dionMediaV3 = true;
  const applyDisplayMode = () => {
    try {
      const mode = DionViewport.mode(location.hostname || '');
      const scale = Math.max(50, Math.min(200, DionViewport.zoom(location.hostname || ''))) / 100;
      let viewport = document.querySelector('meta[name="viewport"]');
      if (!viewport) { viewport=document.createElement('meta');viewport.name='viewport';(document.head||document.documentElement).appendChild(viewport); }
      viewport.content = mode === 'desktop'
        ? 'width=1280, initial-scale=1.0, user-scalable=yes'
        : 'width=412, initial-scale=1.0, user-scalable=yes';
      document.documentElement.style.zoom=String(scale);
      document.documentElement.dataset.dionView=mode;
      dispatchEvent(new Event('resize'));
    } catch (_) {}
  };
  window.__dionApplyDisplayMode=applyDisplayMode;
  if(document.documentElement)applyDisplayMode();else addEventListener('DOMContentLoaded',applyDisplayMode,{once:true});
  const tvScroll = { element: null, target: 0, running: false, lastInput: 0 };
  window.__dionTvScroll = (candidate, delta) => {
    if (!candidate) return;
    const now = performance.now();
    // Keep one container locked throughout a held/repeated remote gesture.
    // Without this lock, content moving beneath the pointer can start competing
    // animations on a parent, iframe panel and the page itself.
    if (!tvScroll.element || !tvScroll.element.isConnected || now - tvScroll.lastInput > 320) {
      tvScroll.element = candidate;
      tvScroll.target = candidate.scrollTop || 0;
    }
    const element = tvScroll.element;
    tvScroll.lastInput = now;
    const maximum = Math.max(0, element.scrollHeight - element.clientHeight);
    tvScroll.target = Math.max(0, Math.min(maximum, tvScroll.target + delta));
    if (tvScroll.running) return;
    tvScroll.running = true;
    const animate = () => {
      const current = element.scrollTop || 0;
      const distance = tvScroll.target - current;
      if (Math.abs(distance) < .8) {
        element.scrollTop = tvScroll.target;
        tvScroll.running = false;
        return;
      }
      element.scrollTop = current + distance * .14;
      requestAnimationFrame(animate);
    };
    requestAnimationFrame(animate);
  };
  const frameId = Math.random().toString(36).slice(2);
  let active = null, lastReport = 0, stalledSince = 0;
  let skipTarget = null;
  const bound = new WeakSet();

  const visibleArea = v => {
    const r = v.getBoundingClientRect();
    return Math.max(0, Math.min(innerWidth, r.right) - Math.max(0, r.left)) *
      Math.max(0, Math.min(innerHeight, r.bottom) - Math.max(0, r.top));
  };
  const score = v => {
    const text = ((v.id || '') + ' ' + (v.className || '') + ' ' + (v.parentElement?.className || '')).toLowerCase();
    const source = (v.currentSrc || v.src || '').toLowerCase();
    let s = Math.min(500, visibleArea(v) / 4000);
    if (!v.paused && !v.ended) s += 700;
    if (v.duration > 60 || !Number.isFinite(v.duration)) s += 180;
    if (v.controls || document.fullscreenElement) s += 80;
    if (/\b(ad|ads|advert|preroll|promo)\b/.test(text + ' ' + source)) s -= 900;
    if (v.muted && v.duration > 0 && v.duration < 60) s -= 350;
    return Math.round(s);
  };
  const pick = () => [...document.querySelectorAll('video')].sort((a,b) => score(b)-score(a))[0] || null;
  const tracks = v => JSON.stringify([...v.textTracks].map((t,i) => ({i,label:t.label||t.language||`Subtitle ${i+1}`,lang:t.language,kind:t.kind,enabled:t.mode==='showing'})));
  const qualities = v => {
    const hls=window.hls||window.HlsPlayer||null;
    const rows = hls?.levels?.length ? hls.levels.map((l,i)=>({i,label:l.height?`${l.height}p`:l.bitrate?`${Math.round(l.bitrate/1000)} kbps`:`Level ${i+1}`,kind:'hls'})) : [...v.querySelectorAll('source')].map((s,i) => ({i,label:s.getAttribute('label')||s.getAttribute('res')||s.getAttribute('size')||s.type||`Source ${i+1}`,src:s.src,type:s.type,kind:'source'}));
    return JSON.stringify(rows);
  };
  const audio = v => JSON.stringify([...((v.audioTracks)||[])].map((t,i)=>({i,label:t.label||t.language||`Audio ${i+1}`,lang:t.language,enabled:!!t.enabled})));
  const streamType = url => /\.m3u8(?:$|\?)/i.test(url) ? 'HLS' : /\.mpd(?:$|\?)/i.test(url) ? 'DASH' : 'VIDEO';
  const report = force => {
    const v = pick(); active = v;
    if (!v) { try { DionMedia.candidate(frameId, -1, false, false, false, 0, 0, '', 'VIDEO', '[]', '[]', '[]'); } catch(_){} return; }
    const now = Date.now(); if (!force && now-lastReport < 700) return; lastReport=now;
    const waiting = !v.paused && v.readyState < 3;
    if (waiting) stalledSince ||= now; else stalledSince = 0;
    const url = v.currentSrc || v.src || '';
    skipTarget=[...document.querySelectorAll('button,a,[role=button]')].find(e=>/skip\s+(intro|recap)|skip opening/i.test(e.textContent||''))||null;
    try { DionMedia.skipAvailable(frameId,!!skipTarget,skipTarget?(skipTarget.textContent||'Skip intro').trim():''); } catch(_){}
    try { DionMedia.candidate(frameId, score(v), true, !v.paused&&!v.ended, waiting, v.currentTime||0, Number.isFinite(v.duration)?v.duration:0, url, streamType(url), tracks(v), qualities(v), audio(v)); } catch(_){}
    if (stalledSince && now-stalledSince > 12000) { try { DionMedia.stalled(frameId, url); } catch(_){} stalledSince=now+30000; }
  };
  const bind = v => {
    if (bound.has(v)) return; bound.add(v);
    ['play','playing','pause','waiting','stalled','seeking','seeked','loadedmetadata','durationchange','ended','ratechange'].forEach(n => v.addEventListener(n, () => report(true)));
  };
  const scan = () => { document.querySelectorAll('video').forEach(bind); report(false); };
  const command = (cmd,val,target) => {
    if (!target || target === frameId) {
      const v=pick(); if(v) {
        if(cmd==='toggle') v.paused?v.play():v.pause();
        else if(cmd==='play') v.play(); else if(cmd==='pause') v.pause();
        else if(cmd==='seek') v.currentTime=Math.max(0,Math.min(v.duration||Infinity,val));
        else if(cmd==='rewind') v.currentTime=Math.max(0,v.currentTime-val);
        else if(cmd==='forward') v.currentTime=Math.min(v.duration||Infinity,v.currentTime+val);
        else if(cmd==='mute') v.muted=!v.muted;
        else if(cmd==='speed') v.playbackRate=val;
        else if(cmd==='subtitle') [...v.textTracks].forEach((t,i)=>t.mode=i===val?'showing':'disabled');
        else if(cmd==='audio'&&v.audioTracks) [...v.audioTracks].forEach((t,i)=>t.enabled=i===val);
        else if(cmd==='quality') { const h=window.hls||window.HlsPlayer;if(h?.levels?.length)h.currentLevel=val;else{const s=[...v.querySelectorAll('source')][val];if(s){const p=v.currentTime,was=!v.paused;v.src=s.src;v.currentTime=p;if(was)v.play();}} }
        else if(cmd==='skip'&&skipTarget) skipTarget.click();
        else if(cmd==='aspect') { v.style.objectFit=['contain','cover','fill','none'][val]||'contain'; v.style.width='100%';v.style.height='100%'; }
        else if(cmd==='fullscreen') (v.requestFullscreen||v.webkitRequestFullscreen)?.call(v);
        report(true);
      }
    }
    document.querySelectorAll('iframe').forEach(f => { try { f.contentWindow.postMessage({__dion:true,cmd,val,target},'*'); } catch(_){} });
  };
  window.__dionMediaCommand=command;
  window.__dionFocusMove=dir=>{
    const all=[...document.querySelectorAll('a,button,input,select,textarea,[tabindex],[role=button]')].filter(e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>5&&r.height>5&&s.visibility!=='hidden'&&s.display!=='none'});
    if(!all.length)return; const current=all.includes(document.activeElement)?document.activeElement:null;
    if(!current){all[0].focus();return} const a=current.getBoundingClientRect(),ax=a.left+a.width/2,ay=a.top+a.height/2;
    const candidates=all.filter(e=>e!==current).map(e=>{const r=e.getBoundingClientRect(),x=r.left+r.width/2,y=r.top+r.height/2,dx=x-ax,dy=y-ay;const valid=dir==='left'?dx<0:dir==='right'?dx>0:dir==='up'?dy<0:dy>0;return{e,d:valid?(dir==='left'||dir==='right'?Math.abs(dx)*1.4+Math.abs(dy):Math.abs(dy)*1.4+Math.abs(dx)):1e9}}).sort((a,b)=>a.d-b.d);
    if(candidates[0]?.d<1e9){candidates[0].e.focus({preventScroll:false});candidates[0].e.scrollIntoView({behavior:'smooth',block:'nearest',inline:'nearest'});}
  };
  const focusStyle=document.createElement('style');focusStyle.textContent=':focus{outline:4px solid #4f86ff!important;outline-offset:4px!important}';(document.head||document.documentElement).appendChild(focusStyle);
  addEventListener('message',e=>{if(e.data?.__dion)command(e.data.cmd,e.data.val,e.data.target)});
  const observe=()=>{if(document.documentElement)new MutationObserver(scan).observe(document.documentElement,{subtree:true,childList:true});scan()};
  if(document.documentElement)observe();else addEventListener('DOMContentLoaded',observe,{once:true});
  setInterval(scan,750); scan();
})();
