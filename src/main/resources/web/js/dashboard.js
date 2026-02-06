// Hytale Admin Dashboard JavaScript

let allPlayers = [];
let dashboardToken = localStorage.getItem('hytale_admin_token');
const avatarCache = new Map();

// Login
function login(event) {
    if (event) event.preventDefault();
    const token = document.getElementById('token-input').value;
    if (!token) return;
    
    dashboardToken = token;
    fetchStats(true).then(success => {
        if (success) {
            localStorage.setItem('hytale_admin_token', token);
            const overlay = document.getElementById('login-overlay');
            overlay.classList.add('hidden');
            document.getElementById('dashboard-container').classList.remove('dashboard-locked');
            setTimeout(() => overlay.style.display = 'none', 500);
            startSync();
        } else {
            document.getElementById('login-error').style.display = 'block';
            dashboardToken = null;
        }
    });
}

// Auto-login
if (dashboardToken) {
    fetchStats(true).then(success => {
        if (success) {
            const overlay = document.getElementById('login-overlay');
            overlay.classList.add('hidden');
            document.getElementById('dashboard-container').classList.remove('dashboard-locked');
            setTimeout(() => overlay.style.display = 'none', 500);
            startSync();
        } else {
            localStorage.removeItem('hytale_admin_token');
            dashboardToken = null;
        }
    });
}

function startSync() {
    setInterval(fetchStats, 2000);
    setInterval(fetchChat, 2000);
    fetchStats();
    fetchMods();
    fetchChat();
}

async function fetchStats(isInit = false) {
    if (!dashboardToken) return false;

    try {
        const pRes = await fetch('/api/players', {
            headers: { 'X-Admin-Token': dashboardToken }
        });
        if (pRes.status === 401) return false;
        
        allPlayers = await pRes.json();
        renderPlayers();
        
        const sRes = await fetch('/api/stats', {
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const stats = await sRes.json();
        
        document.getElementById('player-count').textContent = stats.onlinePlayers;
        document.getElementById('server-tps').textContent = stats.tps.toFixed(1);
        
        const tpsEl = document.getElementById('server-tps');
        tpsEl.style.color = stats.tps > 18 ? '#a3cf93' : (stats.tps > 15 ? '#f4d06f' : '#b74545');

        updateServerTime();
        return true;
    } catch (e) {
        console.error('Failed to fetch stats', e);
        return false;
    }
}

function updateServerTime() {
    const now = new Date();
    const timeStr = now.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
    document.getElementById('server-time').textContent = 'SERVER TIME: ' + timeStr;
}

async function broadcast() {
    const msgInput = document.getElementById('broadcast-msg');
    const message = msgInput.value.trim();
    if (!message || !dashboardToken) return;

    try {
        const res = await fetch('/api/broadcast', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({ message: message })
        });
        if (res.ok) {
            msgInput.value = '';
            alert('Broadcast sent!');
        }
    } catch (e) {
        alert('Failed to send broadcast');
    }
}

function toggleAccordion(id) {
    const content = document.getElementById(id);
    const toggle = document.getElementById(id + '-toggle');
    content.classList.toggle('collapsed');
    toggle.classList.toggle('collapsed');
}

async function viewInv(uuid) {
    try {
        const modal = document.getElementById('inventory-modal');
        if (!modal) {
            alert('ERROR: Modal element not found!');
            return;
        }

        document.getElementById('inv-loading').style.display = 'flex';
        document.getElementById('inv-content').style.display = 'none';
        modal.classList.add('active');

        const res = await fetch(`/api/player/${uuid}/inv`, {
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const data = await res.json();
        
        if (data.error) {
            alert('Failed to load inventory: ' + data.error);
            closeInventory();
            return;
        }

        const player = allPlayers.find(p => p.uuid === uuid);
        const playerName = player ? player.name : 'Unknown';
        
        document.getElementById('inv-player-name').textContent = `${playerName}'s Inventory`;

        renderInventorySection('inv-hotbar', data.hotbar || []);
        renderInventorySection('inv-storage', data.storage || []);
        renderInventorySection('inv-armor', data.armor || []);

        document.getElementById('inv-loading').style.display = 'none';
        document.getElementById('inv-content').style.display = 'block';
    } catch (e) {
        alert('Failed to load inventory: ' + e.message);
        closeInventory();
    }
}

function renderInventorySection(containerId, items) {
    const container = document.getElementById(containerId);
    if (!container) return;
    container.innerHTML = '';
    
    let slotCount = 9;
    if (containerId === 'inv-storage') slotCount = 27;
    if (containerId === 'inv-armor') slotCount = 4;
    
    const itemMap = {};
    if (Array.isArray(items)) {
        items.forEach(item => {
            if (item && item.slot !== undefined) {
                itemMap[item.slot] = item;
            }
        });
    }
    
    for (let i = 0; i < slotCount; i++) {
        const slot = document.createElement('div');
        slot.className = 'inv-slot';
        
        const item = itemMap[i];
        if (item && item.id) {
            const icon = document.createElement('img');
            icon.src = `/api/item/${encodeURIComponent(item.id)}/icon`;
            icon.alt = item.id;
            icon.onerror = function() { this.style.display = 'none'; };
            slot.appendChild(icon);
            
            slot.onmouseenter = (e) => {
                const tooltip = document.getElementById('item-tooltip');
                document.getElementById('tooltip-title').textContent = item.id.split(':').pop().replace(/_/g, ' ');
                document.getElementById('tooltip-id').textContent = item.id;
                document.getElementById('tooltip-count').textContent = item.count > 1 ? `Count: ${item.count}` : '';
                tooltip.classList.add('visible');
                positionTooltip(e, tooltip);
            };
            
            slot.onmousemove = (e) => {
                positionTooltip(e, document.getElementById('item-tooltip'));
            };
            
            slot.onmouseleave = () => {
                document.getElementById('item-tooltip').classList.remove('visible');
            };
            
            if (item.count && item.count > 1) {
                const count = document.createElement('div');
                count.className = 'item-count';
                count.textContent = item.count;
                slot.appendChild(count);
            }
        } else {
            slot.classList.add('empty');
        }
        
        container.appendChild(slot);
    }
}

function closeInventory() {
    const modal = document.getElementById('inventory-modal');
    if (modal) {
        modal.classList.remove('active');
    }
    document.getElementById('item-tooltip').classList.remove('visible');
}

function positionTooltip(e, tooltip) {
    const x = e.clientX + 15;
    const y = e.clientY + 15;
    
    const rect = tooltip.getBoundingClientRect();
    const maxX = window.innerWidth - rect.width - 10;
    const maxY = window.innerHeight - rect.height - 10;
    
    tooltip.style.left = Math.min(x, maxX) + 'px';
    tooltip.style.top = Math.min(y, maxY) + 'px';
}

async function fetchMods() {
    if (!dashboardToken) return;
    try {
        const res = await fetch('/api/plugins', {
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const mods = await res.json();
        document.getElementById('mod-count').textContent = mods.length;
        document.getElementById('mod-count-badge').textContent = mods.length;
        const list = document.getElementById('plugin-list');
        list.innerHTML = '';
        mods.forEach(mod => {
            const div = document.createElement('div');
            div.className = 'plugin-item';
            div.innerHTML = `
                <div class="plugin-info">
                    <img src="${mod.iconUrl}" class="plugin-icon" onerror="this.src='https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(mod.name)}'">
                    <div>
                        <div style="font-weight: 600">${mod.name}</div>
                        <div style="font-size: 0.75rem; color: var(--hytale-gold)">v${mod.version}</div>
                    </div>
                </div>
            `;
            list.appendChild(div);
        });
    } catch (e) {
        console.error('Failed to fetch mods', e);
    }
}

async function fetchChat() {
    if (!dashboardToken) return;
    try {
        const res = await fetch('/api/chat', {
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const messages = await res.json();
        renderChat(messages);
    } catch (e) {
        console.error('Failed to fetch chat', e);
    }
}

function renderChat(messages) {
    const container = document.getElementById('console-log');
    const atBottom = container.scrollHeight - container.scrollTop <= container.clientHeight + 50;
    
    container.innerHTML = '';
    messages.forEach(msg => {
        const div = document.createElement('div');
        div.className = 'console-entry';
        const timeStr = new Date(msg.timestamp).toLocaleTimeString([], { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
        div.innerHTML = `
            <span class="console-time">[${timeStr}]</span>
            <span class="console-sender">${msg.sender}:</span>
            <span>${msg.message}</span>
        `;
        container.appendChild(div);
    });

    if (atBottom) {
        container.scrollTop = container.scrollHeight;
    }
}

function renderPlayers() {
    const tbody = document.getElementById('player-table-body');
    const searchTerm = document.getElementById('search').value.toLowerCase();
    
    const filtered = allPlayers.filter(p => p.name.toLowerCase().includes(searchTerm));
    const currentUuids = new Set(filtered.map(p => p.uuid));

    Array.from(tbody.children).forEach(row => {
        const uuid = row.getAttribute('data-uuid');
        if (!currentUuids.has(uuid)) {
            tbody.removeChild(row);
        }
    });

    filtered.forEach(p => {
        let tr = tbody.querySelector(`tr[data-uuid="${p.uuid}"]`);
        const healthPct = (p.health / p.maxHealth) * 100 || 0;
        const avatarUrl = p.avatarUrl || `/api/avatar/${p.name}`;

        if (!tr) {
            tr = document.createElement('tr');
            tr.setAttribute('data-uuid', p.uuid);
            tr.innerHTML = `
                <td>
                    <div class="player-info">
                        <img src="" class="avatar">
                        <div>
                            <div class="p-name" style="font-weight: 600; font-size: 0.9375rem"></div>
                            <div class="p-uuid-short" style="font-size: 0.75rem; color: var(--text-secondary); font-family: monospace"></div>
                        </div>
                    </div>
                </td>
                <td>
                    <span class="badge badge-success">
                        <span class="pulse"></span>
                        Online
                    </span>
                </td>
                <td>
                    <div class="p-vitals" style="font-size: 0.8125rem; font-weight: 600; margin-bottom: 2px"></div>
                    <div class="health-bar-container">
                        <div class="health-bar"></div>
                    </div>
                </td>
                <td>
                    <div class="p-coords" style="font-size: 0.8125rem; font-family: 'Monaco', monospace"></div>
                    <div class="p-gamemode" style="font-size: 0.6875rem; color: var(--text-secondary); margin-top: 2px"></div>
                </td>
                <td>
                    <div style="display: flex; gap: 0.5rem">
                        <button class="btn btn-secondary btn-inv">Inventory</button>
                        <button class="btn btn-danger btn-kick">Kick</button>
                    </div>
                </td>
            `;
            tbody.appendChild(tr);
            loadSecureAvatar(tr.querySelector('.avatar'), avatarUrl);
            
            tr.querySelector('.btn-inv').onclick = () => viewInv(p.uuid);
            tr.querySelector('.btn-kick').onclick = () => kickPlayer(p.uuid);
        }

        tr.querySelector('.p-name').textContent = p.name;
        tr.querySelector('.p-uuid-short').textContent = p.uuid.substring(0, 8) + '...';
        tr.querySelector('.p-vitals').textContent = `${Math.round(p.health)} / ${Math.round(p.maxHealth)} HP`;
        tr.querySelector('.health-bar').style.width = healthPct + '%';
        tr.querySelector('.p-coords').innerHTML = `X: ${Math.round(p.x)} Y: ${Math.round(p.y)} Z: ${Math.round(p.z)}`;
        tr.querySelector('.p-gamemode').textContent = p.gameMode;
    });
}

async function loadSecureAvatar(img, url) {
    if (!dashboardToken) return;
    
    if (avatarCache.has(url)) {
        img.src = avatarCache.get(url);
        return;
    }

    try {
        const res = await fetch(url, {
            headers: { 'X-Admin-Token': dashboardToken }
        });
        if (!res.ok) throw new Error('Failed to fetch avatar');
        const blob = await res.blob();
        const blobUrl = URL.createObjectURL(blob);
        
        avatarCache.set(url, blobUrl);
        img.src = blobUrl;
    } catch (e) {
        img.src = 'https://api.dicebear.com/7.x/bottts/svg?seed=' + encodeURIComponent(url);
    }
}

document.getElementById('search').addEventListener('input', renderPlayers);

function kickPlayer(uuid) {
    const p = allPlayers.find(x => x.uuid === uuid);
    if (confirm(`Are you sure you want to kick ${p ? p.name : 'this player'}?`)) {
        fetch('/api/kick', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({ uuid: uuid, reason: 'Kicked by Admin' })
        }).then(() => fetchStats());
    }
}

setInterval(updateServerTime, 1000);

// Modal close on escape
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        closeInventory();
    }
});

// Modal close on overlay click
document.getElementById('inventory-modal').addEventListener('click', (e) => {
    if (e.target.id === 'inventory-modal') {
        closeInventory();
    }
});

// Spin animation for loading
const style = document.createElement('style');
style.textContent = '@keyframes spin { to { transform: rotate(360deg); } }';
document.head.appendChild(style);
