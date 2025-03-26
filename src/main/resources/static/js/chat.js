let stompClient = null;
let activeTab = 'PRIVATE'; // 기본 탭: 개인 채팅


function connect() {
    if (!currentUser) {
        console.error('currentUser is not set. Redirecting to login.');
        window.location.href = "/login";
        return;
    }
    console.log('Connecting with currentUser:', currentUser);
    const socket = new SockJS('/chat', null, { transports: ['websocket'], debug: true });
    stompClient = Stomp.over(socket);

    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
        console.log('Subscribing to: /user/' + currentUser + '/topic/chatrooms');
        stompClient.subscribe('/user/' + currentUser + '/topic/chatrooms', function (message) {
            console.log('Raw message:', message);
            const chatRooms = JSON.parse(message.body);
            console.log('Received chat rooms:', chatRooms);
            renderChatList(chatRooms);
        });

        stompClient.subscribe('/user/' + currentUser + '/topic/notification', function (message) {
            console.log('Received notification:', message.body);
            alert(message.body);
        });

        if (stompClient.connected) {
            console.log('Sending refresh request with user:', currentUser);
            stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: currentUser }));
        }
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

    if (!Array.isArray(chatRooms) || chatRooms.length === 0) {
        console.warn('No chat rooms received or invalid data:', chatRooms);
        chatList.innerHTML = '<p>채팅방이 없습니다.</p>';
        return;
    }

    let groupUnread = 0;
    let personalUnread = 0;

    chatRooms.forEach(chat => {
        console.log('Processing chat:', chat);
        if (chat.status === "BLOCKED") return;
        if (activeTab === 'GROUP' && chat.type !== 'GROUP') return;
        if (activeTab === 'PRIVATE' && chat.type !== 'PRIVATE') return;

        // 먼저 isRequester와 isOwner 정의
        const isRequest = chat.status === 'PENDING';
        const isRequester = chat.requester && chat.requester.uuid === currentUser;
        const isOwner = chat.owner && chat.owner.uuid === currentUser;

        const item = document.createElement('article');
        item.className = `chat-item ${isRequest ? 'request-item' : ''}`;

        // chatName 계산
        let chatName = chat.type === 'GROUP' ? (chat.name || 'Unnamed Group Chat') :
            (isRequester ? chat.owner?.name : chat.requester?.name) || 'Unknown';

        let lastMessageTime = chat.lastMessageTime ?
            new Date(chat.lastMessageTime).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) : '';

        item.innerHTML = `
            <div class="chat-avatar">
                <div class="avatar ${chat.type === 'GROUP' ? 'avatar-design' : 'avatar-request'}">
                    <span>${chatName.charAt(0)}${chat.type === 'GROUP' && chatName.length > 1 ? chatName.charAt(1) : ''}</span>
                </div>
            </div>
            <div class="chat-content">
                <div class="chat-header">
                    <div class="chat-title-group">
                        <h3 class="chat-name">${chatName}</h3>
                        ${chat.type === 'GROUP' && chat.participants ? `<span class="member-count">${chat.participants.length}</span>` : ''}
                    </div>
                    <div class="chat-meta">
                        <span class="chat-time">${lastMessageTime}</span>
                        ${chat.unreadCount > 0 ? `<span class="unread-count">${chat.unreadCount}</span>` : ''}
                    </div>
                </div>
                <p class="chat-preview">
                    ${isRequest ? (isRequester ? '승인 대기중입니다' : `요청 사유: ${chat.requestReason || '없음'}`) : (chat.lastMessage || '대화가 없습니다.')}
                </p>
                ${isRequest && isOwner && !isRequester ? `
                    <div class="request-actions">
                        <button class="action-button accept" onclick="handleRequest(${chat.id}, 'APPROVE')">승인</button>
                        <button class="action-button reject" onclick="handleRequest(${chat.id}, 'REJECT')">거부</button>
                        <button class="action-button block" onclick="handleRequest(${chat.id}, 'BLOCK')">차단</button>
                    </div>
                ` : ''}
            </div>
        `;
        chatList.appendChild(item);

        if (chat.type === 'GROUP') groupUnread += chat.unreadCount || 0;
        else personalUnread += chat.unreadCount || 0;
    });

    // 올바른 DOM 요소에 할당
    const groupUnreadElement = document.getElementById('groupUnreadCount');
    const personalUnreadElement = document.getElementById('personalUnreadCount');
    if (groupUnreadElement) groupUnreadElement.textContent = groupUnread || '';
    if (personalUnreadElement) personalUnreadElement.textContent = personalUnread || '';
}
function handleRequest(chatId, action) {
    if (stompClient && stompClient.connected) {
        stompClient.send("/app/handleChatRequest", {}, JSON.stringify({ chatRoomId: chatId, action: action }));
    } else {
        console.error('STOMP client is not connected');
    }
}

// 나머지 이벤트 리스너 및 초기화 코드 유지
document.addEventListener('DOMContentLoaded', function () {
    console.log('Current user set to:', currentUser);

    const tabGroup = document.querySelector('.tab-group');
    const tabPersonal = document.querySelector('.tab-personal');

    if (tabGroup) {
        tabGroup.addEventListener('click', () => {
            activeTab = 'GROUP';
            tabGroup.classList.add('active');
            tabPersonal.classList.remove('active');
            if (stompClient && stompClient.connected) {
                stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: currentUser }));
            }
        });
    }

    if (tabPersonal) {
        tabPersonal.addEventListener('click', () => {
            activeTab = 'PRIVATE';
            tabPersonal.classList.add('active');
            tabGroup.classList.remove('active');
            if (stompClient && stompClient.connected) {
                stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: currentUser }));
            }
        });
    }

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
            if (stompClient && stompClient.connected) {
                stompClient.disconnect(() => console.log('Disconnected'));
                stompClient = null;
            }
        }
    }

    if (openButton) {
        openButton.addEventListener('click', () => {
            state.isChatOpen = true;
            update();
        });
    }

    if (closeButton) {
        closeButton.addEventListener('click', () => {
            state.isChatOpen = false;
            update();
        });
    }

    update();
});