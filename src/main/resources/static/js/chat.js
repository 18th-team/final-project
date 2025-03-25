let stompClient = null;
let activeTab = 'personal';// 전역 변수 선언

function connect() {
    const socket = new SockJS('/chat');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
        console.log('Subscribing with currentUser:', currentUser);

        stompClient.subscribe('/user/' + currentUser + '/topic/chatrooms', function (message) {
            console.log('Raw message:', message);
            const chatRooms = JSON.parse(message.body);
            console.log('Received chat rooms:', chatRooms);
            renderChatList(chatRooms);
        }, function (error) {
            console.error('Chat rooms subscription error:', error);
        });

        stompClient.subscribe('/user/' + currentUser + '/topic/notification', function (message) {
            console.log('Received notification:', message.body);
            alert(message.body);
        });

        setTimeout(function () {
            console.log('Sending refresh request with user:', currentUser);
            stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ email: currentUser }));
        }, 500);
    }, function (error) {
        console.error('WebSocket connection failed:', error);
        alert("채팅 연결에 실패했습니다. 다시 로그인해주세요.");
        window.location.href = "/login";
    });
}

function renderChatList(chatRooms) {
    const chatList = document.getElementById('chatList');
    if (!chatList) {
        console.error('chatList element not found');
        return;
    }
    chatList.innerHTML = '';
    console.log('Rendering chat rooms:', chatRooms);

    let groupUnread = 0;
    let personalUnread = 0;

    chatRooms.forEach(chat => {
        console.log('Processing chat:', chat);
        if (chat.status === "BLOCKED") return;
        if (activeTab === 'group' && chat.type !== 'GROUP') return;
        if (activeTab === 'personal' && chat.type !== 'PERSONAL') return;

        const isRequest = chat.status === 'PENDING';
        const isRequester = chat.requesterEmail === currentUser;
        const item = document.createElement('article');
        item.className = `chat-item ${isRequest ? 'request-item' : ''}`;

        item.innerHTML = `
            <div class="chat-avatar">
                <div class="avatar ${chat.type === 'GROUP' ? 'avatar-design' : 'avatar-request'}">
                    <span>${chat.name.charAt(0)}${chat.type === 'GROUP' ? chat.name.charAt(1) : ''}</span>
                </div>
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
                <p class="chat-preview">${isRequest ? (isRequester ? '승인 대기중입니다' : (chat.requestReason || '없음')) : (chat.lastMessage || '대화가 없습니다.')}</p>
                ${isRequest && !isRequester ? `
                    <div class="request-actions">
                        <button class="action-button accept" onclick="handleRequest(${chat.id}, 'APPROVE')">승인</button>
                        <button class="action-button reject" onclick="handleRequest(${chat.id}, 'REJECT')">거부</button>
                        <button class="action-button block" onclick="handleRequest(${chat.id}, 'BLOCK')">차단</button>
                    </div>
                ` : (chat.type === 'GROUP' && !chat.participants.includes(currentUser) ? `
                    <button class="action-button accept" onclick="requestGroupJoin(${chat.id})">가입 요청</button>
                ` : '')}
            </div>
        `;
        chatList.appendChild(item);

        if (chat.type === 'GROUP') groupUnread += chat.unreadCount || 0;
        else personalUnread += chat.unreadCount || 0;
    });

    document.getElementById('groupUnreadCount').textContent = groupUnread;
    document.getElementById('personalUnreadCount').textContent = personalUnread;
}

function handleRequest(chatId, action) {
    stompClient.send("/app/handleChatRequest", {}, JSON.stringify({ chatRoomId: chatId, action: action }));
}

function requestGroupJoin(groupId) {
    const reason = prompt("가입 요청 이유를 입력하세요:");
    if (reason) {
        stompClient.send("/app/requestGroupJoin", {}, JSON.stringify({ groupId: groupId, reason: reason }));
    }
}

document.addEventListener('DOMContentLoaded', function() {
    console.log('Current user set to:', currentUser);

    document.querySelector('.tab-group').addEventListener('click', () => {
        activeTab = 'group';
        document.querySelector('.tab-group').classList.add('active');
        document.querySelector('.tab-personal').classList.remove('active');
        if (stompClient) stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ email: currentUser }));
    });

    document.querySelector('.tab-personal').addEventListener('click', () => {
        activeTab = 'personal';
        document.querySelector('.tab-personal').classList.add('active');
        document.querySelector('.tab-group').classList.remove('active');
        if (stompClient) stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ email: currentUser }));
    });

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
});