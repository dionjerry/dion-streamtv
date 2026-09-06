import React from 'react';
import ReactDOM from 'react-dom/client';
import './styles.css';

declare global { interface Window { TvSettings?: { getPointerSize():number; getPointerSpeed():number; getSmoothness():number; getPointerAcceleration():boolean; getPointerStyle():number; getFocusMode():boolean; getSeekSeconds():number; getBackgroundPlayback():boolean; getKeepScreenAwake():boolean; getWebsiteViewMode():string; setPointerSize(v:number):void; setPointerSpeed(v:number):void; setSmoothness(v:number):void; setPointerAcceleration(v:boolean):void; setPointerStyle(v:number):void; setFocusMode(v:boolean):void; setSeekSeconds(v:number):void; centerPointer():void; setBackgroundPlayback(v:boolean):void; setKeepScreenAwake(v:boolean):void; setWebsiteViewMode(v:string):void; clearBrowserCache():void; resetPointer():void }; AppUpdater?: { downloadLatest():void }; BrowserHistory?: { getHistory():string; clear():void }; DionDiagnostics?: { getReport():string; clear():void } } }

type HistoryEntry = { url:string; title:string; host:string; visited:number };

const destinations = [
  { icon: '▶', eyebrow: 'MOVIES + SERIES', title: 'StreamIMDB', copy: 'Continue your films and shows.', url: 'https://streamimdb.ru/', tone: 'blue' },
  { icon: '✦', eyebrow: 'ANIME', title: 'AnimePahe', copy: 'Browse anime and episodes.', url: 'https://animepahe.pw/', tone: 'coral' },
  { icon: 'D', eyebrow: 'STREAMING', title: 'Dulo', copy: 'Open Dulo on your TV.', url: 'https://dulo.gd/', tone: 'amber' },
  { icon: 'G', eyebrow: 'SEARCH', title: 'Google', copy: 'Search the web from your TV.', url: 'https://www.google.com/', tone: 'green' },
];

function App() {
  const [address, setAddress] = React.useState('');
  const [settingsOpen, setSettingsOpen] = React.useState(() => location.hash === '#settings');
  const [historyOpen, setHistoryOpen] = React.useState(() => location.hash === '#history');
  const [diagnosticsOpen, setDiagnosticsOpen] = React.useState(() => location.hash === '#diagnostics');
  const [diagnostics, setDiagnostics] = React.useState<{blocked:number;events:{time:number;type:string;message:string}[]}>(()=>{try{return JSON.parse(window.DionDiagnostics?.getReport()??'{"blocked":0,"events":[]}')}catch{return {blocked:0,events:[]}}});
  const [browserHistory, setBrowserHistory] = React.useState<HistoryEntry[]>(() => { try { return JSON.parse(window.BrowserHistory?.getHistory() ?? '[]'); } catch { return []; } });
  const [pointerSize, setPointerSize] = React.useState(() => window.TvSettings?.getPointerSize() ?? 1);
  const [pointerSpeed, setPointerSpeed] = React.useState(() => window.TvSettings?.getPointerSpeed() ?? 1);
  const [smoothness, setSmoothness] = React.useState(() => window.TvSettings?.getSmoothness() ?? 42);
  const [pointerAcceleration, setPointerAcceleration] = React.useState(() => window.TvSettings?.getPointerAcceleration() ?? true);
  const [pointerStyle, setPointerStyle] = React.useState(() => window.TvSettings?.getPointerStyle() ?? 0);
  const [focusMode, setFocusMode] = React.useState(() => window.TvSettings?.getFocusMode() ?? false);
  const [seekSeconds, setSeekSeconds] = React.useState(() => window.TvSettings?.getSeekSeconds() ?? 10);
  const [backgroundPlayback, setBackgroundPlayback] = React.useState(() => window.TvSettings?.getBackgroundPlayback() ?? false);
  const [keepAwake, setKeepAwake] = React.useState(() => window.TvSettings?.getKeepScreenAwake() ?? true);
  const [viewMode, setViewMode] = React.useState(() => window.TvSettings?.getWebsiteViewMode() ?? 'desktop');
  React.useEffect(() => {
    const onBack = () => { setSettingsOpen(location.hash === '#settings'); setHistoryOpen(location.hash === '#history'); setDiagnosticsOpen(location.hash === '#diagnostics'); };
    addEventListener('popstate', onBack);
    return () => removeEventListener('popstate', onBack);
  }, []);
  const openSettings = () => { history.pushState({ settings:true }, '', '#settings'); setSettingsOpen(true); };
  const openHistory = () => {
    try { setBrowserHistory(JSON.parse(window.BrowserHistory?.getHistory() ?? '[]')); } catch { setBrowserHistory([]); }
    history.pushState({ history:true }, '', '#history'); setHistoryOpen(true);
  };
  const closeSettings = () => { if (location.hash === '#settings') history.back(); else setSettingsOpen(false); };
  const openDiagnostics=()=>{try{setDiagnostics(JSON.parse(window.DionDiagnostics?.getReport()??'{"blocked":0,"events":[]}'))}catch{} history.pushState({},'','#diagnostics');setSettingsOpen(false);setDiagnosticsOpen(true);};
  const openAddress = (event: React.FormEvent) => {
    event.preventDefault();
    const value = address.trim();
    if (value) window.location.href = /^https?:\/\//i.test(value) ? value : `https://${value}`;
  };
  if (settingsOpen) return <main className="settingsPage">
    <header className="pageHeader"><div><p className="eyebrow">DiON streamTV</p><h1>Settings</h1><span>Customize your TV controls, playback and website layout.</span></div><button className="closeSettings" onClick={closeSettings}>← BACK TO HOME</button></header>
    <section className="settingsPanel settingsStandalone">
      <p className="settingsSection">POINTER</p>
      <label><span><b>Pointer size</b><small>{Math.round(pointerSize * 100)}%</small></span><input type="range" min="0.65" max="1.8" step="0.05" value={pointerSize} onChange={e => { const v=+e.target.value; setPointerSize(v); window.TvSettings?.setPointerSize(v); }} /></label>
      <label><span><b>Movement speed</b><small>{Math.round(pointerSpeed * 100)}%</small></span><input type="range" min="0.5" max="2.2" step="0.05" value={pointerSpeed} onChange={e => { const v=+e.target.value; setPointerSpeed(v); window.TvSettings?.setPointerSpeed(v); }} /></label>
      <label><span><b>Movement smoothness</b><small>{smoothness}%</small></span><input type="range" min="0" max="100" step="5" value={smoothness} onChange={e => { const v=+e.target.value; setSmoothness(v); window.TvSettings?.setSmoothness(v); }} /></label>
      <button className="settingToggle" onClick={() => { const v=!pointerAcceleration; setPointerAcceleration(v); window.TvSettings?.setPointerAcceleration(v); }}><span><b>Pointer acceleration</b><small>Move faster while holding a direction</small></span><i className={pointerAcceleration?'on':''}>{pointerAcceleration?'ON':'OFF'}</i></button>
      <div className="viewMode"><button className={pointerStyle===0?'selected':''} onClick={() => { setPointerStyle(0); window.TvSettings?.setPointerStyle(0); }}><b>Blue ring</b><small>Classic pointer</small></button><button className={pointerStyle===1?'selected':''} onClick={() => { setPointerStyle(1); window.TvSettings?.setPointerStyle(1); }}><b>Gold dark</b><small>High contrast</small></button><button className={pointerStyle===2?'selected':''} onClick={() => { setPointerStyle(2); window.TvSettings?.setPointerStyle(2); }}><b>Mint</b><small>Bright pointer</small></button></div>
      <button className="centerPointer" onClick={() => window.TvSettings?.centerPointer()}>⊙ Center pointer now</button>
      <button className="settingToggle" onClick={() => { const v=!focusMode; setFocusMode(v); window.TvSettings?.setFocusMode(v); }}><span><b>D-pad Focus mode</b><small>Navigate buttons directly instead of moving the pointer</small></span><i className={focusMode?'on':''}>{focusMode?'ON':'OFF'}</i></button>
      <div className="settingsTip"><b>Recommended</b><span>100% size · 100% speed · 40% smoothness</span></div>
      <p className="settingsSection">PLAYBACK</p>
      <label><span><b>Remote seek step</b><small>{seekSeconds} seconds</small></span><input type="range" min="5" max="60" step="5" value={seekSeconds} onChange={e=>{const v=+e.target.value;setSeekSeconds(v);window.TvSettings?.setSeekSeconds(v);}} /></label>
      <button className="settingToggle" onClick={() => { const v=!backgroundPlayback; setBackgroundPlayback(v); window.TvSettings?.setBackgroundPlayback(v); }}><span><b>Background playback</b><small>Allow audio/video while the app is minimized</small></span><i className={backgroundPlayback?'on':''}>{backgroundPlayback?'ON':'OFF'}</i></button>
      <button className="settingToggle" onClick={() => { const v=!keepAwake; setKeepAwake(v); window.TvSettings?.setKeepScreenAwake(v); }}><span><b>Keep screen awake</b><small>Prevent the TV sleeping while watching</small></span><i className={keepAwake?'on':''}>{keepAwake?'ON':'OFF'}</i></button>
      <p className="settingsSection">WEBSITE LAYOUT</p>
      <div className="viewMode"><button className={viewMode==='desktop'?'selected':''} onClick={() => { setViewMode('desktop'); window.TvSettings?.setWebsiteViewMode('desktop'); }}><b>▣ Desktop</b><small>Wide TV layout</small></button><button className={viewMode==='mobile'?'selected':''} onClick={() => { setViewMode('mobile'); window.TvSettings?.setWebsiteViewMode('mobile'); }}><b>▯ Mobile</b><small>Phone-style layout</small></button></div>
      <p className="settingsSection">MAINTENANCE</p>
      <div className="utilityActions"><button onClick={() => { setPointerSize(1); setPointerSpeed(1); setSmoothness(42); setPointerAcceleration(true); setPointerStyle(0); setFocusMode(false); window.TvSettings?.resetPointer(); }}>Reset controls</button><button onClick={() => window.TvSettings?.clearBrowserCache()}>Clear browser cache</button><button onClick={openDiagnostics}>Open diagnostics</button></div>
      <button className="updateButton" onClick={() => window.AppUpdater?.downloadLatest()}><span>↓</span><b>Download latest app</b><small>Downloads securely, then opens Android’s installer</small></button>
    </section>
  </main>;
  if (diagnosticsOpen) return <main className="settingsPage historyPage"><header className="pageHeader"><div><p className="eyebrow">RELIABILITY</p><h1>Diagnostics</h1><span>Player recovery and filtering events.</span></div><button className="closeSettings" onClick={()=>history.back()}>← BACK</button></header><section className="historyPanel"><div className="settingsTip"><b>Blocked requests</b><span>{diagnostics.blocked}</span></div><div className="historyToolbar"><b>{diagnostics.events.length} recorded events</b><button onClick={()=>{window.DionDiagnostics?.clear();setDiagnostics({blocked:diagnostics.blocked,events:[]})}}>Clear events</button></div><div className="historyEntries">{diagnostics.events.map((e,i)=><div className="historyEntry" key={i}><span className="siteInitial">!</span><span><b>{e.type}</b><small>{new Date(e.time).toLocaleString()} · {e.message}</small></span></div>)}</div></section></main>;
  if (historyOpen) return <main className="settingsPage historyPage">
    <header className="pageHeader"><div><p className="eyebrow">WEB + GOOGLE</p><h1>Browsing History</h1><span>Sites and searches opened inside DiON streamTV.</span></div><button className="closeSettings" onClick={() => history.back()}>← BACK TO HOME</button></header>
    <section className="historyPanel">
      <div className="historyToolbar"><b>{browserHistory.length} {browserHistory.length===1?'page':'pages'}</b><button onClick={() => { window.BrowserHistory?.clear(); setBrowserHistory([]); }}>Clear history</button></div>
      {browserHistory.length===0 ? <div className="historyEmpty"><span>⌁</span><h2>No browsing history yet</h2><p>Pages opened through Web and Google will appear here.</p></div> :
        <div className="historyEntries">{browserHistory.map((entry,index) => <a href={entry.url} className="historyEntry" key={`${entry.url}-${index}`}><span className="siteInitial">{entry.host?.[0]?.toUpperCase() ?? 'W'}</span><span><b>{entry.title}</b><small>{entry.host} · {new Date(entry.visited).toLocaleString()}</small></span><i>OPEN →</i></a>)}</div>}
    </section>
  </main>;
  return <main className="hub">
    <header className="brand">
      <div className="mark"><span>D</span></div>
      <div><p className="kicker">YOUR PERSONAL WATCHSPACE</p><h1>DiON <strong>streamTV</strong></h1></div>
      <div className="headerActions"><button className="settingsButton" onClick={openHistory}>◷ History</button><button className="settingsButton" onClick={openSettings}>⚙ Settings</button><a className="follow" href="https://instagram.com/diondevs">Follow <b>@diondevs</b> ↗</a></div>
    </header>
    <section className="intro"><p>GOOD TO SEE YOU</p><h2>What are we watching?</h2><span>Choose a destination, or enter any website below.</span></section>
    <section className="launchers" aria-label="Streaming destinations">
      {destinations.map(item => <a className={`launcher ${item.tone}`} href={item.url} key={item.title}>
        <span className="launcherIcon">{item.icon}</span><span className="eyebrow">{item.eyebrow}</span><h3>{item.title}</h3><p>{item.copy}</p><span className="go">OPEN <b>→</b></span>
      </a>)}
      <form className="launcher web" onSubmit={openAddress}>
        <span className="launcherIcon">⌁</span><span className="eyebrow">OPEN A WEBSITE</span><h3>Web</h3><p>Type a site address.</p>
        <div className="address"><input aria-label="Website address" placeholder="example.com" value={address} onChange={e => setAddress(e.target.value)} /><button>GO</button></div>
      </form>
    </section>
    <footer><span>D-pad moves the pointer · OK selects</span><span>Library is always available in the top-left</span></footer>
  </main>;
}

ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><App /></React.StrictMode>);
