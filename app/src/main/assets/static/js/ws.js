(function() {
const J = window.JecWeb;
J.wsReconnectAttempts = 0;

J.initWS = function() {
    J.wsConnect();
};

J.wsConnect = function() {
    if (J.ws && J.ws.readyState === WebSocket.OPEN) return;
    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    const wsUrl = `${protocol}//${location.host}/ws`;
    const ws = new WebSocket(wsUrl);
    J.ws = ws;

    ws.onopen = () => {
        console.log('WebSocket connected');
        J.wsReconnectAttempts = 0;
        ws.send(JSON.stringify({action: 'get_status'}));
        if (J.lastPhoneHash) {
            ws.send(JSON.stringify({action:'subscribe', hash:J.lastPhoneHash, ip:'127.0.0.1'}));
        }
        if (J.lastTVHash && J.settings.tv_ip) {
            ws.send(JSON.stringify({action:'subscribe', hash:J.lastTVHash, ip:J.settings.tv_ip}));
        }
    };

    ws.onmessage = (e) => {
        let msg;
        try { msg = JSON.parse(e.data); } catch(err) { return; }
        switch (msg.type) {
            case 'status':
                if (!document.getElementById('playerModal')?.classList.contains('active')) {
                    if (typeof J.updateStatus === 'function') J.updateStatus(msg.data);
                } else {
                    J.pendingStatus = msg.data;
                }
                break;
            case 'magnets':
                if (J.currentTab === 'magnets' && typeof J.renderMagnets === 'function')
                    J.renderMagnets(msg.data);
                // Обновляем нижнюю панель
                if (typeof J.updateTorrentPanel === 'function')
                    J.updateTorrentPanel(msg.data.local || []);
                break;
            case 'torrent_status':
                if (msg.hash === J.lastPhoneHash && typeof J.updatePlayerUI === 'function')
                    J.updatePlayerUI(msg.data);
                break;
            case 'superblitz_status':
                if (msg.hash === J.lastPhoneHash && typeof J.updateSuperblitzUI === 'function')
                    J.updateSuperblitzUI(msg.data);
                break;
        }
    };

    ws.onclose = () => {
        console.log('WebSocket disconnected');
        J.ws = null;
        setTimeout(() => J.wsConnect(), Math.min(30000, 1000 * Math.pow(2, J.wsReconnectAttempts || 0)));
        J.wsReconnectAttempts = (J.wsReconnectAttempts || 0) + 1;
    };

    ws.onerror = (err) => {
        console.error('WebSocket error', err);
        ws.close();
    };
};
})();
