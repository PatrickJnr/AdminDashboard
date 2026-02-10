// Hytale Admin Dashboard JavaScript

let allPlayers = [];
let dashboardToken = localStorage.getItem('hytale_admin_token');
const avatarCache = new Map();
let connectionStatus = 'connected';
let retryCount = 0;
const MAX_RETRIES = 3;

// Utility Functions
function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

function showNotification(message, type = 'success') {
    const banner = document.createElement('div');
    banner.className = type === 'success' ? 'success-banner' : 'error-banner';
    banner.textContent = message;
    document.body.appendChild(banner);
    
    setTimeout(() => {
        banner.classList.add('fade-out');
        setTimeout(() => banner.remove(), 300);
    }, 3000);
}

function updateConnectionStatus(status) {
    connectionStatus = status;
    const pill = document.querySelector('.refresh-pill');
    if (pill) {
        const pulse = pill.querySelector('.pulse');
        const text = pill.childNodes[2];
        
        if (status === 'connected') {
            pulse.style.background = '#a3cf93';
            text.textContent = ' Live Connection';
            retryCount = 0;
        } else if (status === 'reconnecting') {
            pulse.style.background = '#f4d03f';
            text.textContent = ' Reconnecting...';
        } else {
            pulse.style.background = '#efa3a3';
            text.textContent = ' Disconnected';
        }
    }
}

// Keyboard Shortcuts
document.addEventListener('keydown', (e) => {
    // Ctrl/Cmd + R: Refresh data
    if ((e.ctrlKey || e.metaKey) && e.key === 'r') {
        e.preventDefault();
        fetchStats();
        showNotification('Data refreshed', 'success');
    }
    
    // Escape: Close modals
    if (e.key === 'Escape') {
        closeInventory();
        closeTeleportModal();
        closeActionsModal();
        closeBansFileModal();
    }
    
    // Ctrl/Cmd + F: Focus search
    if ((e.ctrlKey || e.metaKey) && e.key === 'f') {
        e.preventDefault();
        document.getElementById('search').focus();
    }
});

// Show keyboard hint on first load
let hintShown = localStorage.getItem('keyboard_hint_shown');
if (!hintShown) {
    setTimeout(() => {
        const hint = document.createElement('div');
        hint.className = 'keyboard-hint visible';
        hint.innerHTML = 'Tip: Press <kbd>Ctrl</kbd>+<kbd>R</kbd> to refresh, <kbd>Ctrl</kbd>+<kbd>F</kbd> to search';
        document.body.appendChild(hint);
        
        setTimeout(() => {
            hint.classList.remove('visible');
            setTimeout(() => hint.remove(), 200);
        }, 5000);
        
        localStorage.setItem('keyboard_hint_shown', 'true');
    }, 2000);
}

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
    fetchBannedPlayers();
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
        
        // Update memory if available
        if (stats.memory) {
            document.getElementById('server-memory').textContent = formatBytes(stats.memory);
        }
        
        const tpsEl = document.getElementById('server-tps');
        tpsEl.style.color = stats.tps > 18 ? '#a3cf93' : (stats.tps > 15 ? '#f4d06f' : '#b74545');

        updateServerTime();
        updateConnectionStatus('connected');
        return true;
    } catch (e) {
        console.error('Failed to fetch stats', e);
        updateConnectionStatus('reconnecting');
        
        // Retry logic
        if (retryCount < MAX_RETRIES) {
            retryCount++;
            setTimeout(() => fetchStats(isInit), 2000 * retryCount);
        } else {
            updateConnectionStatus('disconnected');
            showNotification('Connection lost. Please check your server.', 'error');
        }
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
            showNotification('Broadcast sent successfully!', 'success');
        } else {
            throw new Error('Failed to send broadcast');
        }
    } catch (e) {
        showNotification('Failed to send broadcast', 'error');
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
        
        // Sort mods alphabetically by name
        mods.sort((a, b) => a.name.localeCompare(b.name));
        
        document.getElementById('mod-count').textContent = mods.length;
        document.getElementById('mod-count-badge').textContent = mods.length;
        const list = document.getElementById('plugin-list');
        list.innerHTML = '';
        mods.forEach(mod => {
            const div = document.createElement('div');
            div.className = 'plugin-item';
            
            // Build author and downloads info
            let metaInfo = '';
            if (mod.author) {
                metaInfo += `<div style="font-size: 0.6875rem; color: var(--text-secondary);">by ${mod.author}</div>`;
            }
            if (mod.downloads) {
                const downloadStr = mod.downloads >= 1000 
                    ? (mod.downloads / 1000).toFixed(1) + 'K' 
                    : mod.downloads;
                metaInfo += `<div style="font-size: 0.6875rem; color: var(--hytale-gold); margin-top: 2px;">
                    <span class="material-symbols-outlined" style="font-size: 0.75rem; vertical-align: middle;">download</span>
                    ${downloadStr} downloads
                </div>`;
            }
            
            // Build the HTML
            div.innerHTML = `
                <div class="plugin-info">
                    <img src="${mod.iconUrl}" class="plugin-icon" onerror="this.src='https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(mod.name)}'">
                    <div style="flex: 1;">
                        <div style="font-weight: 600; display: flex; align-items: center; gap: 0.5rem;">
                            ${mod.name}
                            ${mod.curseforgeUrl ? `<a href="${mod.curseforgeUrl}" target="_blank" rel="noopener" style="color: var(--hytale-gold); text-decoration: none;" title="View on CurseForge">
                                <span class="material-symbols-outlined" style="font-size: 1rem;">open_in_new</span>
                            </a>` : ''}
                        </div>
                        ${metaInfo}
                    </div>
                </div>
            `;
            list.appendChild(div);
        });
    } catch (e) {
        console.error('Failed to fetch mods', e);
    }
}

async function fetchBannedPlayers() {
    if (!dashboardToken) return;
    try {
        const res = await fetch('/api/bans', {
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const bans = await res.json();
        document.getElementById('ban-count-badge').textContent = bans.length;
        const list = document.getElementById('ban-list');
        list.innerHTML = '';
        
        if (bans.length === 0) {
            list.innerHTML = `
                <div class="empty-ban-list">
                    <span class="material-symbols-outlined">check_circle</span>
                    <div>No banned players</div>
                </div>
            `;
            return;
        }
        
        bans.forEach(ban => {
            const div = document.createElement('div');
            div.className = 'ban-item';
            const date = new Date(ban.timestamp);
            const dateStr = date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
            div.innerHTML = `
                <div class="ban-info">
                    <div class="ban-uuid">${ban.uuid}</div>
                    <div class="ban-reason">${ban.reason || 'No reason provided'}</div>
                    <div class="ban-date">Banned on ${dateStr}</div>
                </div>
                <div class="ban-actions">
                    <button class="btn btn-secondary" onclick="unbanPlayer('${ban.uuid}')" title="Unban Player">
                        <span class="material-symbols-outlined">check_circle</span>
                        Unban
                    </button>
                </div>
            `;
            list.appendChild(div);
        });
    } catch (e) {
        console.error('Failed to fetch banned players', e);
    }
}

async function unbanPlayer(uuid) {
    if (!confirm('Are you sure you want to unban this player?')) return;
    
    try {
        const res = await fetch('/api/unban', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({ uuid: uuid })
        });
        const data = await res.json();
        
        if (data.status === 'success') {
            showNotification('Player unbanned successfully', 'success');
            fetchBannedPlayers();
        } else {
            throw new Error(data.error || 'Failed to unban player');
        }
    } catch (e) {
        showNotification(e.message || 'Failed to unban player', 'error');
    }
}

async function viewBansFile() {
    try {
        const res = await fetch('/api/bans/file', {
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const data = await res.json();
        
        if (data.error) {
            showNotification('Failed to load bans file: ' + data.error, 'error');
            return;
        }
        
        // Format JSON for display
        let formattedContent;
        try {
            const parsed = JSON.parse(data.content);
            formattedContent = JSON.stringify(parsed, null, 2);
        } catch (e) {
            formattedContent = data.content;
        }
        
        document.getElementById('bans-file-path').textContent = data.path;
        document.getElementById('bans-file-content').textContent = formattedContent;
        
        if (data.lastModified) {
            const date = new Date(data.lastModified);
            document.getElementById('bans-file-modified').textContent = date.toLocaleString();
        } else {
            document.getElementById('bans-file-modified').textContent = 'Unknown';
        }
        
        document.getElementById('bans-file-modal').classList.add('active');
    } catch (e) {
        showNotification('Failed to load bans file', 'error');
    }
}

function closeBansFileModal() {
    document.getElementById('bans-file-modal').classList.remove('active');
}

async function refreshBansFile() {
    await viewBansFile();
    showNotification('File refreshed', 'success');
}

function copyBansFile() {
    const content = document.getElementById('bans-file-content').textContent;
    navigator.clipboard.writeText(content).then(() => {
        showNotification('Copied to clipboard', 'success');
    }).catch(() => {
        showNotification('Failed to copy to clipboard', 'error');
    });
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
            row.style.opacity = '0';
            row.style.transform = 'translateX(-20px)';
            setTimeout(() => tbody.removeChild(row), 150);
        }
    });

    if (filtered.length === 0 && searchTerm) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8" class="empty-state">
                    <div class="empty-state-icon"><span class="material-symbols-outlined">search_off</span></div>
                    <div class="empty-state-text">No players found</div>
                    <div class="empty-state-subtext">Try a different search term</div>
                </td>
            </tr>
        `;
        return;
    } else if (filtered.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8" class="empty-state">
                    <div class="empty-state-icon"><span class="material-symbols-outlined">group_off</span></div>
                    <div class="empty-state-text">No players online</div>
                    <div class="empty-state-subtext">Waiting for players to join...</div>
                </td>
            </tr>
        `;
        return;
    }

    filtered.forEach(p => {
        let tr = tbody.querySelector(`tr[data-uuid="${p.uuid}"]`);
        const healthPct = (p.health / p.maxHealth) * 100 || 0;
        const staminaPct = p.stamina !== undefined ? (p.stamina / (p.maxStamina || 100)) * 100 : 100;
        const manaPct = p.mana !== undefined ? (p.mana / (p.maxMana || 100)) * 100 : 100;
        const defence = p.defence !== undefined ? p.defence : 0;
        const avatarUrl = p.avatarUrl || `/api/avatar/${p.name}`;

        if (!tr) {
            tr = document.createElement('tr');
            tr.setAttribute('data-uuid', p.uuid);
            tr.style.opacity = '0';
            tr.style.transform = 'translateX(-20px)';
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
                    <div class="p-vitals" style="font-size: 0.8125rem; font-weight: 600; margin-bottom: 2px">
                        <span class="material-symbols-outlined stat-icon health">favorite</span>
                        <span class="p-health-text"></span>
                    </div>
                    <div class="health-bar-container">
                        <div class="health-bar"></div>
                    </div>
                </td>
                <td>
                    <div style="font-size: 0.8125rem; font-weight: 600; margin-bottom: 2px">
                        <span class="material-symbols-outlined stat-icon stamina">bolt</span>
                        <span class="p-stamina-text"></span>
                    </div>
                    <div class="stat-bar-container">
                        <div class="stamina-bar"></div>
                    </div>
                </td>
                <td>
                    <div style="font-size: 0.8125rem; font-weight: 600; margin-bottom: 2px">
                        <span class="material-symbols-outlined stat-icon mana">auto_awesome</span>
                        <span class="p-mana-text"></span>
                    </div>
                    <div class="stat-bar-container">
                        <div class="mana-bar"></div>
                    </div>
                </td>
                <td>
                    <div style="text-align: center; display: flex; flex-direction: column; align-items: center; gap: 4px">
                        <span class="material-symbols-outlined stat-icon defence" style="font-size: 1.5rem">shield</span>
                        <div class="defence-value p-defence" style="line-height: 1"></div>
                    </div>
                </td>
                <td>
                    <div class="p-coords" style="font-size: 0.8125rem; font-family: 'Monaco', monospace"></div>
                    <div class="p-gamemode" style="font-size: 0.6875rem; color: var(--text-secondary); margin-top: 2px"></div>
                </td>
                <td>
                    <div style="display: flex; gap: 0.25rem; justify-content: flex-end; flex-wrap: nowrap">
                        <button class="btn btn-secondary btn-actions" title="Player Actions">
                            <span class="material-symbols-outlined">more_horiz</span>
                            Actions
                        </button>
                    </div>
                </td>
            `;
            tbody.appendChild(tr);
            loadSecureAvatar(tr.querySelector('.avatar'), avatarUrl);
            
            tr.querySelector('.btn-actions').onclick = () => openActionsModal(p.uuid, p.name);
            
            // Animate in
            setTimeout(() => {
                tr.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
                tr.style.opacity = '1';
                tr.style.transform = 'translateX(0)';
            }, 10);
        }

        tr.querySelector('.p-name').textContent = p.name;
        tr.querySelector('.p-uuid-short').textContent = p.uuid.substring(0, 8) + '...';
        tr.querySelector('.p-health-text').textContent = `${Math.round(p.health)}/${Math.round(p.maxHealth)}`;
        tr.querySelector('.health-bar').style.width = healthPct + '%';
        
        // Update stamina
        const staminaText = p.stamina !== undefined ? `${Math.round(p.stamina)}/${Math.round(p.maxStamina || 100)}` : 'N/A';
        tr.querySelector('.p-stamina-text').textContent = staminaText;
        tr.querySelector('.stamina-bar').style.width = staminaPct + '%';
        
        // Update mana
        const manaText = p.mana !== undefined ? `${Math.round(p.mana)}/${Math.round(p.maxMana || 100)}` : 'N/A';
        tr.querySelector('.p-mana-text').textContent = manaText;
        tr.querySelector('.mana-bar').style.width = manaPct + '%';
        
        // Update defence with percentage
        const defenceDisplay = defence > 0 ? `${defence}%` : '0%';
        tr.querySelector('.p-defence').textContent = defenceDisplay;
        
        tr.querySelector('.p-coords').innerHTML = `<span style="color: var(--hytale-gold)">X:</span> ${Math.round(p.x)} <span style="color: var(--hytale-gold)">Y:</span> ${Math.round(p.y)} <span style="color: var(--hytale-gold)">Z:</span> ${Math.round(p.z)}`;
        tr.querySelector('.p-gamemode').textContent = p.gameMode || 'Adventure';
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
        })
        .then(res => {
            if (res.ok) {
                showNotification(`${p ? p.name : 'Player'} has been kicked`, 'success');
                fetchStats();
            } else {
                throw new Error('Failed to kick player');
            }
        })
        .catch(() => {
            showNotification('Failed to kick player', 'error');
        });
    }
}

// Actions Modal
let currentActionPlayer = { uuid: null, name: null };

function openActionsModal(uuid, name) {
    currentActionPlayer = { uuid, name };
    document.getElementById('actions-player-name').textContent = `${name} - Actions`;
    
    // Set up event listeners for action buttons
    document.getElementById('action-inventory').onclick = () => {
        closeActionsModal();
        viewInv(uuid);
    };
    
    document.getElementById('action-teleport').onclick = () => {
        closeActionsModal();
        teleportToPlayer(uuid, name);
    };
    
    document.getElementById('action-op').onclick = () => {
        closeActionsModal();
        toggleOP(uuid, name);
    };
    
    document.getElementById('action-ban').onclick = () => {
        closeActionsModal();
        banPlayer(uuid, name);
    };
    
    document.getElementById('action-kick').onclick = () => {
        closeActionsModal();
        kickPlayer(uuid);
    };
    
    document.getElementById('actions-modal').classList.add('active');
}

function closeActionsModal() {
    document.getElementById('actions-modal').classList.remove('active');
}

function banPlayer(uuid, name) {
    const reason = prompt(`Ban ${name}?\n\nEnter ban reason:`, 'Banned by Admin');
    if (reason !== null) {
        fetch('/api/ban', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({ uuid: uuid, reason: reason })
        })
        .then(res => res.json())
        .then(data => {
            if (data.status === 'success') {
                showNotification(`${name} has been banned`, 'success');
                fetchStats();
                fetchBannedPlayers();
            } else {
                throw new Error(data.error || 'Failed to ban player');
            }
        })
        .catch((err) => {
            showNotification(err.message || 'Failed to ban player', 'error');
        });
    }
}

function toggleOP(uuid, name) {
    if (confirm(`Toggle OP status for ${name}?`)) {
        fetch('/api/op', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({ uuid: uuid })
        })
        .then(res => res.json())
        .then(data => {
            if (data.status === 'success') {
                showNotification(`${name} OP status: ${data.isOp ? 'Granted' : 'Revoked'}`, 'success');
            } else {
                throw new Error(data.error || 'Failed to toggle OP');
            }
        })
        .catch((err) => {
            showNotification(err.message || 'Failed to toggle OP status', 'error');
        });
    }
}

function teleportToPlayer(uuid, name) {
    // Get list of online players for teleport target
    const otherPlayers = allPlayers.filter(p => p.uuid !== uuid);
    if (otherPlayers.length === 0) {
        showNotification('No other players online to teleport to', 'error');
        return;
    }
    
    // Show teleport modal
    const modal = document.getElementById('teleport-modal');
    const playerList = document.getElementById('teleport-player-list');
    document.getElementById('teleport-player-name').textContent = `Teleport ${name}`;
    
    // Clear previous list
    playerList.innerHTML = '';
    
    // Add each player as a clickable option
    otherPlayers.forEach(player => {
        const item = document.createElement('div');
        item.className = 'teleport-player-item';
        item.innerHTML = `
            <img src="${player.avatarUrl || '/api/avatar/' + player.name}" class="avatar" onerror="this.src='https://api.dicebear.com/7.x/bottts/svg?seed=${encodeURIComponent(player.name)}'">
            <div class="teleport-player-info">
                <div class="teleport-player-name">${player.name}</div>
                <div class="teleport-player-location">
                    <span style="color: var(--hytale-gold)">X:</span> ${Math.round(player.x)} 
                    <span style="color: var(--hytale-gold)">Y:</span> ${Math.round(player.y)} 
                    <span style="color: var(--hytale-gold)">Z:</span> ${Math.round(player.z)}
                </div>
            </div>
            <span class="material-symbols-outlined teleport-player-icon">near_me</span>
        `;
        
        item.onclick = () => {
            closeTeleportModal();
            executeTeleport(uuid, name, player.uuid, player.name);
        };
        
        playerList.appendChild(item);
    });
    
    modal.classList.add('active');
}

function closeTeleportModal() {
    const modal = document.getElementById('teleport-modal');
    modal.classList.remove('active');
}

function executeTeleport(uuid, name, targetUuid, targetName) {
    fetch('/api/teleport', {
        method: 'POST',
        headers: { 
            'Content-Type': 'application/json',
            'X-Admin-Token': dashboardToken
        },
        body: JSON.stringify({ uuid: uuid, targetUuid: targetUuid })
    })
    .then(res => {
        if (res.ok) {
            showNotification(`Teleported ${name} to ${targetName}`, 'success');
        } else {
            throw new Error('Failed to teleport');
        }
    })
    .catch(() => {
        showNotification('Failed to teleport player', 'error');
    });
}

setInterval(updateServerTime, 1000);

// Debounce search input
let searchTimeout;
const originalSearchListener = document.getElementById('search').oninput;
document.getElementById('search').addEventListener('input', (e) => {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => renderPlayers(), 300);
});

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

document.getElementById('teleport-modal').addEventListener('click', (e) => {
    if (e.target.id === 'teleport-modal') {
        closeTeleportModal();
    }
});

document.getElementById('actions-modal').addEventListener('click', (e) => {
    if (e.target.id === 'actions-modal') {
        closeActionsModal();
    }
});

document.getElementById('bans-file-modal').addEventListener('click', (e) => {
    if (e.target.id === 'bans-file-modal') {
        closeBansFileModal();
    }
});

// Spin animation for loading
const style = document.createElement('style');
style.textContent = '@keyframes spin { to { transform: rotate(360deg); } }';
document.head.appendChild(style);


// ==================== NEW FEATURES ====================

// Gamemode (Disabled - API compatibility issues)
async function setGamemode(uuid, gamemode) {
    showNotification('Gamemode switching is temporarily disabled due to API compatibility issues', 'error');
    // TODO: Re-enable once correct Hytale API methods are identified
}

// Heal Player
async function healPlayer(uuid) {
    if (!confirm('Heal this player to full health?')) return;
    
    try {
        const response = await fetch('/api/heal', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({ uuid })
        });
        
        const data = await response.json();
        
        if (data.status === 'success') {
            showNotification('Player healed successfully', 'success');
            fetchStats();
        } else {
            showNotification(data.error || 'Failed to heal player', 'error');
        }
    } catch (error) {
        console.error('Error healing player:', error);
        showNotification('Error healing player', 'error');
    }
}

// Time Control (Disabled - API compatibility issues)
async function setTime(time) {
    showNotification('Time control is temporarily disabled due to API compatibility issues', 'error');
    // TODO: Re-enable once correct Hytale API methods are identified
}

// Weather Control (Disabled - API compatibility issues)
async function setWeather(weather) {
    showNotification('Weather control is temporarily disabled due to API compatibility issues', 'error');
    // TODO: Re-enable once correct Hytale API methods are identified
}

// Mute System
async function mutePlayer(uuid, name) {
    const durations = {
        '5 minutes': 300,
        '30 minutes': 1800,
        '1 hour': 3600,
        '1 day': 86400,
        'Permanent': null
    };
    
    const durationChoice = prompt(`Mute ${name}?\n\nSelect duration:\n1. 5 minutes\n2. 30 minutes\n3. 1 hour\n4. 1 day\n5. Permanent\n\nEnter number (1-5):`, '5');
    if (durationChoice === null) return;
    
    const durationKeys = Object.keys(durations);
    const index = parseInt(durationChoice) - 1;
    if (index < 0 || index >= durationKeys.length) {
        showNotification('Invalid duration choice', 'error');
        return;
    }
    
    const durationKey = durationKeys[index];
    const duration = durations[durationKey];
    const reason = prompt('Enter mute reason:', 'Muted by admin');
    if (reason === null) return;
    
    try {
        const response = await fetch('/api/mute', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({ uuid, duration, reason })
        });
        const data = await response.json();
        if (data.status === 'success') {
            showNotification(`${name} has been muted for ${durationKey}`, 'success');
            fetchMutes();
        } else {
            showNotification(data.error || 'Failed to mute player', 'error');
        }
    } catch (error) {
        showNotification('Error muting player', 'error');
    }
}

async function unmutePlayer(uuid) {
    try {
        const response = await fetch('/api/unmute', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({ uuid })
        });
        const data = await response.json();
        if (data.status === 'success') {
            showNotification('Player unmuted successfully', 'success');
            fetchMutes();
        } else {
            showNotification(data.error || 'Failed to unmute player', 'error');
        }
    } catch (error) {
        showNotification('Error unmuting player', 'error');
    }
}

async function fetchMutes() {
    if (!dashboardToken) return;
    try {
        const res = await fetch('/api/mutes', {
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const mutes = await res.json();
        document.getElementById('mute-count-badge').textContent = mutes.length;
        const list = document.getElementById('mute-list');
        list.innerHTML = '';
        
        if (mutes.length === 0) {
            list.innerHTML = `
                <div class="empty-ban-list">
                    <span class="material-symbols-outlined">check_circle</span>
                    <div>No muted players</div>
                </div>
            `;
            return;
        }
        
        mutes.forEach(mute => {
            const div = document.createElement('div');
            div.className = 'ban-item';
            const date = new Date(mute.timestamp);
            const dateStr = date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
            
            let durationStr = 'Permanent';
            if (mute.duration && mute.remaining) {
                const hours = Math.floor(mute.remaining / 3600);
                const minutes = Math.floor((mute.remaining % 3600) / 60);
                if (hours > 0) {
                    durationStr = `${hours}h ${minutes}m remaining`;
                } else {
                    durationStr = `${minutes}m remaining`;
                }
            }
            
            const playerName = mute.name || mute.uuid.substring(0, 8) + '...';
            
            div.innerHTML = `
                <div class="ban-info">
                    <div class="ban-uuid">${playerName}</div>
                    <div class="ban-reason">${mute.reason || 'No reason provided'}</div>
                    <div class="ban-date">Muted on ${dateStr} - ${durationStr}</div>
                </div>
                <div class="ban-actions">
                    <button class="btn btn-secondary" onclick="unmutePlayer('${mute.uuid}')" title="Unmute Player">
                        <span class="material-symbols-outlined">volume_up</span>
                        Unmute
                    </button>
                </div>
            `;
            list.appendChild(div);
        });
    } catch (e) {
        console.error('Failed to fetch mutes', e);
    }
}

// Warp System
let selectedPlayerForWarp = null;

async function fetchWarps() {
    if (!dashboardToken) return;
    try {
        const res = await fetch('/api/warps', {
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const warps = await res.json();
        document.getElementById('warp-count-badge').textContent = warps.length;
        const list = document.getElementById('warp-list');
        list.innerHTML = '';
        
        if (warps.length === 0) {
            list.innerHTML = `
                <div class="empty-ban-list">
                    <span class="material-symbols-outlined">location_off</span>
                    <div>No warp points</div>
                </div>
            `;
            return;
        }
        
        warps.forEach(warp => {
            const div = document.createElement('div');
            div.className = 'ban-item';
            
            div.innerHTML = `
                <div class="ban-info">
                    <div class="ban-uuid" style="font-weight: 600; color: var(--hytale-gold);">${warp.name}</div>
                    <div class="ban-reason" style="font-family: monospace; font-size: 0.75rem;">
                        X: ${Math.round(warp.x)} Y: ${Math.round(warp.y)} Z: ${Math.round(warp.z)}
                    </div>
                    <div class="ban-date">World: ${warp.world}</div>
                </div>
                <div class="ban-actions" style="display: flex; gap: 0.5rem;">
                    <button class="btn btn-secondary" onclick="teleportPlayerToWarp('${warp.name}')" title="Teleport to Warp">
                        <span class="material-symbols-outlined">near_me</span>
                        Teleport
                    </button>
                    <button class="btn btn-secondary" onclick="deleteWarp('${warp.name}')" title="Delete Warp" style="background: var(--danger-color);">
                        <span class="material-symbols-outlined">delete</span>
                    </button>
                </div>
            `;
            list.appendChild(div);
        });
    } catch (e) {
        console.error('Failed to fetch warps', e);
    }
}

async function createWarpFromInput() {
    const nameInput = document.getElementById('warp-name-input');
    const name = nameInput.value.trim();
    
    if (!name) {
        showNotification('Please enter a warp name', 'error');
        return;
    }
    
    if (!selectedPlayerForWarp) {
        showNotification('Please select a player first from the player list', 'error');
        return;
    }
    
    try {
        const response = await fetch('/api/warp/create', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({ name, uuid: selectedPlayerForWarp })
        });
        const data = await response.json();
        if (data.status === 'success') {
            showNotification(`Warp "${name}" created successfully`, 'success');
            nameInput.value = '';
            fetchWarps();
        } else {
            showNotification(data.error || 'Failed to create warp', 'error');
        }
    } catch (error) {
        showNotification('Error creating warp', 'error');
    }
}

async function deleteWarp(name) {
    if (!confirm(`Delete warp "${name}"?`)) return;
    
    try {
        const response = await fetch('/api/warp/delete', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({ name })
        });
        const data = await response.json();
        if (data.status === 'success') {
            showNotification(`Warp "${name}" deleted`, 'success');
            fetchWarps();
        } else {
            showNotification(data.error || 'Failed to delete warp', 'error');
        }
    } catch (error) {
        showNotification('Error deleting warp', 'error');
    }
}

async function teleportPlayerToWarp(warpName) {
    if (!selectedPlayerForWarp) {
        showNotification('Please select a player first from the player list', 'error');
        return;
    }
    
    try {
        const response = await fetch('/api/warp/teleport', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({ uuid: selectedPlayerForWarp, warp: warpName })
        });
        const data = await response.json();
        if (data.status === 'success') {
            showNotification(`Player teleported to "${warpName}"`, 'success');
        } else {
            showNotification(data.error || 'Failed to teleport', 'error');
        }
    } catch (error) {
        showNotification('Error teleporting to warp', 'error');
    }
}

// Give Item (Disabled - API compatibility issues)
async function giveItem(uuid, name) {
    showNotification('Give item is temporarily disabled due to API compatibility issues', 'error');
    // TODO: Re-enable once correct Hytale API methods are identified
}

// Clear Inventory
async function clearInventory(uuid, name) {
    if (!confirm(`Clear all items from ${name}'s inventory? This cannot be undone!`)) return;
    
    try {
        const response = await fetch('/api/clearinv', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({ uuid })
        });
        
        const data = await response.json();
        
        if (data.status === 'success') {
            showNotification('Inventory cleared successfully', 'success');
            fetchStats();
        } else {
            showNotification(data.error || 'Failed to clear inventory', 'error');
        }
    } catch (error) {
        console.error('Error clearing inventory:', error);
        showNotification('Error clearing inventory', 'error');
    }
}

// Update action modal to include new actions
function openActionsModal(uuid, name) {
    currentActionPlayer = { uuid, name };
    selectedPlayerForWarp = uuid; // Set for warp system
    document.getElementById('actions-player-name').textContent = `${name} - Actions`;
    
    // Set up event listeners for action buttons
    document.getElementById('action-inventory').onclick = () => {
        closeActionsModal();
        viewInv(uuid);
    };
    
    document.getElementById('action-gamemode').onclick = () => {
        closeActionsModal();
        setGamemode(uuid, 'SURVIVAL'); // Will show disabled message
    };
    
    document.getElementById('action-heal').onclick = () => {
        closeActionsModal();
        healPlayer(uuid);
    };
    
    document.getElementById('action-teleport').onclick = () => {
        closeActionsModal();
        teleportToPlayer(uuid, name);
    };
    
    document.getElementById('action-give').onclick = () => {
        closeActionsModal();
        giveItem(uuid, name);
    };
    
    document.getElementById('action-clearinv').onclick = () => {
        closeActionsModal();
        clearInventory(uuid, name);
    };
    
    document.getElementById('action-mute').onclick = () => {
        closeActionsModal();
        mutePlayer(uuid, name);
    };
    
    document.getElementById('action-op').onclick = () => {
        closeActionsModal();
        toggleOP(uuid, name);
    };
    
    document.getElementById('action-ban').onclick = () => {
        closeActionsModal();
        banPlayer(uuid, name);
    };
    
    document.getElementById('action-kick').onclick = () => {
        closeActionsModal();
        kickPlayer(uuid);
    };
    
    document.getElementById('actions-modal').classList.add('active');
}

// Update startSync to fetch new data
function startSync() {
    setInterval(fetchStats, 2000);
    setInterval(fetchChat, 2000);
    setInterval(fetchMutes, 5000);
    setInterval(fetchWarps, 10000);
    fetchStats();
    fetchMods();
    fetchBannedPlayers();
    fetchChat();
    fetchMutes();
    fetchWarps();
}
