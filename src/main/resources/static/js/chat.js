let stompClient = null;
let activeTab = 'PRIVATE';
let currentChatRoomId = null;
let isConnected = false;
let isScrollable = true;

function connect() {
    if (!currentUser) {
        console.error('currentUser is not set. Redirecting to login.');
        window.location.href = "/login";
        return;
    }
    if (isConnected) {
        console.log('Already connected, skipping connect.');
        refreshMessages();
        return;
    }

    console.log('Connecting with currentUser:', currentUser);
    const socket = new SockJS('/chat', null, { transports: ['websocket'], debug: true });
    stompClient = Stomp.over(socket);

    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
        isConnected = true;

        stompClient.subscribe('/user/' + currentUser + '/topic/chatrooms', function (message) {
            console.log('Received chatrooms:', message.body);
            const chatRooms = JSON.parse(message.body);
            renderChatList(chatRooms);
        });

        stompClient.subscribe('/user/' + currentUser + '/topic/messages', function (message) {
            console.log('Received raw message:', message); // 원시 메시지 객체 확인
            console.log('Received message body:', message.body); // 메시지 본문 확인
            const items = JSON.parse(message.body);
            console.log('Parsed items:', items); // 파싱된 데이터 확인
            if (Array.isArray(items)) {
                console.log('Processing array of items:', items.length);
                items.forEach(item => {
                    console.log('Processing item:', item);
                    if (item.chatRoomId === currentChatRoomId || item.date) {
                        renderMessage(item);
                    } else {
                        console.log('Item skipped - chatRoomId mismatch:', item.chatRoomId, '!=', currentChatRoomId);
                    }
                });
            } else if (items.chatRoomId === currentChatRoomId) {
                console.log('Processing single item:', items);
                renderMessage(items);
            } else {
                console.log('Single item skipped - chatRoomId mismatch:', items.chatRoomId, '!=', currentChatRoomId);
            }
        }, function (error) {
            console.error('Subscription error:', error); // 구독 오류 확인
        });

        stompClient.subscribe('/user/' + currentUser + '/topic/notification', function (message) {
            console.log('Received notification:', message.body);
            alert(message.body);
        });

        console.log('Sending refresh request with user:', currentUser);
        stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: currentUser }));
    }, function (error) {
        console.error('WebSocket connection failed:', error);
        alert("채팅 연결에 실패했습니다. 다시 로그인해주세요.");
        window.location.href = "/login";
        isConnected = false;
    });
}

function refreshMessages() {
    if (currentChatRoomId && stompClient && stompClient.connected) {
        console.log('Requesting messages for chatRoomId:', currentChatRoomId);
        stompClient.send("/app/getMessages", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
    } else {
        console.error('Cannot refresh messages: STOMP not connected or no chatRoomId');
    }
}

function renderChatList(chatRooms) {
    const chatList = document.getElementById('chatList');
    if (!chatList) {
        console.error('chatList element not found');
        return;
    }
    chatList.innerHTML = '';
    if (!Array.isArray(chatRooms) || chatRooms.length === 0) {
        chatList.innerHTML = '<p>채팅방이 없습니다.</p>';
        return;
    }

    let groupUnread = 0;
    let personalUnread = 0;

    chatRooms.forEach(chat => {
        if (chat.status === "BLOCKED" || (activeTab === 'GROUP' && chat.type !== 'GROUP') || (activeTab === 'PRIVATE' && chat.type !== 'PRIVATE')) return;

        const isRequest = chat.status === 'PENDING';
        const isRequester = chat.requester && chat.requester.uuid === currentUser;
        const isOwner = chat.owner && chat.owner.uuid === currentUser;

        const item = document.createElement('article');
        item.className = `chat-item ${isRequest ? 'request-item' : ''}`;
        if (chat.status === 'ACTIVE') {
            item.addEventListener('click', () => openPersonalChat(chat));
            item.style.cursor = 'pointer';
        } else {
            item.style.cursor = 'default';
        }

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

function renderMessage(item) {
    console.log('Rendering message:', item);
    const messagesContainer = document.querySelector('.messages-container'); // .messages-inner 대신
    if (!messagesContainer) {
        console.error('Messages container not found');
        return;
    }

    if (item.date) {
        const dateElement = document.createElement('article');
        dateElement.className = 'date-notification';
        dateElement.innerHTML = `<time class="date-text">${item.date}</time>`;
        messagesContainer.appendChild(dateElement);
    } else if (item.type === 'SYSTEM') {
        const systemElement = document.createElement('article');
        systemElement.className = 'system-notification';
        systemElement.innerHTML = `<p class="system-text">${item.content}</p>`;
        messagesContainer.appendChild(systemElement);
    } else {
        const isOwnMessage = item.sender && item.sender.uuid === currentUser;
        const messageElement = document.createElement('article');
        messageElement.className = isOwnMessage ? 'message-sent' : 'message-received';

        const timeStr = new Date(item.timestamp).toLocaleTimeString('ko-KR', {
            hour: 'numeric',
            minute: '2-digit',
            hour12: true
        });

        if (!isOwnMessage && item.sender) {
            messageElement.innerHTML = `
                    <div class="avatar" aria-label="${item.sender.name}의 아바타">${item.sender.name.slice(0, 2)}</div>
                    <div class="message-content">
                        <header class="message-header">
                            <h2 class="user-name">${item.sender.name}</h2>
                            <time class="timestamp">${timeStr}</time>
                        </header>
                        <p class="message-text">${item.content}</p>
                    </div>
                `;
        } else {
            messageElement.innerHTML = `
                    <header class="message-header">
                        <time class="timestamp">${timeStr}</time>
                    </header>
                    <p class="message-text">${item.content}</p>
                `;
        }
        messagesContainer.appendChild(messageElement);
    }
    // 스크롤이 맨 아래에 있는 경우에만 자동 스크롤
    if (isScrollable) {
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }
}

function openPersonalChat(chat) {
    currentChatRoomId = chat.id;
    const chatWindow = document.querySelector('.personal-chat');
    const messagesList = document.getElementById('messagesList');
    messagesList.classList.remove('visible');
    chatWindow.classList.add('visible');
    const chatNameElement = chatWindow.querySelector('.chat-name');
    const chatName = chat.type === 'GROUP' ? chat.name :
        (chat.requester.uuid === currentUser ? chat.owner.name : chat.requester.name);
    chatNameElement.textContent = chatName;

    const avatarSpan = chatWindow.querySelector('.avatar span');
    avatarSpan.textContent = chatName.slice(0, 2);

    const messagesContainer = document.querySelector('.messages-container'); // .messages-inner 대신
    messagesContainer.innerHTML = '';

    console.log('Opening chat, requesting messages for:', currentChatRoomId);
    refreshMessages();
}
document.addEventListener('DOMContentLoaded', function () {
    const optionsButton = document.querySelector(".options-button");
    const optionsMenu = document.querySelector(".options-menu");
    const sendButton = document.querySelector('.send-button');
    const messageInput = document.querySelector('.message-input');
    const backButton = document.querySelector('.back-button');
    const blockButton = document.querySelector('.block-option');
    const leaveButton = document.querySelector('.leave-option');
    const messagescontainer = document.querySelector('.messages-container');
    // 스크롤 이벤트 리스너
    messagescontainer.addEventListener('scroll', (e) => {
        const { scrollHeight, scrollTop, clientHeight } = e.target;
        // 스크롤이 맨 아래에 있는지 확인 (약간의 여유를 둠)
        isScrollable = Math.abs(scrollHeight - scrollTop - clientHeight) < 5; // 5px 오차 허용
    });
    optionsButton?.addEventListener("click", () => {
        optionsMenu.style.display = optionsMenu.style.display === "block" ? "none" : "block";
    });

    document.addEventListener("click", (event) => {
        if (!optionsButton.contains(event.target) && !optionsMenu.contains(event.target)) {
            optionsMenu.style.display = "none";
        }
    });

    sendButton.addEventListener('click', () => {
        const content = messageInput.value.trim();
        if (content && currentChatRoomId && stompClient && stompClient.connected) {
            stompClient.send("/app/sendMessage", {}, JSON.stringify({
                chatRoomId: currentChatRoomId,
                content: content
            }));
            messageInput.value = '';
            messagescontainer.scrollTop = messagescontainer.scrollHeight;
        }
    });

    messageInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') sendButton.click();
    });

    backButton?.addEventListener('click', () => {
        document.querySelector('.personal-chat').classList.remove('visible');

        currentChatRoomId = null;
        document.querySelector('#messagesList').classList.add('visible');
    });

    blockButton?.addEventListener('click', () => {
        if (currentChatRoomId) {
            handleRequest(currentChatRoomId, 'BLOCK');
            document.querySelector('.personal-chat').classList.remove('visible');
        }
    });

    leaveButton?.addEventListener('click', () => {
        if (currentChatRoomId) {
            console.log('Leaving chat room:', currentChatRoomId);
            document.querySelector('.personal-chat').classList.remove('visible');
        }
    });

    const tabGroup = document.querySelector('.tab-group');
    const tabPersonal = document.querySelector('.tab-personal');

    tabGroup?.addEventListener('click', () => {
        activeTab = 'GROUP';
        tabGroup.classList.add('active');
        tabPersonal.classList.remove('active');
        if (stompClient && stompClient.connected) {
            stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: currentUser }));
        }
    });

    tabPersonal?.addEventListener('click', () => {
        activeTab = 'PRIVATE';
        tabPersonal.classList.add('active');
        tabGroup.classList.remove('active');
        if (stompClient && stompClient.connected) {
            stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: currentUser }));
        }
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
            if (stompClient && stompClient.connected) {
                stompClient.disconnect(() => console.log('Disconnected'));
                stompClient = null;
                isConnected = false;
            }
        }
    }

    openButton?.addEventListener('click', () => {
        state.isChatOpen = true;
        update();
    });

    closeButton?.addEventListener('click', () => {
        state.isChatOpen = false;
        update();
    });

    update();
});