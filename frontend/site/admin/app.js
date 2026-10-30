const $ = id => document.getElementById(id);
const ATOMIC = 1e12;
const TX_PAGE = 100;
const TX_MAX = 500;
const REFRESH_MS = 30000;
const SEED_DONE_KEY = 'mm_admin_seed_backed_up';

let auth = { access: null, refresh: null };
let txs = [];
let txTotal = 0;
let xferVendor = null;

// ============ Helpers ============
// Builds elements without innerHTML; strings become text nodes
function h(tag, props, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (k === 'class') el.className = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (v === true) el.setAttribute(k, '');
    else if (v !== false && v != null) el.setAttribute(k, v);
  }
  for (const c of children.flat()) {
    if (c != null && c !== false) el.append(c);
  }
  return el;
}

function table(headers, rows) {
  return h('table', { class: 'data-table' },
    h('thead', {}, h('tr', {}, headers.map(t => h('th', {}, t)))),
    h('tbody', {}, rows.map(cells => h('tr', {}, cells))));
}

function empty(text) {
  return h('div', { class: 'empty-state' }, text);
}

function fmtXMR(atomic) {
  return atomic == null ? '--' : (atomic / ATOMIC).toFixed(6) + ' XMR';
}

function fmtDate(s) {
  if (!s) return '-';
  return new Date(s).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function toast(msg, ok) {
  const el = h('div', { class: 'toast ' + (ok ? 'ok' : 'err') }, h('div', { class: 't-icon' }, ok ? 'OK' : '!'), h('span', {}, msg));
  $('toastWrap').append(el);
  setTimeout(() => {
    el.style.opacity = '0';
    el.style.transform = 'translateX(20px)';
    setTimeout(() => el.remove(), 300);
  }, 3500);
}

// Works over plain http where navigator.clipboard is unavailable
function copy(text) {
  if (navigator.clipboard && window.isSecureContext) {
    navigator.clipboard.writeText(text).then(() => toast('Copied', true), () => legacyCopy(text));
  } else {
    legacyCopy(text);
  }
}

function legacyCopy(text) {
  const ta = h('textarea', { readonly: true });
  ta.value = text;
  ta.style.position = 'fixed';
  ta.style.top = '-1000px';
  document.body.append(ta);
  ta.select();
  const ok = document.execCommand('copy');
  ta.remove();
  toast(ok ? 'Copied' : 'Copy failed, select it manually', ok);
}

function seedBackedUp() {
  try { return localStorage.getItem(SEED_DONE_KEY) === '1'; } catch (e) { return false; }
}

// ============ API ============
// Errors come as JSON {error} / {message} or as plain text
async function errorMessage(res, fallback) {
  const text = await res.text().catch(() => '');
  try {
    const data = JSON.parse(text);
    if (data.error || data.message) return data.error || data.message;
  } catch (e) {}
  return text.trim() || fallback;
}

// Never throws: a network failure comes back as a 503 so callers only check res.ok
async function api(method, path, body) {
  const send = () => fetch('/api' + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(auth.access ? { Authorization: 'Bearer ' + auth.access } : {}) },
    body: body ? JSON.stringify(body) : undefined
  }).catch(() => new Response('Network error', { status: 503 }));
  let res = await send();
  if (res.status === 401 && auth.refresh && await refresh()) {
    res = await send();
  }
  return res;
}

// Parallel requests that all get a 401 share one refresh
let refreshing = null;
function refresh() {
  refreshing = refreshing || doRefresh().finally(() => { refreshing = null; });
  return refreshing;
}

async function doRefresh() {
  const res = await fetch('/api/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refresh_token: auth.refresh })
  }).catch(() => null);
  if (!res) return false;
  if (!res.ok) {
    logout();
    return false;
  }
  const data = await res.json();
  setTokens(data.access_token, data.refresh_token);
  return true;
}

// ============ Auth ============
// Only the refresh token is stored; it's exchanged for an access token on load
function setTokens(access, refreshToken) {
  auth = { access, refresh: refreshToken };
  try { localStorage.setItem('mm_admin_refresh', refreshToken); } catch (e) {}
}

function logout() {
  auth = { access: null, refresh: null };
  try { localStorage.removeItem('mm_admin_refresh'); } catch (e) {}
  location.replace(location.pathname);
}

async function login(e) {
  e.preventDefault();
  $('loginError').textContent = '';
  $('loginBtn').disabled = true;
  try {
    const res = await fetch('/api/auth/login-admin', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: $('loginUser').value.trim(), password: $('loginPass').value })
    });
    if (!res.ok) {
      $('loginError').textContent = await errorMessage(res, 'Invalid credentials');
      return;
    }
    const data = await res.json();
    setTokens(data.access_token, data.refresh_token);
    showApp();
  } catch (err) {
    $('loginError').textContent = 'Network error';
  } finally {
    $('loginBtn').disabled = false;
  }
}

function showApp() {
  $('loginBg').classList.add('hidden');
  $('topNav').classList.remove('hidden');
  $('appScreen').classList.remove('hidden');
  loadAll();
  setInterval(() => { if (!document.hidden) loadAll(); }, REFRESH_MS);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) loadAll(); });
}

// ============ Data ============
// Calls made while a load is running share it
let loading = null;
function loadAll() {
  loading = loading || Promise.all([
    fetchHealth(), fetchDaemonStatus(), fetchBalance(), fetchWalletInfo(),
    fetchVendors(), fetchTx(), fetchPos(), fetchInvites()
  ]).catch(() => {}).finally(() => { loading = null; });
  return loading;
}

async function fetchHealth() {
  let dbOk = false, mpOk = false;
  try {
    const res = await api('GET', '/misc/health');
    const svc = (await res.json()).services || {};
    const mp = svc.MoneroPay || {};
    dbOk = svc.postgresql === true;
    mpOk = mp.status === 200 && !!(mp.services && mp.services.walletrpc);
  } catch (e) {}

  const down = [!dbOk && 'DB', !mpOk && 'MoneroPay'].filter(Boolean);
  $('statusDot').className = 'dot' + (down.length === 2 ? ' down' : down.length ? ' warn' : '');
  $('statusText').replaceChildren('Services ', h('strong', {}, down.length ? down.join(' + ') + ' Down' : 'OK'));
  for (const [id, ok] of [['stripDb', dbOk], ['stripMp', mpOk]]) {
    $(id).textContent = ok ? 'OK' : 'Down';
    $(id).className = 's-val ' + (ok ? 'ok' : 'down');
  }
}

async function fetchDaemonStatus() {
  const bar = $('syncBar');
  let d = {};
  try {
    const res = await api('GET', '/misc/daemon-status');
    if (res.ok) d = await res.json();
  } catch (e) {}

  let state, text;
  if (d.synced) {
    state = 'synced';
    text = 'Synced at height ' + d.height.toLocaleString();
  } else if (d.height > 0) {
    state = 'syncing';
    text = 'Syncing, height ' + d.height.toLocaleString() + (d.target_height ? ' / ' + d.target_height.toLocaleString() : '');
  } else {
    state = 'offline';
    text = 'Unreachable';
  }
  bar.className = 'sync-bar ' + state;
  $('syncText').replaceChildren(h('strong', {}, 'Monero Node: '), text);
}

async function fetchBalance() {
  const res = await api('GET', '/admin/balance');
  if (!res.ok) return;
  const d = await res.json();
  $('dashTotal').textContent = fmtXMR(d.total);
  $('dashUnlocked').textContent = fmtXMR(d.unlocked);
  $('stripLocked').textContent = (d.locked / ATOMIC).toFixed(6);
  $('stripOwed').textContent = (d.vendor_balances / ATOMIC).toFixed(6);
}

async function fetchWalletInfo() {
  const wrap = $('homeWalletWrap');
  const res = await api('GET', '/admin/wallet-info');
  if (!res.ok) {
    wrap.replaceChildren(empty(await errorMessage(res, 'Failed to load wallet info')));
    return;
  }
  const d = await res.json();
  // Keep an open restore form so a refresh doesn't wipe a half-typed seed
  const restoring = !$('restoreCard').classList.contains('hidden');
  $('walletSetupCard').classList.toggle('hidden', d.exists || restoring);
  if (d.exists) $('restoreCard').classList.add('hidden');
  $('seedReminder').classList.toggle('hidden', !d.exists || seedBackedUp());
  if (!d.exists) {
    wrap.replaceChildren(empty('No wallet yet'));
    return;
  }
  wrap.replaceChildren(
    h('div', { class: 'wallet-row' },
      h('div', { class: 'addr' }, d.address),
      h('button', { class: 'btn btn-ghost btn-small', onclick: () => copy(d.address) }, 'Copy')),
    h('div', { class: 'note' }, 'Wallet file: ' + d.wallet_name));
}

async function fetchVendors() {
  const res = await api('GET', '/admin/vendors');
  if (!res.ok) return toast('Failed to load vendors');
  const vendors = (await res.json()).vendors || [];
  $('dashVendors').textContent = vendors.length;
  if (!vendors.length) {
    $('vendorsWrap').replaceChildren(empty('No vendors yet. Create an invite to add one.'));
    return;
  }
  $('vendorsWrap').replaceChildren(table(['Name', 'Email', 'Payout Address', 'Balance', 'Actions'], vendors.map(v => [
    h('td', {}, h('strong', {}, v.name)),
    h('td', {}, v.email ? h('button', {
      type: 'button', class: 'btn btn-ghost btn-small',
      title: 'Click to copy email', 'aria-label': 'Copy email ' + v.email,
      onclick: () => copy(v.email)
    }, v.email) : ''),
    h('td', { class: 'mono', title: v.monero_subaddress }, v.monero_subaddress.slice(0, 10) + '...'),
    h('td', { class: 'mono' }, fmtXMR(v.balance)),
    h('td', { class: 'actions' },
      h('button', { class: 'btn-send', disabled: v.balance <= 0, title: v.balance > 0 ? null : 'No balance to transfer', onclick: () => openXfer(v) }, 'Transfer'),
      h('button', { class: 'btn-del', onclick: () => deleteVendor(v) }, 'Delete'))
  ])));
}

function txStatus(tx) {
  if (tx.transferred) return 'transferred';
  if (tx.confirmed) return 'confirmed';
  if (tx.accepted) return 'pending';
  return 'awaiting';
}

function txBadge(tx) {
  const st = txStatus(tx);
  return h('span', { class: 'badge ' + st }, st[0].toUpperCase() + st.slice(1));
}

async function fetchTx(more) {
  const offset = more ? txs.length : 0;
  // A refresh reloads the rows already shown, up to the API's limit
  const limit = more ? TX_PAGE : Math.min(Math.max(txs.length, TX_PAGE), TX_MAX);
  const res = await api('GET', '/admin/transactions?limit=' + limit + '&offset=' + offset);
  if (!res.ok) return toast('Failed to load transactions');
  const d = await res.json();
  txs = more ? txs.concat(d.transactions || []) : (d.transactions || []);
  txTotal = d.total || 0;
  $('dashTx').textContent = txTotal;
  $('loadMoreTxBtn').classList.toggle('hidden', txs.length >= txTotal);

  $('txWrap').replaceChildren(txs.length ? table(['ID', 'Vendor', 'POS', 'Amount', 'Status', 'Date'], txs.map(tx => [
    h('td', {}, '#' + tx.id),
    h('td', {}, tx.vendor_name || '-'),
    h('td', {}, tx.pos_name || '-'),
    h('td', { class: 'mono' }, fmtXMR(tx.amount)),
    h('td', {}, txBadge(tx)),
    h('td', { class: 'mono' }, fmtDate(tx.created_at))
  ])) : empty('No transactions yet'));

  $('dashTxWrap').replaceChildren(txs.length ? table(['ID', 'Vendor', 'Amount', 'Status'], txs.slice(0, 5).map(tx => [
    h('td', {}, '#' + tx.id),
    h('td', {}, tx.vendor_name || '-'),
    h('td', { class: 'mono' }, fmtXMR(tx.amount)),
    h('td', {}, txBadge(tx))
  ])) : empty('No recent transactions'));
}

async function fetchPos() {
  const res = await api('GET', '/admin/pos-devices');
  if (!res.ok) return toast('Failed to load POS devices');
  const devices = (await res.json()).devices || [];
  $('stripPos').textContent = devices.length;
  $('posWrap').replaceChildren(devices.length ? table(['ID', 'Name', 'Vendor', 'Created'], devices.map(p => [
    h('td', {}, '#' + p.id),
    h('td', {}, h('strong', {}, p.name)),
    h('td', {}, p.vendor_name || '-'),
    h('td', { class: 'mono' }, fmtDate(p.created_at))
  ])) : empty('No POS devices yet'));
}

async function fetchInvites() {
  const res = await api('GET', '/admin/invites');
  if (!res.ok) return toast('Failed to load invites');
  const invites = (await res.json()).invites || [];
  $('stripInvites').textContent = invites.length;
  const now = Date.now();
  $('invitesWrap').replaceChildren(invites.length ? table(['Code', 'Status', 'Forced Name', 'Valid Until'], invites.map(i => [
    h('td', { class: 'mono' }, i.invite_code),
    h('td', {}, i.used ? 'Used' : new Date(i.valid_until) < now ? 'Expired' : 'Open'),
    h('td', {}, i.forced_name || '-'),
    h('td', { class: 'mono' }, fmtDate(i.valid_until))
  ])) : empty('No invites yet'));
}

// ============ Actions ============
async function setupWallet() {
  $('setupWalletBtn').disabled = true;
  try {
    const res = await api('POST', '/admin/setup-wallet');
    if (!res.ok) return toast(await errorMessage(res, 'Wallet creation failed'));
    toast('Wallet created', true);
    await Promise.all([fetchWalletInfo(), fetchBalance()]);
  } finally {
    $('setupWalletBtn').disabled = false;
  }
}

async function restoreWallet(e) {
  e.preventDefault();
  $('confirmRestoreBtn').disabled = true;
  try {
    const res = await api('POST', '/admin/restore-wallet', {
      seed: $('restoreSeed').value.trim(),
      restore_height: parseInt($('restoreHeight').value, 10) || 0
    });
    if (!res.ok) return toast(await errorMessage(res, 'Restore failed'));
    $('restoreSeed').value = '';
    toast('Wallet restored', true);
    await Promise.all([fetchWalletInfo(), fetchBalance()]);
  } finally {
    $('confirmRestoreBtn').disabled = false;
  }
}

async function showSeed(e) {
  e.preventDefault();
  $('showSeedBtn').disabled = true;
  try {
    const res = await api('POST', '/admin/wallet-seed', { password: $('seedPass').value });
    if (!res.ok) return toast(await errorMessage(res, 'Could not get the seed'));
    $('seedDisplay').textContent = (await res.json()).seed;
    $('seedPass').value = '';
    $('seedForm').classList.add('hidden');
    $('seedWrap').classList.remove('hidden');
  } finally {
    $('showSeedBtn').disabled = false;
  }
}

function clearSeed() {
  $('seedDisplay').textContent = '';
  $('seedWrap').classList.add('hidden');
  $('seedForm').classList.remove('hidden');
}

function seedWrittenDown() {
  clearSeed();
  try { localStorage.setItem(SEED_DONE_KEY, '1'); } catch (e) {}
  $('seedReminder').classList.add('hidden');
}

async function createInvite(e) {
  e.preventDefault();
  const days = parseInt($('inviteValidDays').value, 10);
  $('inviteBtn').disabled = true;
  try {
    const res = await api('POST', '/admin/invite', {
      valid_until: Math.floor(Date.now() / 1000) + days * 86400,
      forced_name: $('inviteForced').value.trim() || null
    });
    if (!res.ok) return toast(await errorMessage(res, 'Could not create invite'));
    $('inviteCodeDisplay').textContent = (await res.json()).invite_code;
    $('inviteCodeDisplay').classList.add('show');
    $('inviteForced').value = '';
    toast('Invite created', true);
    await fetchInvites();
  } finally {
    $('inviteBtn').disabled = false;
  }
}

function openXfer(vendor) {
  xferVendor = vendor;
  $('xferVendorName').textContent = vendor.name;
  $('xferModal').classList.add('open');
  $('xferConfirmBtn').focus();
}

function closeXfer() {
  $('xferModal').classList.remove('open');
}

async function confirmXfer() {
  closeXfer();
  const res = await api('POST', '/admin/transfer-balance', { vendor_id: xferVendor.id });
  if (!res.ok) return toast(await errorMessage(res, 'Transfer failed'));
  toast('Transfer started', true);
  await Promise.all([fetchBalance(), fetchVendors()]);
}

async function deleteVendor(vendor) {
  if (!confirm('Delete vendor "' + vendor.name + '"?')) return;
  const res = await api('POST', '/admin/delete', { vendor_id: vendor.id });
  if (!res.ok) return toast(await errorMessage(res, 'Delete failed'));
  toast('Vendor deleted', true);
  await fetchVendors();
}

function navTo(page) {
  // Don't leave the seed on the page
  if (page !== 'settings') clearSeed();
  document.querySelectorAll('.nav-item').forEach(el => el.classList.toggle('active', el.dataset.nav === page));
  document.querySelectorAll('.section').forEach(el => el.classList.toggle('active', el.id === 'sec-' + page));
  document.querySelector('.main').scrollTo({ top: 0 });
  loadAll();
}

// ============ Startup ============
document.addEventListener('DOMContentLoaded', async () => {
  $('loginForm').addEventListener('submit', login);
  $('logoutBtn').addEventListener('click', logout);
  document.querySelectorAll('.nav-item').forEach(el => el.addEventListener('click', () => navTo(el.dataset.nav)));
  $('viewAllTxBtn').addEventListener('click', () => navTo('transactions'));
  $('createInviteBtn').addEventListener('click', () => navTo('invites'));
  $('backupSeedBtn').addEventListener('click', () => navTo('settings'));
  $('loadMoreTxBtn').addEventListener('click', async () => {
    $('loadMoreTxBtn').disabled = true;
    await fetchTx(true);
    $('loadMoreTxBtn').disabled = false;
  });
  $('inviteForm').addEventListener('submit', createInvite);
  $('inviteCodeDisplay').addEventListener('click', () => copy($('inviteCodeDisplay').textContent));
  $('setupWalletBtn').addEventListener('click', setupWallet);
  $('restoreWalletBtn').addEventListener('click', () => {
    $('walletSetupCard').classList.add('hidden');
    $('restoreCard').classList.remove('hidden');
  });
  $('cancelRestoreBtn').addEventListener('click', () => {
    $('restoreCard').classList.add('hidden');
    $('walletSetupCard').classList.remove('hidden');
  });
  $('restoreCard').addEventListener('submit', restoreWallet);
  $('seedForm').addEventListener('submit', showSeed);
  $('seedDoneBtn').addEventListener('click', seedWrittenDown);
  $('xferConfirmBtn').addEventListener('click', confirmXfer);
  $('xferCancelBtn').addEventListener('click', closeXfer);
  $('xferModal').addEventListener('click', e => { if (e.target === e.currentTarget) closeXfer(); });
  document.addEventListener('keydown', e => { if (e.key === 'Escape') closeXfer(); });
  $('posUrl').textContent = location.origin;
  $('copyPosUrlBtn').addEventListener('click', () => copy(location.origin));

  let saved = null;
  try { saved = localStorage.getItem('mm_admin_refresh'); } catch (e) {}
  if (saved) {
    auth.refresh = saved;
    if (await refresh()) showApp();
  }
});
