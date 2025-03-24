let stompClient = null;
let activeTab = 'personal';

function connect() {
    const socket = new SockJS('/chat');
    stompClient = Stomp.over(socket);
    console.log("Attempting to connect to WebSocket");
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
        stompClient.subscribe('/user/' + currentUser + '/topic/chatrooms', function (message) {
            console.log("Received chatrooms message: " + message.body);
            renderChatList(JSON.parse(message.body));
        });
        stompClient.subscribe('/user/' + currentUser + '/topic/notification', function (message) {
            console.log("Received notification: " + message.body);
            alert(message.body);
        });
    }, function (error) {
        console.error('WebSocket connection failed:', error);
        alert("채팅 연결에 실패했습니다. 다시 로그인해주세요.");
        window.location.href = "/login";
    });
}

function handleRequest(chatRoomId, action) {
    if (stompClient) {
        stompClient.send("/app/handleChatRequest", {}, JSON.stringify({ chatRoomId: chatRoomId, action: action }));
    }
}

function requestGroupJoin(groupId) {
    const reason = prompt("모임 가입 요청 이유를 입력하세요:");
    if (reason && stompClient) {
        stompClient.send("/app/requestGroupJoin", {}, JSON.stringify({ groupId: groupId, reason: reason }));
    }
}
function renderChatList(chatRooms) {
    const chatList = document.getElementById('chatList');
    chatList.innerHTML = '';

    let groupUnread = 0;
    let personalUnread = 0;

    chatRooms.forEach(chat => {
        if (chat.status === "BLOCKED") return;
        if (activeTab === 'group' && chat.type !== 'GROUP') return;
        if (activeTab === 'personal' && chat.type !== 'PERSONAL') return;

        const isRequest = chat.status === 'PENDING';
        const item = document.createElement('article');
        item.className = `chat-item ${isRequest ? 'request-item' : ''}`;

        item.innerHTML = `
            <div class="chat-avatar">
                <div class="avatar ${chat.type === 'GROUP' ? 'avatar-design' : 'avatar-request'}">
                    <span>${chat.name.charAt(0)}${chat.type === 'GROUP' ? chat.name.charAt(1) : ''}</span>
                </div>
                ${chat.type === 'PERSONAL' ? '<div class="status-indicator"></div>' : ''}
            </div>
            <div class="chat-content">
                <div class="chat-header">
                    <div class="chat-title-group">
                        <h3 class="chat-name">${chat.name}</h3>
                        ${chat.type === 'GROUP' ? `<span class="member-count">${chat.participants.length}</span>` : ''}
                    </div>
                    <div class="chat-meta">
                        <span class="chat-time">${chat.lastMessageTime ? new Date(chat.lastMessageTime).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) : ''}</span>
                        ${chat.unreadCount > 0 ? `<span class="unread-count">${chat.unreadCount}</span>` : ''}
                    </div>
                </div>
                <p class="chat-preview">${isRequest ? '요청 이유: ' + chat.requestReason : (chat.lastMessage || '대화가 없습니다.')}</p>
                ${isRequest ? `
                    <div class="request-actions">
                        <button class="action-button accept" onclick="handleRequest(${chat.id}, 'APPROVE')">승인</button>
                        <button class="action-button reject" onclick="handleRequest(${chat.id}, 'REJECT')">거부</button>
                        <button class="action-button block" onclick="handleRequest(${chat.id}, 'BLOCK')">차단</button>
                    </div>
                ` : (chat.type === 'GROUP' && !chat.participants.some(p => p.name === currentUser) ? `
                    <button class="action-button accept" onclick="requestGroupJoin(${chat.id})">가입 요청</button>
                ` : '')}
            </div>
        `;
        chatList.appendChild(item);

        if (chat.type === 'GROUP') groupUnread += chat.unreadCount;
        else personalUnread += chat.unreadCount;
    });

    document.getElementById('groupUnreadCount').textContent = groupUnread;
    document.getElementById('personalUnreadCount').textContent = personalUnread;
}

// 탭 전환
document.querySelector('.tab-group').addEventListener('click', () => {
    activeTab = 'group';
    document.querySelector('.tab-group').classList.add('active');
    document.querySelector('.tab-personal').classList.remove('active');
    if (stompClient) stompClient.send("/app/refreshChatRooms", {}, "");
});

document.querySelector('.tab-personal').addEventListener('click', () => {
    activeTab = 'personal';
    document.querySelector('.tab-personal').classList.add('active');
    document.querySelector('.tab-group').classList.remove('active');
    if (stompClient) stompClient.send("/app/refreshChatRooms", {}, "");
});

// 채팅 열기/닫기
(() => {
    const state = { isChatOpen: false };
    const messagesList = document.getElementById('messagesList');
    const openButton = document.getElementById('openChat');
    const closeButton = document.getElementById('closeChat');

    function update() {
        if (state.isChatOpen) {
            messagesList.classList.add('visible');
            openButton.classList.add('hidden');
            closeButton.classList.remove('hidden');
            connect();
        } else {
            messagesList.classList.remove('visible');
            openButton.classList.remove('hidden');
            closeButton.classList.add('hidden');
            if (stompClient) stompClient.disconnect();
        }
    }

    openButton.addEventListener('click', () => {
        state.isChatOpen = true;
        update();
    });

    closeButton.addEventListener('click', () => {
        state.isChatOpen = false;
        update();
    });

    update();
})();