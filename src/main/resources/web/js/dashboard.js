

const originalFetch = window.fetch;
window.fetch = function() {
    let [resource, config] = arguments;
    if(config === undefined) {
        config = {};
    }
    config.credentials = 'same-origin';
    return originalFetch(resource, config);
};

const routes = {
    '/': 'dashboard',
    '/dashboard': 'dashboard',
    '/players': 'players',
    '/world': 'world',
    '/metrics': 'metrics',
    '/logs': 'logs',
    '/files': 'files',
    '/config': 'config',
    '/info': 'info'
};

function switchPlayerSubTab(subTab) {
    const onlineView = document.getElementById('view-players-online');
    const bannedView = document.getElementById('view-players-banned');
    const btnOnline = document.getElementById('btn-sub-online');
    const btnBanned = document.getElementById('btn-sub-banned');

    if (subTab === 'online') {
        if (onlineView) onlineView.style.display = 'block';
        if (bannedView) bannedView.style.display = 'none';
        if (btnOnline) {
            btnOnline.classList.remove('btn-secondary');
            btnOnline.classList.add('btn-primary');
        }
        if (btnBanned) {
            btnBanned.classList.remove('btn-primary');
            btnBanned.classList.add('btn-secondary');
        }
    } else {
        if (onlineView) onlineView.style.display = 'none';
        if (bannedView) bannedView.style.display = 'block';
        if (btnOnline) {
            btnOnline.classList.remove('btn-primary');
            btnOnline.classList.add('btn-secondary');
        }
        if (btnBanned) {
            btnBanned.classList.remove('btn-secondary');
            btnBanned.classList.add('btn-primary');
        }
    }
}

function switchTab(tabName, updateHistory = true) {
    document.querySelectorAll('.tab-content').forEach(tab => {
        tab.classList.remove('active');
    });

    const selectedTab = document.getElementById('tab-' + tabName);
    if (selectedTab) {
        selectedTab.classList.add('active');
    }

    document.querySelectorAll('.sidebar-item').forEach(item => {
        item.classList.remove('active');
    });
    const activeItem = document.querySelector(`[data-tab="${tabName}"]`);
    if (activeItem) {
        activeItem.classList.add('active');
    }

    
    if (updateHistory) {
        const path = '/' + tabName;
        history.pushState({ tab: tabName }, '', path);
    }
}

window.addEventListener('popstate', (event) => {
    if (event.state && event.state.tab) {
        switchTab(event.state.tab, false);
    } else {
        
        const path = window.location.pathname;
        const tab = routes[path] || 'dashboard';
        switchTab(tab, false);
    }
});

let allPlayers = [];
let dashboardToken = localStorage.getItem('hytale_admin_token');
const avatarCache = new Map();
let connectionStatus = 'connected';
let retryCount = 0;
const MAX_RETRIES = 3;


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
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    
    const iconMap = {
        'success': 'check_circle',
        'error': 'error',
        'info': 'info'
    };

    toast.innerHTML = `
        <span class="material-symbols-outlined">${iconMap[type] || 'info'}</span>
        <span>${message}</span>
    `;

    container.appendChild(toast);
    
    
    setTimeout(() => toast.classList.add('visible'), 10);
    
    
    setTimeout(() => {
        toast.classList.remove('visible');
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

async function executeCommand() {
    const input = document.getElementById('console-command-input');
    const cmd = input.value.trim();
    if (!cmd || !dashboardToken) return;

    input.value = '';
    const btn = event?.target || document.querySelector('#console-command-area button');
    if (btn) btn.classList.add('loading');

    try {
        const res = await fetch('/api/console/execute', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({ command: cmd })
        });
        const data = await res.json();
        
        if (data.status === 'success') {
            showNotification('Command executed: ' + cmd, 'success');
        } else {
            showNotification('Command failed: ' + (data.error || 'Unknown error'), 'error');
        }
    } catch (e) {
        showNotification('Failed to execute command', 'error');
    } finally {
        if (btn) btn.classList.remove('loading');
    }
}

let currentConsoleMode = 'chat';
function switchConsoleTab(mode) {
    currentConsoleMode = mode;
    const btnChat = document.getElementById('btn-mode-chat');
    const btnConsole = document.getElementById('btn-mode-console');
    const headerTitle = document.getElementById('console-header-title');
    const commandArea = document.getElementById('console-command-area');

    if (mode === 'chat') {
        btnChat.classList.replace('btn-secondary', 'btn-primary');
        btnConsole.classList.replace('btn-primary', 'btn-secondary');
        if (headerTitle) headerTitle.textContent = 'Server Chat';
        if (commandArea) commandArea.style.display = 'none';
        fetchChat();
    } else {
        btnChat.classList.replace('btn-primary', 'btn-secondary');
        btnConsole.classList.replace('btn-secondary', 'btn-primary');
        if (headerTitle) headerTitle.textContent = 'Server Console';
        if (commandArea) commandArea.style.display = 'flex';
        fetchConsole();
    }
}

async function fetchChat() {
    if (!dashboardToken || currentConsoleMode !== 'chat') return;
    try {
        const res = await fetch('/api/chat', { headers: { 'X-Admin-Token': dashboardToken } });
        const data = await res.json();
        renderConsole(data.logs || []);
    } catch (e) { console.error('Failed to fetch chat', e); }
}

async function fetchConsole() {
    if (!dashboardToken || currentConsoleMode !== 'console') return;
    try {
        const res = await fetch('/api/console', { headers: { 'X-Admin-Token': dashboardToken } });
        const data = await res.json();
        renderConsole(data.logs || []);
    } catch (e) { console.error('Failed to fetch console', e); }
}

function renderConsole(logs) {
    const container = document.getElementById('console-log');
    if (!container) return;
    container.innerHTML = logs.map(line => `<div class="console-entry">${line}</div>`).join('');
    container.scrollTop = container.scrollHeight;
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


document.addEventListener('keydown', (e) => {

    if ((e.ctrlKey || e.metaKey) && e.key === 'r') {
        e.preventDefault();
        fetchStats();
        showNotification('Data refreshed', 'success');
    }
    

    if (e.key === 'Escape') {
        closeInventory();
        closeTeleportModal();
        closeActionsModal();
        closeBansFileModal();
    }
    

    if ((e.ctrlKey || e.metaKey) && e.key === 'f') {
        e.preventDefault();
        document.getElementById('search').focus();
    }
});


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


function login(event) {
    if (event) event.preventDefault();
    const token = document.getElementById('token-input').value;
    if (!token) return;
    
    
    fetch('/api/login', {
        method: 'POST',
        headers: { 'X-Admin-Token': token }
    }).then(res => {
        if (res.ok) {
            dashboardToken = "session_active"; 
            localStorage.setItem('hytale_admin_token', 'session_active'); 
            const overlay = document.getElementById('login-overlay');
            overlay.classList.add('hidden');
            document.getElementById('dashboard-container').classList.remove('dashboard-locked');
            setTimeout(() => overlay.style.display = 'none', 500);
            fetchStats(true); 
            startSync();
        } else {
            document.getElementById('login-error').style.display = 'block';
            dashboardToken = null;
        }
    }).catch(err => {
        console.error("Login request failed", err);
        document.getElementById('login-error').style.display = 'block';
    });
}


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
            const infoVer = document.getElementById('info-version');
            if (infoVer) infoVer.textContent = 'v' + data.version;

            
            if (data.javaVersion) document.getElementById('sys-java').textContent = data.javaVersion;
            if (data.osName) document.getElementById('sys-os-name').textContent = data.osName;
            if (data.osArch) document.getElementById('sys-os-arch').textContent = data.osArch;
            if (data.cores) document.getElementById('sys-cores').textContent = data.cores;

            
            if (data.heapUsed && data.heapMax) {
                const memPct = Math.round((data.heapUsed / data.heapMax) * 100);
                const memText = document.getElementById('sys-memory-text');
                const memBar = document.getElementById('sys-memory-bar');
                if (memText) memText.textContent = `${formatBytes(data.heapUsed)} / ${formatBytes(data.heapMax)}`;
                if (memBar) memBar.style.width = `${memPct}%`;
            }

            
            if (data.diskUsed && data.diskTotal) {
                const diskPct = Math.round((data.diskUsed / data.diskTotal) * 100);
                const diskText = document.getElementById('sys-disk-text');
                const diskBar = document.getElementById('sys-disk-bar');
                if (diskText) diskText.textContent = `${formatBytes(data.diskUsed)} / ${formatBytes(data.diskTotal)}`;
                if (diskBar) diskBar.style.width = `${diskPct}%`;
            }
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
        
        
        if (stats.memory) {
            document.getElementById('server-memory').textContent = formatBytes(stats.memory);
        }
        
        
        if (stats.uptimeMs) {
            document.getElementById('server-uptime').textContent = formatUptime(stats.uptimeMs);
        }
        
        const tpsEl = document.getElementById('server-tps');
        tpsEl.style.color = stats.tps > 18 ? '#a3cf93' : (stats.tps > 15 ? '#f4d06f' : '#b74545');

        updateServerTime();
        fetchEntityStats();
        fetchChat();
        fetchConsole();
        updateConnectionStatus('connected');
        return true;
    } catch (e) {
        console.error('Failed to fetch stats', e);
        updateConnectionStatus('reconnecting');
        
        
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

async function fetchEntityStats() {
    if (!dashboardToken || document.hidden) return;
    try {
        const res = await fetch('/api/world/entities', {
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const data = await res.json();
        const el = document.getElementById('total-entities');
        if (el) el.textContent = data.total || 0;
    } catch (e) { console.error('Failed to fetch entity stats', e); }
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
        
        content.classList.remove('collapsed');
        arrow.classList.remove('collapsed');
    } else {
        
        content.classList.add('collapsed');
        arrow.classList.add('collapsed');
    }
}

let currentInventoryPlayerUuid = null;

async function viewInv(uuid) {
    try {
        currentInventoryPlayerUuid = uuid;
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
    let sectionId = -1; // -1=Hotbar, -2=Storage, -3=Armor
    
    if (containerId === 'inv-storage') {
        slotCount = 27;
        sectionId = -2;
    } else if (containerId === 'inv-armor') {
        slotCount = 4;
        sectionId = -3;
    }
    
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
        slot.setAttribute('data-section', sectionId);
        slot.setAttribute('data-slot', i);
        
        // Drag and Drop
        slot.ondragover = handleDragOver;
        slot.ondragleave = handleDragLeave;
        slot.ondrop = handleDrop;
        
        const item = itemMap[i];
        if (item && item.id) {
            slot.setAttribute('draggable', 'true');
            slot.ondragstart = handleDragStart;
            
            // Rarity
            if (item.rarityColor) {
                slot.style.setProperty('--rarity-color', item.rarityColor);
                slot.style.setProperty('--rarity-color-opaque', item.rarityColor + '44');
                slot.setAttribute('data-rarity-color', item.rarityColor);
            }
            
            const icon = document.createElement('img');
            icon.src = `/api/item/${encodeURIComponent(item.id)}/icon`;
            icon.alt = item.id;
            icon.onerror = function() { this.style.display = 'none'; };
            slot.appendChild(icon);
            
            slot.onmouseenter = (e) => {
                const tooltip = document.getElementById('item-tooltip');
                let rarityHtml = '';
                if (item.rarity) {
                    rarityHtml = `<div style="color: ${item.rarityColor || '#fff'}; font-size: 0.75rem; font-weight: 700; text-transform: uppercase; margin-bottom: 4px;">${item.rarity}</div>`;
                }
                
                document.getElementById('tooltip-title').innerHTML = rarityHtml + item.id.split(':').pop().replace(/_/g, ' ');
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

// Drag and Drop Handlers
function handleDragStart(e) {
    const slot = e.currentTarget;
    const section = slot.getAttribute('data-section');
    const slotId = slot.getAttribute('data-slot');
    
    e.dataTransfer.setData('text/plain', JSON.stringify({
        section: parseInt(section),
        slot: parseInt(slotId)
    }));
    
    slot.classList.add('dragging');
    e.dataTransfer.effectAllowed = 'move';
}

function handleDragOver(e) {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    e.currentTarget.classList.add('drag-over');
}

function handleDragLeave(e) {
    e.currentTarget.classList.remove('drag-over');
}

async function handleDrop(e) {
    e.preventDefault();
    const targetSlot = e.currentTarget;
    targetSlot.classList.remove('drag-over');
    
    try {
        const data = JSON.parse(e.dataTransfer.getData('text/plain'));
        const fromSection = data.section;
        const fromSlot = data.slot;
        const toSection = parseInt(targetSlot.getAttribute('data-section'));
        const toSlot = parseInt(targetSlot.getAttribute('data-slot'));
        
        // Don't move if same slot
        if (fromSection === toSection && fromSlot === toSlot) return;
        
        await executeMove(fromSection, fromSlot, toSection, toSlot);
    } catch (err) {
        console.error('Drop error:', err);
    }
    
    
    document.querySelectorAll('.inv-slot.dragging').forEach(s => s.classList.remove('dragging'));
}

async function executeMove(fromSection, fromSlot, toSection, toSlot, quantity = -1) {
    if (!currentInventoryPlayerUuid) return;
    
    try {
        const response = await fetch('/api/inventory/move', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken
            },
            body: JSON.stringify({
                playerUuid: currentInventoryPlayerUuid,
                fromSection,
                fromSlot,
                toSection,
                toSlot,
                quantity
            })
        });
        
        const data = await response.json();
        if (data.status === 'success') {
            
            viewInv(currentInventoryPlayerUuid);
        } else {
            showNotification(data.error || 'Failed to move item', 'error');
        }
    } catch (err) {
        showNotification('Failed to move item', 'error');
    }
}

function closeInventory() {
    const modal = document.getElementById('inventory-modal');
    if (modal) {
        modal.classList.remove('active');
    }
    document.getElementById('item-tooltip').classList.remove('visible');
    currentInventoryPlayerUuid = null;
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
        
        
        mods.sort((a, b) => a.name.localeCompare(b.name));
        
        
    const initialPath = window.location.pathname;
    const initialTab = routes[initialPath] || 'server';
    switchTab(initialTab, false); 
        
        document.getElementById('mod-count').textContent = mods.length;
        const modCountBadgeServer = document.getElementById('mod-count-badge-server');
        if (modCountBadgeServer) {
            modCountBadgeServer.textContent = mods.length;
        }
        
        
        const listServer = document.getElementById('plugin-list-server');
        if (listServer) {
            listServer.innerHTML = '';
            mods.forEach(mod => {
                const div = document.createElement('div');
                div.className = 'plugin-item';
                
                
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
        
        
        const staminaText = p.stamina !== undefined ? `${Math.round(p.stamina)}/${Math.round(p.maxStamina || 100)}` : 'N/A';
        tr.querySelector('.p-stamina-text').textContent = staminaText;
        tr.querySelector('.stamina-bar').style.width = staminaPct + '%';
        
        
        const manaText = p.mana !== undefined ? `${Math.round(p.mana)}/${Math.round(p.maxMana || 100)}` : 'N/A';
        tr.querySelector('.p-mana-text').textContent = manaText;
        tr.querySelector('.mana-bar').style.width = manaPct + '%';
        
        
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


let currentActionPlayer = { uuid: null, name: null };


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
    
    const otherPlayers = allPlayers.filter(p => p.uuid !== uuid);
    if (otherPlayers.length === 0) {
        showNotification('No other players online to teleport to', 'error');
        return;
    }
    
    
    const modal = document.getElementById('teleport-modal');
    const playerList = document.getElementById('teleport-player-list');
    document.getElementById('teleport-player-name').textContent = `Teleport ${name}`;
    
    
    playerList.innerHTML = '';
    
    
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


let searchTimeout;
const originalSearchListener = document.getElementById('search').oninput;
document.getElementById('search').addEventListener('input', (e) => {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => renderPlayers(), 300);
});


document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        closeInventory();
    }
});


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


const style = document.createElement('style');
style.textContent = '@keyframes spin { to { transform: rotate(360deg); } }';
document.head.appendChild(style);





async function setGamemode(uuid, gamemode) {
    if (!dashboardToken) return;
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
            showNotification(`Gamemode changed to ${gamemode} for ${currentActionPlayer.name}`, 'success');
            fetchStats(); 
        } else {
            showNotification(data.error || 'Failed to change gamemode', 'error');
        }
    } catch (error) {
        showNotification('Error changing gamemode: ' + error.message, 'error');
    }
}


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
        
        
        const grid = document.createElement('div');
        grid.style.cssText = 'display: grid; grid-template-columns: repeat(auto-fill, minmax(90px, 1fr)); gap: 0.75rem; margin-bottom: 2.5rem;';
        
        players.forEach(player => {
            const div = document.createElement('div');
            div.className = 'warp-player-icon';
            
            
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


async function giveItem(uuid, name) {
    if (!dashboardToken) return;
    
    
    const modalInput = document.getElementById('giveitem-search');
    const isModalOpen = document.getElementById('giveitem-modal').classList.contains('active');
    
    let itemId, quantity;
    
    if (isModalOpen) {
        
        return;
    } else {
        itemId = await customPrompt('Enter item ID (e.g., hytale:stone):', '', 'Give Item');
        if (!itemId) return;
        
        const quantityStr = await customPrompt('Enter quantity (1-999):', '1', 'Give Item');
        if (!quantityStr) return;
        
        quantity = parseInt(quantityStr);
    }
    
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

function renderWorlds(worlds) {
    const container = document.getElementById('world-list-container');
    if (!container) return;
    container.innerHTML = '';
    
    worlds.forEach(world => {
        const div = document.createElement('div');
        div.className = `world-item ${world.loaded ? 'loaded' : 'unloaded'}`;
        div.innerHTML = `
            <div class="world-info">
                <div class="world-name">${world.name}</div>
                <div class="world-status">${world.loaded ? 'Loaded' : 'Unloaded'} - ${world.players || 0} Players</div>
                <div class="world-meta">${world.loaded ? (world.ticking ? 'Ticking' : 'Paused') : 'Inactive'}</div>
            </div>
            <div class="world-actions" style="display: flex; gap: 1rem;">
                ${world.loaded ? `
                    <button class="btn btn-secondary" onclick="toggleWorldState('${world.name}', ${!world.ticking})">
                        <span class="material-symbols-outlined">${world.ticking ? 'pause' : 'play_arrow'}</span>
                        ${world.ticking ? 'Pause' : 'Resume'}
                    </button>
                    <button class="btn btn-danger" onclick="unloadWorld('${world.name}')">Unload</button>
                ` : `
                    <button class="btn btn-primary" onclick="loadWorld('${world.name}')">Load</button>
                `}
            </div>
        `;
        container.appendChild(div);
    });
}

async function fetchWorlds() {
    if (!dashboardToken) return;
    try {
        const res = await fetch('/api/worlds/list', { headers: { 'X-Admin-Token': dashboardToken } });
        const worlds = await res.json();
        renderWorlds(worlds);
    } catch (e) { console.error('Failed to fetch worlds', e); }
}

async function loadWorld(name) {
    try {
        await fetch('/api/worlds/load', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Admin-Token': dashboardToken },
            body: JSON.stringify({ name })
        });
        showNotification(`Loading world: ${name}`, 'success');
        fetchWorlds();
    } catch (e) { showNotification('Failed to load world', 'error'); }
}

async function unloadWorld(name) {
    if (!await customConfirm(`Unload world "${name}"?`, 'Confirm Unload', true)) return;
    try {
        await fetch('/api/worlds/unload', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Admin-Token': dashboardToken },
            body: JSON.stringify({ name })
        });
        showNotification(`Unloading world: ${name}`, 'success');
        fetchWorlds();
    } catch (e) { showNotification('Failed to unload world', 'error'); }
}

async function toggleWorldState(name, ticking) {
    try {
        await fetch('/api/worlds/state', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Admin-Token': dashboardToken },
            body: JSON.stringify({ name, ticking })
        });
        fetchWorlds();
    } catch (e) { showNotification('Failed to toggle world state', 'error'); }
}

// Update startSync to fetch new data
function startSync() {
    setInterval(fetchStats, 2000);
    setInterval(fetchAdvancedMetrics, 5000); // Aligned with 5s backend cache
    setInterval(() => {
        if (currentConsoleMode === 'chat') fetchChat();
        else fetchConsole();
    }, 2000);
    setInterval(fetchMutes, 5000);
    setInterval(fetchWarps, 10000);
    setInterval(fetchWorlds, 10000); // Less frequent
    setInterval(fetchWorldInfo, 5000); // 5s is plenty for world status
    
    fetchStats();
    fetchAdvancedMetrics();
    fetchMods();
    fetchBannedPlayers();
    fetchChat();
    fetchMutes();
    fetchWarps();
    fetchBackups();
    fetchVersion();
    fetchLogs();
    fetchConfig();
    fetchWorlds();
    fetchWorldInfo();
    fetchGameRules();
}

// Metrics Implementation
let metricsChart = null;
const MAX_METRICS_POINTS = 30;
let metricsData = {
    labels: [],
    cpu: [],
    memory: [],
    tps: []
};

function initMetricsChart() {
    const ctx = document.getElementById('metricsChart');
    if (!ctx) return;

    metricsChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: metricsData.labels,
            datasets: [
                {
                    label: 'CPU (%)',
                    data: metricsData.cpu,
                    borderColor: '#efa3e3',
                    backgroundColor: 'rgba(239, 163, 227, 0.1)',
                    tension: 0.4,
                    fill: true
                },
                {
                    label: 'Memory (GB)',
                    data: metricsData.memory,
                    borderColor: '#a3cf93',
                    backgroundColor: 'rgba(163, 207, 147, 0.1)',
                    tension: 0.4,
                    fill: true
                },
                {
                    label: 'TPS',
                    data: metricsData.tps,
                    borderColor: '#f4d06f',
                    backgroundColor: 'rgba(244, 208, 111, 0.1)',
                    tension: 0.4,
                    fill: true,
                    yAxisID: 'y-tps'
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: {
                    beginAtZero: true,
                    max: 100,
                    grid: { color: 'rgba(255, 255, 255, 0.05)' },
                    ticks: { color: 'rgba(255, 255, 255, 0.5)' }
                },
                'y-tps': {
                    position: 'right',
                    beginAtZero: true,
                    max: 20,
                    grid: { display: false },
                    ticks: { color: 'rgba(244, 208, 111, 0.5)' }
                },
                x: {
                    grid: { display: false },
                    ticks: { display: false }
                }
            },
            plugins: {
                legend: {
                    labels: { color: '#fff', boxWidth: 12, usePointStyle: true }
                }
            }
        }
    });
}

async function fetchAdvancedMetrics() {
    if (!dashboardToken) return;
    try {
        const res = await fetch('/api/metrics', {
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const data = await res.json();
        
        // Update Chart
        const now = new Date().toLocaleTimeString();
        metricsData.labels.push(now);
        metricsData.cpu.push(data.processCpuLoad * 100);
        metricsData.memory.push(data.heapUsed / (1024 * 1024 * 1024));
        metricsData.tps.push(data.tps);
        
        if (metricsData.labels.length > MAX_METRICS_POINTS) {
            metricsData.labels.shift();
            metricsData.cpu.shift();
            metricsData.memory.shift();
            metricsData.tps.shift();
        }
        
        if (!metricsChart) {
            initMetricsChart();
        } else {
            metricsChart.update('none');
        }
        
        // Update counters
        const elCpu = document.getElementById('metric-cpu');
        const elMem = document.getElementById('metric-mem');
        const elThreads = document.getElementById('metric-threads');
        const elGC = document.getElementById('metric-gc');
        
        if (elCpu) elCpu.textContent = Math.round(data.processCpuLoad * 100) + '%';
        if (elMem) elMem.textContent = (data.heapUsed / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
        if (elThreads) elThreads.textContent = data.threadCount;
        if (elGC) elGC.textContent = data.gcCollections;
        
        // Update World Performance (TPS)
        const worldContainer = document.getElementById('world-metrics-container');
        if (worldContainer) {
            worldContainer.innerHTML = '';
            if (data.worlds && Object.keys(data.worlds).length > 0) {
                Object.entries(data.worlds).forEach(([name, metrics]) => {
                    const div = document.createElement('div');
                    div.className = 'metric-card';
                    div.style.padding = '1rem';
                    div.innerHTML = `
                        <div style="font-weight: 600; font-size: 0.875rem; margin-bottom: 0.5rem; color: var(--text-secondary);">${name}</div>
                        <div style="display: flex; justify-content: space-between; align-items: baseline;">
                            <span style="font-size: 1.5rem; font-weight: 700; font-family: 'Lexend'; color: var(--accent-primary);">${metrics.tps}</span>
                            <span style="font-size: 0.75rem; color: var(--text-muted);">TPS</span>
                        </div>
                        <div class="stat-trend ${metrics.tps >= 29 ? 'positive' : 'negative'}">
                            <span class="material-symbols-outlined" style="font-size: 1rem;">
                                ${metrics.tps >= 29 ? 'check_circle' : 'warning'}
                            </span>
                            <span>${metrics.tps >= 29 ? 'Stable' : 'Degraded'}</span>
                        </div>
                    `;
                    worldContainer.appendChild(div);
                });
            } else {
                worldContainer.innerHTML = '<div style="padding: 2rem; text-align: center; color: var(--text-muted);">No worlds loaded</div>';
            }
        }
        
    } catch (e) { console.error('Metrics fetch failed', e); }
}

// Logs Implementation
let currentLogContent = "";

async function fetchLogs() {
    if (!dashboardToken) return;
    try {
        const res = await fetch('/api/logs/list', { headers: { 'X-Admin-Token': dashboardToken } });
        const logs = await res.json();
        renderLogList(logs);
    } catch (e) { console.error('Failed to fetch logs', e); }
}

function renderLogList(logs) {
    const container = document.getElementById('log-file-list');
    if (!container) return;
    container.innerHTML = '';
    
    logs.forEach(log => {
        const div = document.createElement('div');
        div.className = 'plugin-item log-item';
        div.style.cursor = 'pointer';
        div.innerHTML = `
            <div class="plugin-info" onclick="viewLog('${log.name}')">
                <span class="material-symbols-outlined">description</span>
                <div>
                    <div style="font-weight: 600;">${log.name}</div>
                    <div style="font-size: 0.75rem; color: var(--text-secondary);">${formatBytes(log.size)} - ${new Date(log.modified).toLocaleString()}</div>
                </div>
            </div>
            <div class="log-actions">
                <button class="btn-icon btn-danger" onclick="deleteLogFile('${log.name}')" title="Delete Log">
                    <span class="material-symbols-outlined">delete</span>
                </button>
            </div>
        `;
        container.appendChild(div);
    });
}

async function viewLog(name) {
    try {
        const res = await fetch(`/api/logs/view/${encodeURIComponent(name)}`, {
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const data = await res.json();
        currentLogContent = data.content || "";
        renderFilteredLogs();
        
        const nameDisplay = document.getElementById('current-log-name');
        if (nameDisplay) nameDisplay.textContent = name;
    } catch (e) { 
        console.error('Failed to load log', e);
        showNotification('Failed to load log', 'error'); 
    }
}

function filterLogs() {
    renderFilteredLogs();
}

function renderFilteredLogs() {
    const viewer = document.getElementById('log-viewer-content');
    if (!viewer) return;

    const searchTerm = document.getElementById('log-search')?.value.toLowerCase() || "";
    const levelFilter = document.getElementById('log-level-filter')?.value || "";

    if (!currentLogContent) {
        viewer.textContent = "Log file is empty or not loaded.";
        return;
    }

    const lines = currentLogContent.split('\n');
    const filteredLines = lines.filter(line => {
        // Match level like "[... INFO]" or "INFO:"
        const matchesLevel = levelFilter === "" || 
                           line.includes(` ${levelFilter}]`) || 
                           line.includes(`[${levelFilter}]`) ||
                           line.includes(`${levelFilter}:`);
        const matchesSearch = searchTerm === "" || line.toLowerCase().includes(searchTerm);
        return matchesLevel && matchesSearch;
    });

    if (filteredLines.length === 0) {
        viewer.textContent = "No log entries match the current filters.";
    } else {
        viewer.textContent = filteredLines.join('\n');
    }
    
    // Auto-scroll to bottom
    viewer.scrollTop = viewer.scrollHeight;
}

async function deleteLogFile(name) {
    if (!await customConfirm(`Are you sure you want to delete log file "<b>${name}</b>"?<br>This action cannot be undone.`, 'CONFIRM DELETE', true, true)) return;
    
    try {
        const res = await fetch(`/api/logs/delete/${encodeURIComponent(name)}`, {
            method: 'POST',
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const data = await res.json();
        if (data.success) {
            showNotification(`Log file ${name} deleted`, 'success');
            fetchLogs();
            if (document.getElementById('current-log-name').textContent === name) {
                document.getElementById('current-log-name').textContent = 'Select a log file';
                document.getElementById('log-viewer-content').textContent = 'Pruned logs... waiting for new events.';
            }
        } else {
            showNotification(data.error || 'Failed to delete log', 'error');
        }
    } catch (e) { showNotification('Failed to delete log', 'error'); }
}

async function downloadLog() {
    const name = document.getElementById('current-log-name').textContent;
    if (!name || name === 'Select a log file') return;
    window.open(`/api/logs/download/${encodeURIComponent(name)}?token=${dashboardToken}`, '_blank');
}

// Config Implementation
async function fetchConfig() {
    if (!dashboardToken) return;
    try {
        const res = await fetch('/api/config/get', { headers: { 'X-Admin-Token': dashboardToken } });
        const config = await res.json();
        
        if (document.getElementById('cfg-serverName')) document.getElementById('cfg-serverName').value = config.serverName || '';
        if (document.getElementById('cfg-motd')) document.getElementById('cfg-motd').value = config.motd || '';
        if (document.getElementById('cfg-maxPlayers')) document.getElementById('cfg-maxPlayers').value = config.maxPlayers || 0;
        if (document.getElementById('cfg-viewRadius')) document.getElementById('cfg-viewRadius').value = config.maxViewRadius || 32;

        if (document.getElementById('cfg-reverseProxy')) document.getElementById('cfg-reverseProxy').checked = config.reverseProxy || false;
        if (document.getElementById('cfg-loggingEnabled')) document.getElementById('cfg-loggingEnabled').checked = config.loggingEnabled || false;
        if (document.getElementById('cfg-logLevel')) document.getElementById('cfg-logLevel').value = config.logLevel || 'INFO';
        if (document.getElementById('cfg-loginRateLimit')) document.getElementById('cfg-loginRateLimit').value = config.loginRateLimit || 5;
        if (document.getElementById('cfg-ipAllowlist')) document.getElementById('cfg-ipAllowlist').value = (config.ipAllowlist || []).join(', ');
        if (document.getElementById('cfg-letsEncrypt')) document.getElementById('cfg-letsEncrypt').checked = config.letsEncrypt || false;
        if (document.getElementById('cfg-letsEncryptEmail')) document.getElementById('cfg-letsEncryptEmail').value = config.letsEncryptEmail || '';
        
        // Defaults
        if (config.defaults) {
            if (document.getElementById('cfg-defaultWorld')) document.getElementById('cfg-defaultWorld').value = config.defaults.defaultWorld || '';
            if (document.getElementById('cfg-defaultGameMode')) {
                // Server returns 'Adventure' or 'Creative' (Title Case)
                // Select options are also Title Case now. 
                // We ensure we match exactly, or fallback
                let gm = config.defaults.defaultGameMode || 'Adventure';
                // Ensure title case just in case
                gm = gm.charAt(0).toUpperCase() + gm.slice(1).toLowerCase();
                document.getElementById('cfg-defaultGameMode').value = gm;
            }
        }

        // Timeouts
        if (config.connectionTimeouts) {
            if (document.getElementById('cfg-playTimeout')) document.getElementById('cfg-playTimeout').value = config.connectionTimeouts.playTimeout || 0;
        }
        
        // Discord Integration Config
        if (config.webdash) {
            const wdConfig = config.webdash;
            
            if (document.getElementById('cfg-discordEnabled')) document.getElementById('cfg-discordEnabled').checked = wdConfig.discordEnabled || false;
            
            // Password fields - show indicator if set
            const discordTokenStatus = document.getElementById('cfg-discordToken-status');
            if (discordTokenStatus) {
                if (wdConfig.hasDiscordToken) {
                    discordTokenStatus.style.display = 'block';
                } else {
                    discordTokenStatus.style.display = 'none';
                }
            }
            if (document.getElementById('cfg-discordToken')) document.getElementById('cfg-discordToken').value = ''; // Always clear real token
            
            if (document.getElementById('cfg-discordGuildId')) document.getElementById('cfg-discordGuildId').value = wdConfig.discordGuildId || '';
            if (document.getElementById('cfg-discordChannelLogs')) document.getElementById('cfg-discordChannelLogs').value = wdConfig.discordChannelLogs || '';
            if (document.getElementById('cfg-discordChannelAlerts')) document.getElementById('cfg-discordChannelAlerts').value = wdConfig.discordChannelAlerts || '';
            if (document.getElementById('cfg-discordChannelJoins')) document.getElementById('cfg-discordChannelJoins').value = wdConfig.discordChannelJoins || '';
            if (document.getElementById('cfg-discordCommandPrefix')) document.getElementById('cfg-discordCommandPrefix').value = wdConfig.discordCommandPrefix || '!cmd ';
            
            // HTTPS Config
            if (document.getElementById('cfg-useHttps')) document.getElementById('cfg-useHttps').checked = wdConfig.useHttps || false;
            if (document.getElementById('cfg-domain')) document.getElementById('cfg-domain').value = wdConfig.domain || '';
            if (document.getElementById('cfg-keystorePath')) document.getElementById('cfg-keystorePath').value = wdConfig.keystorePath || 'keystore.jks';
            
            const keystorePasswordStatus = document.getElementById('cfg-keystorePassword-status');
            if (keystorePasswordStatus) {
                if (wdConfig.hasKeystorePassword) {
                    keystorePasswordStatus.style.display = 'block';
                } else {
                    keystorePasswordStatus.style.display = 'none';
                }
            }
            if (document.getElementById('cfg-keystorePassword')) document.getElementById('cfg-keystorePassword').value = '';
        }

        // Mods
        const modList = document.getElementById('cfg-mod-list');
        if (modList && config.mods) {
            modList.innerHTML = '';
            // Sort by Mod ID
            const sortedMods = Object.entries(config.mods).sort((a, b) => a[0].localeCompare(b[0]));
            
            for (const [modId, modData] of sortedMods) {
               const isProtected = modId === 'uk.co.grimtech:AdminWebDash';
               const div = document.createElement('div');
               div.className = 'mod-item-toggle';
               div.setAttribute('data-mod-id', modId.toLowerCase());
               
               let toggleHtml = `
                   <label class="switch">
                     <input type="checkbox" ${modData.enabled ? 'checked' : ''} data-mod-id="${modId}" onchange="showRestartWarning()">
                     <span class="slider round"></span>
                   </label>
               `;

               if (isProtected) {
                   toggleHtml = `
                       <div style="display: flex; align-items: center; gap: 0.5rem;">
                           <span class="material-symbols-outlined" style="font-size: 1.25rem; color: var(--text-secondary);" title="Cannot be disabled">lock</span>
                           <label class="switch" style="opacity: 0.5; pointer-events: none;">
                             <input type="checkbox" checked disabled>
                             <span class="slider round"></span>
                           </label>
                       </div>
                   `;
               }

               div.innerHTML = `
                   <span title="${modId}">${modId}</span>
                   ${toggleHtml}
               `;
               modList.appendChild(div);
            }
        }

        // Render log levels
        const llContainer = document.getElementById('log-level-list');
        if (llContainer) {
            llContainer.innerHTML = '';
            for (const [pkg, level] of Object.entries(config.logLevels)) {
                const div = document.createElement('div');
                div.className = 'plugin-item';
                div.innerHTML = `
                    <div style="flex:1; font-family: monospace; font-size: 0.875rem;">${pkg}</div>
                    <div style="font-weight: 600; color: var(--hytale-gold);">${level}</div>
                `;
                llContainer.appendChild(div);
            }
        }
    } catch (e) { console.error('Failed to fetch config', e); }
}

function filterMods() {
    const input = document.getElementById('cfg-mod-search');
    const filter = input.value.toLowerCase();
    const nodes = document.getElementsByClassName('mod-item-toggle');

    for (let i = 0; i < nodes.length; i++) {
        const modId = nodes[i].getAttribute('data-mod-id');
        if (modId.includes(filter)) {
            nodes[i].style.display = "flex";
        } else {
            nodes[i].style.display = "none";
        }
    }
}

function showRestartWarning() {
    const warning = document.getElementById('cfg-restart-warning');
    if (warning) warning.style.display = 'flex';
}

async function updateConfig() {
    const payload = {
        serverName: document.getElementById('cfg-serverName').value,
        motd: document.getElementById('cfg-motd').value,
        maxPlayers: parseInt(document.getElementById('cfg-maxPlayers').value),
        maxViewRadius: parseInt(document.getElementById('cfg-viewRadius').value),
        defaults: {
            defaultWorld: document.getElementById('cfg-defaultWorld').value,
            defaultGameMode: document.getElementById('cfg-defaultGameMode').value
        },
        connectionTimeouts: {
             playTimeout: parseInt(document.getElementById('cfg-playTimeout').value)
        },
        mods: {}
    };

    // Gather mods
    const modInputs = document.querySelectorAll('#cfg-mod-list input[type="checkbox"]');
    modInputs.forEach(input => {
        payload.mods[input.getAttribute('data-mod-id')] = {
            enabled: input.checked
        };
    });

    payload.reverseProxy = document.getElementById('cfg-reverseProxy')?.checked || false;
    payload.loggingEnabled = document.getElementById('cfg-loggingEnabled')?.checked || false;
    payload.logLevel = document.getElementById('cfg-logLevel')?.value || 'INFO';
    payload.loginRateLimit = parseInt(document.getElementById('cfg-loginRateLimit')?.value || '5');
    
    // IP Allowlist
    const ips = document.getElementById('cfg-ipAllowlist')?.value || '';
    payload.ipAllowlist = ips.split(',').map(s => s.trim()).filter(s => s.length > 0);

    // Let's encrypt
    payload.letsEncrypt = document.getElementById('cfg-letsEncrypt')?.checked || false;
    payload.letsEncryptEmail = document.getElementById('cfg-letsEncryptEmail')?.value.trim() || '';
    
    
    payload.discordEnabled = document.getElementById('cfg-discordEnabled')?.checked || false;
    
    const discordTokenElem = document.getElementById('cfg-discordToken');
    if (discordTokenElem && discordTokenElem.value.trim() !== '') {
        payload.discordToken = discordTokenElem.value.trim();
    }
    
    payload.discordGuildId = document.getElementById('cfg-discordGuildId')?.value.trim() || '';
    payload.discordChannelLogs = document.getElementById('cfg-discordChannelLogs')?.value.trim() || '';
    payload.discordChannelAlerts = document.getElementById('cfg-discordChannelAlerts')?.value.trim() || '';
    payload.discordChannelJoins = document.getElementById('cfg-discordChannelJoins')?.value.trim() || '';
    payload.discordCommandPrefix = document.getElementById('cfg-discordCommandPrefix')?.value || '!cmd ';

    
    payload.useHttps = document.getElementById('cfg-useHttps')?.checked || false;
    payload.domain = document.getElementById('cfg-domain')?.value.trim() || '';
    payload.keystorePath = document.getElementById('cfg-keystorePath')?.value.trim() || 'keystore.jks';
    
    const keystorePasswordElem = document.getElementById('cfg-keystorePassword');
    if (keystorePasswordElem && keystorePasswordElem.value.trim() !== '') {
        payload.keystorePassword = keystorePasswordElem.value.trim();
    }
    
    try {
        const res = await fetch('/api/config/set', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Admin-Token': dashboardToken },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (data.success || data.status === 'success') {
            let msg = 'Server configuration updated';
            if (data.requiresRestart) {
                msg += '. Some changes require a restart to take effect.';
            }
            showNotification(msg, 'success');
            
            setTimeout(() => { fetchConfig(); }, 500); 
        } else {
            showNotification(data.error || 'Failed to update config', 'error');
        }
    } catch (e) { showNotification('Update request failed', 'error'); }
}


async function testDiscordConnection() {
    const btn = document.getElementById('btn-test-discord');
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = '<span class="material-symbols-outlined pulse">network_check</span> Testing...';
    }
    
    const payload = {};
    const tokenElem = document.getElementById('cfg-discordToken');
    if (tokenElem && tokenElem.value.trim() !== '') {
        payload.discordToken = tokenElem.value.trim();
    }
    
    payload.discordChannelLogs = document.getElementById('cfg-discordChannelLogs')?.value.trim() || '';
    
    try {
        const res = await fetch('/api/discord/test', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Admin-Token': dashboardToken },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (data.status === 'success' || data.success) {
            showNotification('Discord test successful! Message sent to channel.', 'success');
        } else {
            showNotification(data.error || 'Discord connection failed', 'error');
        }
    } catch (e) { 
        showNotification('Discord test request failed', 'error'); 
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = '<span class="material-symbols-outlined">network_check</span> Test Connection';
        }
    }
}


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




let confirmResolve = null;
let promptResolve = null;


function customConfirm(message, title = 'Confirm Action', isDanger = false, allowHtml = false) {
    return new Promise((resolve) => {
        confirmResolve = resolve;
        document.getElementById('confirm-title').textContent = title;
        
        const msgEl = document.getElementById('confirm-message');
        if (allowHtml) {
            msgEl.innerHTML = message;
        } else {
            msgEl.textContent = message;
        }
        
        const confirmBtn = document.getElementById('confirm-btn');
        if (isDanger) {
            confirmBtn.className = 'btn btn-danger';
        } else {
            confirmBtn.className = 'btn btn-primary';
        }
        
        document.getElementById('confirm-modal').classList.add('active');
        
        
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


function customPrompt(message, defaultValue = '', title = 'Input Required') {
    return new Promise((resolve) => {
        promptResolve = resolve;
        document.getElementById('prompt-title').textContent = title;
        document.getElementById('prompt-message').textContent = message;
        document.getElementById('prompt-input').value = defaultValue;
        document.getElementById('prompt-modal').classList.add('active');
        
        
        setTimeout(() => {
            const input = document.getElementById('prompt-input');
            input.focus();
            input.select();
        }, 100);
        
        
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








function closeAlertModal() {
    document.getElementById('alert-modal').classList.remove('active');
    if (window.alertResolve) {
        window.alertResolve();
        window.alertResolve = null;
    }
}


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


document.getElementById('confirm-modal').addEventListener('click', (e) => {
    if (e.target.id === 'confirm-modal') closeConfirmModal(false);
});
document.getElementById('prompt-modal').addEventListener('click', (e) => {
    if (e.target.id === 'prompt-modal') closePromptModal(null);
});

async function viewBansFile() {
    try {
        const res = await fetch('/api/bans/raw', {
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const data = await res.json();
        
        document.getElementById('bans-file-path').textContent = data.path || 'Unknown';
        document.getElementById('bans-file-modified').textContent = data.modified || 'Unknown';
        document.getElementById('bans-file-content').textContent = data.content || '[]';
        
        document.getElementById('bans-file-modal').classList.add('active');
    } catch (e) {
        showNotification('Failed to read bans file', 'error');
    }
}

function closeBansFileModal() {
    document.getElementById('bans-file-modal').classList.remove('active');
}

function refreshBansFile() {
    viewBansFile();
}

function copyBansFile() {
    const content = document.getElementById('bans-file-content').textContent;
    navigator.clipboard.writeText(content).then(() => {
        showNotification('Copied to clipboard!', 'success');
    });
}



function openGamemodeModal(uuid, name) {
    const player = allPlayers.find(p => p.uuid === uuid);
    const currentGamemode = player ? player.gameMode : 'Adventure';
    
    document.getElementById('gamemode-player-name').textContent = `${name} - Change Gamemode`;
    document.getElementById('gamemode-current').textContent = `Current: ${currentGamemode}`;
    
    
    const creativeBtn = document.getElementById('gamemode-creative-btn');
    const adventureBtn = document.getElementById('gamemode-adventure-btn');
    
    creativeBtn.classList.remove('active-gamemode');
    adventureBtn.classList.remove('active-gamemode');
    
    if (currentGamemode.toLowerCase() === 'creative') {
        creativeBtn.classList.add('active-gamemode');
    } else {
        adventureBtn.classList.add('active-gamemode');
    }
    
    
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


let allItems = [];
let filteredItems = [];
let selectedItemForGive = null;

async function openGiveItemModal(uuid, name) {
    document.getElementById('giveitem-player-name').textContent = `Give Item to ${name}`;
    document.getElementById('giveitem-search').value = '';
    document.getElementById('giveitem-quantity').value = '1';
    selectedItemForGive = null;
    
    
    if (allItems.length === 0) {
        document.getElementById('giveitem-loading').style.display = 'flex';
        document.getElementById('giveitem-browser').style.display = 'none';
        await loadAllItems();
        document.getElementById('giveitem-loading').style.display = 'none';
        document.getElementById('giveitem-browser').style.display = 'block';
    }
    
    
    filteredItems = [...allItems];
    renderItemBrowser();
    updateItemCount();
    
    
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
                
                
                return nameLower.includes(query) || 
                       idLower.includes(query) || 
                       keywords.includes(query.replace(/\s+/g, ''));
            });
        }
        renderItemBrowser();
        updateItemCount();
    };
    
    
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
            
            container.querySelectorAll('.item-browser-card').forEach(card => {
                card.classList.remove('selected');
            });
            itemCard.classList.add('selected');
            
            
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



async function clearCurseForgeCache() {
    try {
        const response = await fetch('/api/curseforge/clear-cache', {
            method: 'POST',
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const data = await response.json();
        if (data.status === 'success') {
            showNotification('CurseForge cache cleared! Refresh the page to see updated mod info.', 'success');
            
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


window.clearCurseForgeCache = clearCurseForgeCache;


let backupInterval = 0;
let pendingBackupFile = null;

async function fetchBackups() {
    if (!dashboardToken) return;

    
    const tbody = document.getElementById('backup-list-body');
    if (tbody && tbody.children.length === 0) {
        if(document.getElementById('backup-loading')) document.getElementById('backup-loading').style.display = 'flex';
        if(document.getElementById('backup-empty')) document.getElementById('backup-empty').style.display = 'none';
    }

    try {
        const res = await fetch('/api/backups', {
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const backups = await res.json();
        renderBackups(backups);
        
        
        if(document.getElementById('backup-count-badge')) document.getElementById('backup-count-badge').textContent = backups.length;
        
        
    } catch (e) {
        console.error('Failed to fetch backups', e);
        showNotification('Failed to fetch backups', 'error');
    } finally {
        if(document.getElementById('backup-loading')) document.getElementById('backup-loading').style.display = 'none';
        fetchBackupSchedule();
    }
}

async function fetchBackupSchedule() {
    if (!dashboardToken) return;
    try {
        const schedRes = await fetch('/api/backup/schedule', {
             headers: { 'X-Admin-Token': dashboardToken }
        });
        const sched = await schedRes.json();
        if (sched.intervalMinutes !== undefined) {
            backupInterval = sched.intervalMinutes;
            const input = document.getElementById('backup-interval-input');
            if (input) input.value = backupInterval > 0 ? backupInterval : '';
        }
    } catch (e) {
        console.error('Failed to fetch backup schedule', e);
    }
}

function renderBackups(backups) {
    const tbody = document.getElementById('backup-list-body');
    if (!tbody) return;
    
    tbody.innerHTML = '';
    
    if (backups.length === 0) {
        if(document.getElementById('backup-empty')) document.getElementById('backup-empty').style.display = 'block';
        return;
    }
    
    if(document.getElementById('backup-empty')) document.getElementById('backup-empty').style.display = 'none';
    
    if(document.getElementById('backup-empty')) document.getElementById('backup-empty').style.display = 'none';
    
    
    
    
    
    
    
    
    

    backups.forEach(backup => {
        const tr = document.createElement('tr');
        tr.style.borderBottom = '1px solid var(--border-color)';
        
        const date = new Date(backup.timestamp);
        const dateStr = date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
        
        tr.innerHTML = `
            <td style="font-family: monospace;">${backup.name}</td>
            <td>${formatBytes(backup.size)}</td>
            <td style="color: var(--text-secondary); font-size: 0.875rem;">${dateStr}</td>
            <td style="text-align: right;">
                <button class="btn btn-secondary" onclick="restoreBackup('${backup.name}')" title="Restore this backup" style="margin-right: 0.25rem;">
                    <span class="material-symbols-outlined" style="font-size: 1rem;">history</span>
                </button>
                <button class="btn btn-danger" onclick="deleteBackup('${backup.name}')" title="Delete backup">
                    <span class="material-symbols-outlined" style="font-size: 1rem;">delete</span>
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

let backupPollInterval = null;

async function pollBackupStatus() {
    try {
        const res = await fetch('/api/backup/status', {
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const status = await res.json();
        
        const container = document.getElementById('backup-progress-container');
        const bar = document.getElementById('backup-progress-bar');
        const text = document.getElementById('backup-status-text');
        const percentage = document.getElementById('backup-percentage');
        
        const createBtn = document.querySelector('button[onclick="createBackup()"]');
        const restoreBtns = document.querySelectorAll('button[title="Restore this backup"]');
        const deleteBtns = document.querySelectorAll('button[title="Delete backup"]');
        
        if (status.active) {
            if (container) container.style.display = 'block';
            if (text) text.textContent = status.message || 'Backing up...';
            
            const percent = Math.round(status.progress * 100);
            if (bar) bar.style.width = percent + '%';
            if (percentage) percentage.textContent = percent + '%';
            
            
            if (createBtn) createBtn.disabled = true;
            restoreBtns.forEach(b => b.disabled = true);
            deleteBtns.forEach(b => b.disabled = true);
            
        } else {
            
            if (container && container.style.display !== 'none') {
                 
                 if (status.message === 'Completed') {
                     showNotification('Backup created successfully', 'success');
                     fetchBackups();
                 } else if (status.message && status.message.startsWith('Failed')) {
                     showNotification(status.message, 'error');
                 }
                 
                 
                 setTimeout(() => {
                     container.style.display = 'none';
                     if (bar) bar.style.width = '0%';
                 }, 2000);
                 
                 
                 if (backupPollInterval) {
                     clearInterval(backupPollInterval);
                     backupPollInterval = null;
                 }
                 
                 
                 if (createBtn) createBtn.disabled = false;
                 restoreBtns.forEach(b => b.disabled = false);
                 deleteBtns.forEach(b => b.disabled = false);
            }
        }
        
    } catch (e) {
        console.error('Failed to poll status', e);
    }
}

async function createBackup() {
    if (!dashboardToken) return;
    
    
    
    try {
        const res = await fetch('/api/backup/create', {
            method: 'POST',
            headers: { 'X-Admin-Token': dashboardToken }
        });
        const data = await res.json();
        
        if (data.status === 'success') {
            
            if (backupPollInterval) clearInterval(backupPollInterval);
            backupPollInterval = setInterval(pollBackupStatus, 1000);
            pollBackupStatus(); 
            
        } else {
            showNotification(data.error || 'Backup failed', 'error');
        }
    } catch (e) {
        console.error('Backup creation error', e);
        showNotification('Failed to start backup', 'error');
    }
}

async function restoreBackup(name) {
    const msg = `Are you sure you want to restore "<b>${name}</b>"?<br><br>
    <strong style="color: #ff4444;">WARNING: This will:</strong>
    <ul style="text-align: left; margin: 10px 0; padding-left: 20px;">
        <li>Kick all players</li>
        <li>Unload all worlds</li>
        <li>Overwrite current world data</li>
        <li>Reload worlds (may require restart)</li>
    </ul>
    Current progress since this backup will be <strong>LOST!</strong>`;

    if (!await customConfirm(msg, 'CONFIRM RESTORE', true, true)) return;
    
    showNotification('Initiating restore...', 'success');
    
    try {
        const res = await fetch('/api/backup/restore', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken 
            },
            body: JSON.stringify({ name: name })
        });
        const data = await res.json();
        
        if (data.status === 'restore_started') {
            showNotification('Restore started. Check server console/logs.', 'success');
        } else {
            showNotification(data.error || 'Restore failed', 'error');
        }
    } catch (e) {
        showNotification('Restore request failed', 'error');
    }
}

async function deleteBackup(name) {
    if (!await customConfirm(`Delete backup "${name}"? This cannot be undone.`, 'Delete Backup', true)) return;
    
    try {
        const res = await fetch('/api/backup/delete', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken 
            },
            body: JSON.stringify({ name: name })
        });
        const data = await res.json();
        
        if (data.status === 'success') {
            showNotification('Backup deleted', 'success');
            fetchBackups();
        } else {
            showNotification(data.error || 'Failed to delete backup', 'error');
        }
    } catch (e) {
        showNotification('Delete request failed', 'error');
    }
}

async function updateBackupSchedule() {
    const input = document.getElementById('backup-interval-input');
    const interval = parseInt(input.value);
    
    if (isNaN(interval) || interval < 0) {
        showNotification('Invalid interval', 'error');
        return;
    }
    
    try {
        const res = await fetch('/api/backup/schedule', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-Admin-Token': dashboardToken 
            },
            body: JSON.stringify({ intervalMinutes: interval })
        });
        const data = await res.json();
        
        if (data.status === 'success') {
            showNotification('Backup schedule updated', 'success');
            fetchBackupSchedule();
        } else {
            showNotification(data.error || 'Failed to update schedule', 'error');
        }
    } catch (e) {
        showNotification('Schedule update failed', 'error');
    }
}


function saveConfig() {
    updateConfig();
}

function clearLogViewer() {
    const viewer = document.getElementById('log-viewer-content');
    if (viewer) viewer.textContent = 'Select a log file to view...';
    const nameDisplay = document.getElementById('current-log-name');
    if (nameDisplay) nameDisplay.textContent = 'Select a log file';
}

async function addLogLevel() {
    const pkg = document.getElementById('new-log-pkg').value.trim();
    const level = document.getElementById('new-log-lvl').value;
    
    if (!pkg) {
        showNotification('Please enter a package name', 'error');
        return;
    }
    
    try {
        const res = await fetch('/api/config/loglevel', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Admin-Token': dashboardToken },
            body: JSON.stringify({ package: pkg, level: level })
        });
        const data = await res.json();
        if (data.success) {
            showNotification(`Log level set for ${pkg}`, 'success');
            fetchConfig();
            document.getElementById('new-log-pkg').value = '';
        } else {
            showNotification(data.error || 'Failed to set log level', 'error');
        }
    } catch (e) { showNotification('Request failed', 'error'); }
}


async function fetchWorldInfo() {
    if (!dashboardToken || document.hidden) return;
    
    const tabWorld = document.getElementById('tab-world');
    if (!tabWorld || !tabWorld.classList.contains('active')) return;
    
    try {
        const res = await fetch('/api/world/info', { headers: { 'X-Admin-Token': dashboardToken } });
        const data = await res.json();
        
        if (data.error) return;
        
        const setTxt = (id, txt) => {
            const el = document.getElementById(id);
            if (el) el.textContent = txt;
        };

        setTxt('world-name', data.name || 'Unknown World');
        setTxt('world-dim', `Dimension: ${data.dimension}`);
        setTxt('world-time', `Time: ${data.time}`);
        setTxt('world-weather', `Weather: ${data.weather}`);
        
    } catch (e) { console.error('Failed to fetch world info', e); }
}

async function fetchGameRules() {
    if (!dashboardToken) return;
    try {
        const res = await fetch('/api/world/gamerules', { headers: { 'X-Admin-Token': dashboardToken } });
        const rules = await res.json();
        
        if (rules.error) return;
        
        const setChecked = (id, val) => {
            const el = document.getElementById(id);
            if (el) el.checked = val;
        };
        
        setChecked('rule-isPvpEnabled', rules.isPvpEnabled);
        setChecked('rule-isFallDamageEnabled', rules.isFallDamageEnabled);
        setChecked('rule-isSpawningNPC', rules.isSpawningNPC);
        setChecked('rule-doDaylightCycle', !rules.isGameTimePaused); 
        setChecked('rule-isBlockBreakingAllowed', rules.isBlockBreakingAllowed);
        setChecked('rule-isBlockPlacementAllowed', rules.isBlockPlacementAllowed);
        
    } catch (e) { console.error('Failed to fetch gamerules', e); }
}

async function updateGameRule(rule, value) {
    try {
        const payload = {};
        payload[rule] = value;
        
        await fetch('/api/world/gamerules', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Admin-Token': dashboardToken },
            body: JSON.stringify(payload)
        });
        showNotification('Game rule updated', 'success');
    } catch (e) { 
        showNotification('Failed to update game rule', 'error');
        fetchGameRules(); 
    }
}

async function toggleDaylightCycle(enabled) {
    
    updateGameRule('isGameTimePaused', !enabled);
}
