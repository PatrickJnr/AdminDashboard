// Hytale Admin Dashboard JavaScript

// Tab Switching
function switchTab(tabName) {
    // Hide all tabs
    document.querySelectorAll('.tab-content').forEach(tab => {
        tab.classList.remove('active');
    });
    
    // Show selected tab
    const selectedTab = document.getElementById('tab-' + tabName);
    if (selectedTab) {
        selectedTab.classList.add('active');
    }
    
    // Update sidebar active state
    document.querySelectorAll('.sidebar-item').forEach(item => {
        item.classList.remove('active');
    });
    const activeItem = document.querySelector(`[data-tab="${tabName}"]`);
    if (activeItem) {
        activeItem.classList.add('active');
    }
}

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

function formatUptime(ms) {
    const seconds = Math.floor(ms / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);
    
    if (days > 0) {
        return `${days}d ${hours % 24}h`;
    } else if (hours > 0) {
        return `${hours}h ${minutes % 60}m`;
    } else if (minutes > 0) {
        return `${minutes}m ${seconds % 60}s`;
    } else {
        return `${seconds}s`;
    }
}

function showNotification(message, type = 'success') {
    const banner = document.createElement('div');
    banner.className = type === 'success' ? 'success-banner' : 'error-banner';
    banner.textContent = message;
    document.body.appendChild(banner);
    
    setTimeout(() => {
        banner.classList.add('fade-out');
        setTimeout(() => banner.remove(), 400);
    }, 2500);
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

async function fetchVersion() {
    try {
        const res = await fetch('/api/version');
        if (!res.ok) {
            throw new Error('Failed to fetch version');
        }
        const data = await res.json();
        if (data.version) {
            document.getElementById('sidebar-version').textContent = 'v' + data.version;
        }
    } catch (e) {
        document.getElementById('sidebar-version').textContent = 'v1.0.0';
    }
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
        
        // Update uptime if available
        if (stats.uptimeMs) {
            document.getElementById('server-uptime').textContent = formatUptime(stats.uptimeMs);
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
    const arrow = document.getElementById(id + '-toggle');
    
    if (!content || !arrow) return;
    
    const isCollapsed = content.classList.contains('collapsed');
    
    if (isCollapsed) {
        // Expand
        content.classList.remove('collapsed');
        arrow.classList.remove('collapsed');
    } else {
        // Collapse
        content.classList.add('collapsed');
        arrow.classList.add('collapsed');
    }
}

async function viewInv(uuid) {
    try {
        const modal = document.getElementById('inventory-modal');
        if (!modal) {
            await customAlert('ERROR: Modal element not found!', 'Error');
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
            await customAlert('Failed to load inventory: ' + data.error, 'Error');
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
        await customAlert('Failed to load inventory: ' + e.message, 'Error');
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
        
        // Update counts
        document.getElementById('mod-count').textContent = mods.length;
        const modCountBadgeServer = document.getElementById('mod-count-badge-server');
        if (modCountBadgeServer) {
            modCountBadgeServer.textContent = mods.length;
        }
        
        // Populate Server tab mods list
        const listServer = document.getElementById('plugin-list-server');
        if (listServer) {
            listServer.innerHTML = '';
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
                listServer.appendChild(div);
            });
        }
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
    if (!await customConfirm('Are you sure you want to unban this player?', 'Confirm Unban')) return;
    
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
            setTimeout(() => {
                if (row.parentNode === tbody) {
                    tbody.removeChild(row);
                }
            }, 150);
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

async function kickPlayer(uuid) {
    const p = allPlayers.find(x => x.uuid === uuid);
    if (await customConfirm(`Are you sure you want to kick ${p ? p.name : 'this player'}?`, 'Confirm Kick', true)) {
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

async function banPlayer(uuid, name) {
    const reason = await customPrompt(`Ban ${name}?\n\nEnter ban reason:`, 'Banned by Admin', 'Ban Player');
    if (reason !== null && reason !== '') {
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

async function toggleOP(uuid, name) {
    if (await customConfirm(`Toggle OP status for ${name}?`, 'Confirm OP Toggle')) {
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
    try {
        const response = await fetch('/api/gamemode', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({ uuid, gamemode })
        });
        const data = await response.json();
        if (data.status === 'success') {
            showNotification(`Gamemode changed to ${gamemode}`, 'success');
        } else {
            showNotification(data.error || 'Failed to change gamemode', 'error');
        }
    } catch (error) {
        showNotification('Error changing gamemode: ' + error.message, 'error');
    }
}

// Heal Player
async function healPlayer(uuid) {
    if (!await customConfirm('Heal this player to full health?', 'Confirm Heal')) return;
    
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
    
    const durationChoice = await customPrompt(`Mute ${name}?\n\nSelect duration:\n1. 5 minutes\n2. 30 minutes\n3. 1 hour\n4. 1 day\n5. Permanent\n\nEnter number (1-5):`, '5', 'Mute Player');
    if (durationChoice === null || durationChoice === '') return;
    
    const durationKeys = Object.keys(durations);
    const index = parseInt(durationChoice) - 1;
    if (index < 0 || index >= durationKeys.length) {
        showNotification('Invalid duration choice', 'error');
        return;
    }
    
    const durationKey = durationKeys[index];
    const duration = durations[durationKey];
    const reason = await customPrompt('Enter mute reason:', 'Muted by admin', 'Mute Reason');
    if (reason === null || reason === '') return;
    
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
    if (!await customConfirm(`Delete warp "${name}"?`, 'Confirm Delete', true)) return;
    
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
    // Open modal to select which player to teleport
    openWarpTeleportModal(warpName);
}

let currentWarpForTeleport = null;

async function openWarpTeleportModal(warpName) {
    currentWarpForTeleport = warpName;
    const modal = document.getElementById('warp-teleport-modal');
    const title = document.getElementById('warp-teleport-title');
    const playerList = document.getElementById('warp-teleport-player-list');
    
    title.textContent = `Teleport to "${warpName}"`;
    playerList.innerHTML = '<div style="text-align: center; padding: 2rem; color: var(--text-secondary);">Loading players...</div>';
    
    modal.classList.add('active');
    
    try {
        const res = await fetch('/api/players', {
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const players = await res.json();
        
        playerList.innerHTML = '';
        
        if (players.length === 0) {
            playerList.innerHTML = '<div style="text-align: center; padding: 2rem; color: var(--text-secondary);">No online players</div>';
            return;
        }
        
        // Create grid of player avatars
        const grid = document.createElement('div');
        grid.style.cssText = 'display: grid; grid-template-columns: repeat(auto-fill, minmax(90px, 1fr)); gap: 0.75rem;';
        
        players.forEach(player => {
            const div = document.createElement('div');
            div.className = 'warp-player-icon';
            
            // Create avatar image
            const avatarUrl = `/api/avatar/${encodeURIComponent(player.uuid)}`;
            
            div.innerHTML = `
                <img src="${avatarUrl}" alt="${player.name}" style="width: 64px; height: 64px; border-radius: 8px; image-rendering: pixelated;">
                <div class="warp-player-tooltip">${player.name}</div>
            `;
            div.onclick = () => executeWarpTeleport(player.uuid, player.name);
            grid.appendChild(div);
        });
        
        playerList.appendChild(grid);
    } catch (e) {
        playerList.innerHTML = '<div style="text-align: center; padding: 2rem; color: var(--danger-color);">Failed to load players</div>';
    }
}

function closeWarpTeleportModal() {
    document.getElementById('warp-teleport-modal').classList.remove('active');
    currentWarpForTeleport = null;
}

async function executeWarpTeleport(uuid, playerName) {
    if (!currentWarpForTeleport) {
        showNotification('No warp selected', 'error');
        return;
    }
    
    // Store the warp name before closing modal (which sets it to null)
    const warpName = currentWarpForTeleport;
    
    closeWarpTeleportModal();
    
    try {
        const payload = { 
            uuid: uuid, 
            warp: warpName 
        };
        
        const response = await fetch('/api/warp/teleport', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify(payload)
        });
        
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        
        const data = await response.json();
        if (data.status === 'success') {
            showNotification(`${playerName} teleported to "${warpName}"`, 'success');
        } else {
            const errorMsg = data.error && data.error !== 'null' ? data.error : 'Failed to teleport';
            showNotification(errorMsg, 'error');
        }
    } catch (error) {
        showNotification('Error teleporting to warp: ' + error.message, 'error');
    }
}

// Give Item (Disabled - API compatibility issues)
async function giveItem(uuid, name) {
    const itemId = await customPrompt('Enter item ID (e.g., hytale:stone):', '', 'Give Item');
    if (!itemId) return;
    
    const quantityStr = await customPrompt('Enter quantity (1-999):', '1', 'Give Item');
    if (!quantityStr) return;
    
    const quantity = parseInt(quantityStr);
    if (isNaN(quantity) || quantity < 1 || quantity > 999) {
        showNotification('Invalid quantity. Must be between 1 and 999', 'error');
        return;
    }
    
    try {
        const response = await fetch('/api/give', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({ uuid, itemId, quantity })
        });
        const data = await response.json();
        if (data.status === 'success') {
            showNotification(`Gave ${quantity}x ${itemId} to ${name}`, 'success');
        } else {
            showNotification(data.error || 'Failed to give item', 'error');
        }
    } catch (error) {
        showNotification('Error giving item: ' + error.message, 'error');
    }
}

// Clear Inventory
async function clearInventory(uuid, name) {
    if (!await customConfirm(`Clear all items from ${name}'s inventory? This cannot be undone!`, 'Confirm Clear Inventory', true)) return;
    
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
        openGamemodeModal(uuid, name);
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
        openGiveItemModal(uuid, name);
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
    fetchVersion();
}

// Time Control
async function setTime(time) {
    try {
        const response = await fetch('/api/time', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({ time })
        });

        const data = await response.json();

        if (data.status === 'success') {
            showNotification(`Time set to ${time}`, 'success');
        } else {
            showNotification(data.error || 'Failed to set time', 'error');
        }
    } catch (error) {
        console.error('Error setting time:', error);
        showNotification('Error setting time', 'error');
    }
}

// Weather Control
async function setWeather(weather) {
    try {
        const response = await fetch('/api/weather', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({ weather })
        });

        const data = await response.json();

        if (data.status === 'success') {
            showNotification(`Weather set to ${weather}`, 'success');
        } else {
            showNotification(data.error || 'Failed to set weather', 'error');
        }
    } catch (error) {
        console.error('Error setting weather:', error);
        showNotification('Error setting weather', 'error');
    }
}

// ==================== CUSTOM MODAL SYSTEM ====================
// Replace browser confirm(), prompt(), and alert() with custom modals

let confirmResolve = null;
let promptResolve = null;

// Custom confirm dialog
function customConfirm(message, title = 'Confirm Action', isDanger = false) {
    return new Promise((resolve) => {
        confirmResolve = resolve;
        document.getElementById('confirm-title').textContent = title;
        document.getElementById('confirm-message').textContent = message;
        
        const confirmBtn = document.getElementById('confirm-btn');
        if (isDanger) {
            confirmBtn.className = 'btn btn-danger';
        } else {
            confirmBtn.className = 'btn btn-primary';
        }
        
        document.getElementById('confirm-modal').classList.add('active');
        
        // Focus on confirm button
        setTimeout(() => confirmBtn.focus(), 100);
    });
}

function closeConfirmModal(result) {
    document.getElementById('confirm-modal').classList.remove('active');
    if (confirmResolve) {
        confirmResolve(result);
        confirmResolve = null;
    }
}

// Custom prompt dialog
function customPrompt(message, defaultValue = '', title = 'Input Required') {
    return new Promise((resolve) => {
        promptResolve = resolve;
        document.getElementById('prompt-title').textContent = title;
        document.getElementById('prompt-message').textContent = message;
        document.getElementById('prompt-input').value = defaultValue;
        document.getElementById('prompt-modal').classList.add('active');
        
        // Focus on input
        setTimeout(() => {
            const input = document.getElementById('prompt-input');
            input.focus();
            input.select();
        }, 100);
        
        // Handle Enter key
        const input = document.getElementById('prompt-input');
        input.onkeydown = (e) => {
            if (e.key === 'Enter') {
                closePromptModal(input.value);
            } else if (e.key === 'Escape') {
                closePromptModal(null);
            }
        };
    });
}

function closePromptModal(result) {
    document.getElementById('prompt-modal').classList.remove('active');
    if (promptResolve) {
        promptResolve(result);
        promptResolve = null;
    }
}

// Custom alert dialog
function customAlert(message, title = 'Notice') {
    return new Promise((resolve) => {
        document.getElementById('alert-title').textContent = title;
        document.getElementById('alert-message').textContent = message;
        document.getElementById('alert-modal').classList.add('active');
        
        // Store resolve for when modal closes
        window.alertResolve = resolve;
    });
}

function closeAlertModal() {
    document.getElementById('alert-modal').classList.remove('active');
    if (window.alertResolve) {
        window.alertResolve();
        window.alertResolve = null;
    }
}

// Close modals on Escape key
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        if (document.getElementById('confirm-modal').classList.contains('active')) {
            closeConfirmModal(false);
        }
        if (document.getElementById('prompt-modal').classList.contains('active')) {
            closePromptModal(null);
        }
        if (document.getElementById('alert-modal').classList.contains('active')) {
            closeAlertModal();
        }
    }
});

// Close modals on overlay click
document.getElementById('confirm-modal').addEventListener('click', (e) => {
    if (e.target.id === 'confirm-modal') closeConfirmModal(false);
});
document.getElementById('prompt-modal').addEventListener('click', (e) => {
    if (e.target.id === 'prompt-modal') closePromptModal(null);
});
document.getElementById('alert-modal').addEventListener('click', (e) => {
    if (e.target.id === 'alert-modal') closeAlertModal();
});


// ==================== IMPROVED GAMEMODE MODAL ====================
function openGamemodeModal(uuid, name) {
    const player = allPlayers.find(p => p.uuid === uuid);
    const currentGamemode = player ? player.gameMode : 'Adventure';
    
    document.getElementById('gamemode-player-name').textContent = `${name} - Change Gamemode`;
    document.getElementById('gamemode-current').textContent = `Current: ${currentGamemode}`;
    
    // Highlight current gamemode
    const creativeBtn = document.getElementById('gamemode-creative-btn');
    const adventureBtn = document.getElementById('gamemode-adventure-btn');
    
    creativeBtn.classList.remove('active-gamemode');
    adventureBtn.classList.remove('active-gamemode');
    
    if (currentGamemode.toLowerCase() === 'creative') {
        creativeBtn.classList.add('active-gamemode');
    } else {
        adventureBtn.classList.add('active-gamemode');
    }
    
    // Set up click handlers
    creativeBtn.onclick = () => {
        closeGamemodeModal();
        setGamemode(uuid, 'creative');
    };
    
    adventureBtn.onclick = () => {
        closeGamemodeModal();
        setGamemode(uuid, 'adventure');
    };
    
    document.getElementById('gamemode-modal').classList.add('active');
}

function closeGamemodeModal() {
    document.getElementById('gamemode-modal').classList.remove('active');
}

// ==================== IMPROVED GIVE ITEM MODAL ====================
let allItems = [];
let filteredItems = [];
let selectedItemForGive = null;

async function openGiveItemModal(uuid, name) {
    document.getElementById('giveitem-player-name').textContent = `Give Item to ${name}`;
    document.getElementById('giveitem-search').value = '';
    document.getElementById('giveitem-quantity').value = '1';
    selectedItemForGive = null;
    
    // Load items if not already loaded
    if (allItems.length === 0) {
        document.getElementById('giveitem-loading').style.display = 'flex';
        document.getElementById('giveitem-browser').style.display = 'none';
        await loadAllItems();
        document.getElementById('giveitem-loading').style.display = 'none';
        document.getElementById('giveitem-browser').style.display = 'block';
    }
    
    // Show all items initially (sorted alphabetically)
    filteredItems = [...allItems];
    renderItemBrowser();
    updateItemCount();
    
    // Set up search
    const searchInput = document.getElementById('giveitem-search');
    searchInput.oninput = () => {
        const query = searchInput.value.toLowerCase().trim();
        if (query === '') {
            filteredItems = [...allItems];
        } else {
            filteredItems = allItems.filter(item => {
                const nameLower = item.name.toLowerCase();
                const idLower = item.id.toLowerCase();
                const keywords = item.keywords || nameLower.replace(/\s+/g, '');
                
                // Match against name, ID, or keywords (without spaces)
                return nameLower.includes(query) || 
                       idLower.includes(query) || 
                       keywords.includes(query.replace(/\s+/g, ''));
            });
        }
        renderItemBrowser();
        updateItemCount();
    };
    
    // Set up give button
    document.getElementById('giveitem-confirm-btn').onclick = () => {
        if (!selectedItemForGive) {
            showNotification('Please select an item first', 'error');
            return;
        }
        const quantity = parseInt(document.getElementById('giveitem-quantity').value);
        if (isNaN(quantity) || quantity < 1 || quantity > 999) {
            showNotification('Invalid quantity. Must be between 1 and 999', 'error');
            return;
        }
        closeGiveItemModal();
        executeGiveItem(uuid, name, selectedItemForGive, quantity);
    };
    
    document.getElementById('giveitem-modal').classList.add('active');
    
    // Focus search input
    setTimeout(() => searchInput.focus(), 100);
}

function updateItemCount() {
    const countEl = document.getElementById('item-count');
    if (filteredItems.length === allItems.length) {
        countEl.textContent = `${allItems.length} items`;
    } else {
        countEl.textContent = `${filteredItems.length} of ${allItems.length} items`;
    }
}

function closeGiveItemModal() {
    document.getElementById('giveitem-modal').classList.remove('active');
}

async function loadAllItems() {
    try {
        const response = await fetch('/api/items', {
            headers: { 'X-Admin-Token': dashboardToken }
        });
        
        const data = await response.json();
        
        if (data.error) {
            showNotification('Failed to load items: ' + data.error, 'error');
            allItems = [];
            return;
        }
        
        allItems = data.items || [];
        allItems.sort((a, b) => a.name.localeCompare(b.name));
    } catch (error) {
        showNotification('Failed to load items', 'error');
        allItems = [];
    }
}

function renderItemBrowser() {
    const container = document.getElementById('giveitem-items');
    container.innerHTML = '';
    
    if (filteredItems.length === 0) {
        container.innerHTML = `
            <div class="empty-item-state">
                <span class="material-symbols-outlined" style="font-size: 4rem; opacity: 0.3; color: var(--text-secondary);">search_off</span>
                <div style="margin-top: 1rem; font-size: 1.125rem; font-weight: 600;">No items found</div>
                <div style="margin-top: 0.5rem; font-size: 0.875rem; color: var(--text-secondary);">Try a different search term</div>
            </div>
        `;
        return;
    }
    
    // Limit to first 100 items for performance
    const itemsToShow = filteredItems.slice(0, 100);
    
    itemsToShow.forEach(item => {
        const itemCard = document.createElement('div');
        itemCard.className = 'item-browser-card';
        if (selectedItemForGive === item.id) {
            itemCard.classList.add('selected');
        }
        
        itemCard.innerHTML = `
            <div class="item-browser-icon-wrapper">
                <img src="/api/item/${encodeURIComponent(item.id)}/icon" 
                     alt="${item.name}" 
                     class="item-browser-icon"
                     onerror="this.src='data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2248%22 height=%2248%22%3E%3Crect width=%2248%22 height=%2248%22 fill=%22%23333%22/%3E%3Ctext x=%2224%22 y=%2224%22 text-anchor=%22middle%22 dy=%22.3em%22 fill=%22%23666%22 font-size=%2216%22%3E?%3C/text%3E%3C/svg%3E'">
            </div>
            <div class="item-browser-info">
                <div class="item-browser-name" title="${item.name}">${item.name}</div>
                <div class="item-browser-id" title="${item.id}">${item.id}</div>
            </div>
        `;
        
        itemCard.onclick = () => {
            selectedItemForGive = item.id;
            // Update selection visually
            container.querySelectorAll('.item-browser-card').forEach(card => {
                card.classList.remove('selected');
            });
            itemCard.classList.add('selected');
            
            // Scroll selected item into view if needed
            itemCard.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        };
        
        container.appendChild(itemCard);
    });
    
    if (filteredItems.length > 100) {
        const moreDiv = document.createElement('div');
        moreDiv.className = 'item-browser-more';
        moreDiv.innerHTML = `
            <span class="material-symbols-outlined">info</span>
            <div>Showing first 100 of ${filteredItems.length} items</div>
            <div style="font-size: 0.75rem; margin-top: 0.25rem;">Use search to narrow results</div>
        `;
        container.appendChild(moreDiv);
    }
}

async function executeGiveItem(uuid, name, itemId, quantity) {
    try {
        const response = await fetch('/api/give', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({ uuid, itemId, quantity })
        });
        const data = await response.json();
        if (data.status === 'success') {
            const itemName = allItems.find(i => i.id === itemId)?.name || itemId;
            showNotification(`Gave ${quantity}x ${itemName} to ${name}`, 'success');
        } else {
            showNotification(data.error || 'Failed to give item', 'error');
        }
    } catch (error) {
        showNotification('Error giving item: ' + error.message, 'error');
    }
}

// Close modals on Escape and overlay click
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        if (document.getElementById('gamemode-modal').classList.contains('active')) {
            closeGamemodeModal();
        }
        if (document.getElementById('giveitem-modal').classList.contains('active')) {
            closeGiveItemModal();
        }
    }
});

// Add overlay click handlers for new modals
if (document.getElementById('gamemode-modal')) {
    document.getElementById('gamemode-modal').addEventListener('click', (e) => {
        if (e.target.id === 'gamemode-modal') {
            closeGamemodeModal();
        }
    });
}

if (document.getElementById('giveitem-modal')) {
    document.getElementById('giveitem-modal').addEventListener('click', (e) => {
        if (e.target.id === 'giveitem-modal') {
            closeGiveItemModal();
        }
    });
}


// ==================== CURSEFORGE CACHE MANAGEMENT ====================
async function clearCurseForgeCache() {
    try {
        const response = await fetch('/api/curseforge/clear-cache', {
            method: 'POST',
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const data = await response.json();
        if (data.status === 'success') {
            showNotification('CurseForge cache cleared! Refresh the page to see updated mod info.', 'success');
            // Optionally refresh mods automatically
            setTimeout(() => {
                location.reload();
            }, 2000);
        } else {
            showNotification('Failed to clear cache: ' + (data.error || 'Unknown error'), 'error');
        }
    } catch (error) {
        showNotification('Error clearing cache: ' + error.message, 'error');
    }
}

// Make it available globally for console access
window.clearCurseForgeCache = clearCurseForgeCache;
