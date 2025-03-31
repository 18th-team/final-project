let stompClient = null;
let activeTab = 'PRIVATE';
let currentChatRoomId = null;
let isConnected = false;
let isScrollable = true;
let retryCount = 0;
const maxRetries = 5;

// state를 전역 변수로 정의
let state = { isChatOpen: false };

function saveChatState() {
    const chatState = {
        isChatOpen: state.isChatOpen,
        currentChatRoomId: currentChatRoomId,
        activeTab: activeTab
    };
    localStorage.setItem('chatState', JSON.stringify(chatState));
}

function loadChatState() {
    const savedState = localStorage.getItem('chatState');
    if (savedState) {
        const parsedState = JSON.parse(savedState);
        state.isChatOpen = parsedState.isChatOpen || false;
        currentChatRoomId = parsedState.currentChatRoomId || null;
        activeTab = parsedState.activeTab || 'PRIVATE';
    }
}

function connect() {
    if (isConnected) {
        refreshMessages();
        return;
    }

    const socket = new SockJS('/chat', null, { transports: ['websocket'] });
    stompClient = Stomp.over(socket);

    stompClient.connect({}, frame => {
        console.log("Connected frame headers:", frame.headers);
        isConnected = true;
        retryCount = 0;
        const currentUserFromHeader = frame.headers['user-name'];
        if (!currentUserFromHeader) {
            window.location.href = "/login";
            return;
        }
        window.currentUser = currentUserFromHeader;
        console.log("User-name from header:", window.currentUser);

        stompClient.subscribe('/user/' + window.currentUser + '/topic/chatrooms', message => {
            const chatRooms = JSON.parse(message.body);
            renderChatList(chatRooms);

            const currentChatRoom = chatRooms.find(chat => chat.id === currentChatRoomId);
            if (currentChatRoom && currentChatRoom.status === 'CLOSED') {
                const messageInput = document.querySelector('.message-input');
                const sendButton = document.querySelector('.send-button');
                if (messageInput && sendButton) {
                    messageInput.disabled = true;
                    sendButton.disabled = true;
                    messageInput.placeholder = "채팅방이 종료되었습니다.";
                }
            }

            if (state.isChatOpen && currentChatRoomId) {
                const chatToOpen = chatRooms.find(chat => chat.id === currentChatRoomId);
                if (chatToOpen) {
                    openPersonalChat(chatToOpen);
                } else {
                    currentChatRoomId = null;
                    saveChatState();
                }
            }
        });

        stompClient.subscribe('/user/' + window.currentUser + '/topic/messages', message => {
            const items = JSON.parse(message.body);
            if (Array.isArray(items)) {
                items.forEach(item => {
                    if (item.chatRoomId === currentChatRoomId || item.date) {
                        renderMessage(item);
                    }
                });
            } else if (items.chatRoomId === currentChatRoomId) {
                renderMessage(items);
            }
        });

        stompClient.subscribe('/user/' + window.currentUser + '/topic/notification', message => {
            alert(message.body);
        });

        stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: window.currentUser }));
    }, error => {
        if (retryCount < maxRetries) {
            retryCount++;
            setTimeout(connect, 1000 * retryCount);
        } else {
            alert("채팅 서버에 연결할 수 없습니다. 로그인 페이지로 이동합니다.");
            window.location.href = "/login";
        }
        isConnected = false;
    });
}

function refreshMessages() {
    if (currentChatRoomId && stompClient?.connected) {
        stompClient.send("/app/getMessages", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
    }
}

function renderChatList(chatRooms) {
    const chatList = document.getElementById('chatList');
    if (!chatList) return;
    chatList.innerHTML = Array.isArray(chatRooms) && chatRooms.length > 0 ? '' : '<p>채팅방이 없습니다.</p>';

    let groupUnread = 0, personalUnread = 0;

    chatRooms.forEach(chat => {
        if ((activeTab === 'GROUP' && chat.type !== 'GROUP') || (activeTab === 'PRIVATE' && chat.type !== 'PRIVATE')) return;

        const isRequest = chat.status === 'PENDING';
        const isRequester = chat.requester?.uuid === window.currentUser;
        const isOwner = chat.owner?.uuid === window.currentUser;
        const isClosed = chat.status === 'CLOSED';
        const item = document.createElement('article');
        item.className = `chat-item ${isRequest ? 'request-item' : ''} ${isClosed ? 'closed-item' : ''}`;
        if (chat.status === 'ACTIVE' || chat.status === 'CLOSED' || chat.status === 'BLOCKED') {
            item.addEventListener('click', () => openPersonalChat(chat));
            item.style.cursor = 'pointer';
        }

        const chatName = chat.type === 'GROUP' ? (chat.name || 'Unnamed Group Chat') :
            (isRequester ? chat.owner?.name : chat.requester?.name) || 'Unknown';
        const lastMessageTime = chat.lastMessageTime ?
            new Date(chat.lastMessageTime).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) : '';
        const avatarText = chatName.charAt(0) + (chat.type === 'GROUP' && chatName.length > 1 ? chatName.charAt(1) : '');

        item.innerHTML = `
            <div class="chat-avatar">
                <div class="avatar ${chat.type === 'GROUP' ? 'avatar-design' : 'avatar-request'}">
                    <span>${avatarText}</span>
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
    if (groupUnreadElement) groupUnreadElement.textContent = groupUnread || '';
    const personalUnreadElement = document.getElementById('personalUnreadCount');
    if (personalUnreadElement) personalUnreadElement.textContent = personalUnread || '';
}

function handleRequest(chatId, action) {
    if (stompClient?.connected) {
        stompClient.send("/app/handleChatRequest", {}, JSON.stringify({ chatRoomId: chatId, action }));
    }
}

function renderMessage(item) {
    const messagesContainer = document.querySelector('.messages-container');
    if (!messagesContainer) return;

    const element = document.createElement('article');
    if (item.date) {
        element.className = 'date-notification';
        element.innerHTML = `<time class="date-text">${item.date}</time>`;
    } else if (item.type === 'SYSTEM') {
        element.className = 'system-notification';
        element.innerHTML = `<p class="system-text">${item.content}</p>`;
    } else {
        const isOwnMessage = item.sender?.uuid === window.currentUser;
        element.className = isOwnMessage ? 'message-sent' : 'message-received';
        const timeStr = new Date(item.timestamp).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit', hour12: true });

        element.innerHTML = isOwnMessage ? `
            <header class="message-header">
                <time class="timestamp">${timeStr}</time>
            </header>
            <p class="message-text">${item.content}</p>
        ` : `
            <div class="avatar" aria-label="${item.sender.name}의 아바타">${item.sender.name.slice(0, 2)}</div>
            <div class="message-content">
                <header class="message-header">
                    <h2 class="user-name">${item.sender.name}</h2>
                    <time class="timestamp">${timeStr}</time>
                </header>
                <p class="message-text">${item.content}</p>
            </div>
        `;
    }
    messagesContainer.appendChild(element);
    if (isScrollable) messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

function openPersonalChat(chat) {
    currentChatRoomId = chat.id;
    const chatWindow = document.querySelector('.personal-chat');
    document.getElementById('messagesList').classList.remove('visible');
    chatWindow.classList.add('visible');

    const chatName = chat.type === 'GROUP' ? chat.name :
        (chat.requester.uuid === window.currentUser ? chat.owner.name : chat.requester.name);
    chatWindow.querySelector('.chat-name').textContent = chatName;
    chatWindow.querySelector('.avatar span').textContent = chatName.slice(0, 2);

    document.querySelector('.messages-container').innerHTML = '';
    refreshMessages();

    const messageInput = document.querySelector('.message-input');
    const sendButton = document.querySelector('.send-button');
    if (chat.status === 'CLOSED' || chat.status === 'BLOCKED') {
        messageInput.disabled = true;
        sendButton.disabled = true;
        messageInput.placeholder = "채팅방이 종료되었습니다.";
    } else {
        messageInput.disabled = false;
        sendButton.disabled = false;
        messageInput.placeholder = "메시지를 입력하세요.";
    }
    saveChatState();
}

document.addEventListener('DOMContentLoaded', () => {
    loadChatState();

    if (activeTab === 'GROUP') {
        document.querySelector('.tab-group')?.classList.add('active');
        document.querySelector('.tab-personal')?.classList.remove('active');
    } else {
        document.querySelector('.tab-personal')?.classList.add('active');
        document.querySelector('.tab-group')?.classList.remove('active');
    }

    connect();

    const messagesContainer = document.querySelector('.messages-container');
    messagesContainer?.addEventListener('scroll', e => {
        const { scrollHeight, scrollTop, clientHeight } = e.target;
        isScrollable = Math.abs(scrollHeight - scrollTop - clientHeight) < 5;
    });

    document.querySelector('.options-button')?.addEventListener('click', () => {
        const optionsMenu = document.querySelector('.options-menu');
        optionsMenu.style.display = optionsMenu.style.display === 'block' ? 'none' : 'block';
    });

    document.addEventListener('click', event => {
        const optionsButton = document.querySelector('.options-button');
        const optionsMenu = document.querySelector('.options-menu');
        if (!optionsButton.contains(event.target) && !optionsMenu.contains(event.target)) {
            optionsMenu.style.display = 'none';
        }
    });

    const sendButton = document.querySelector('.send-button');
    const messageInput = document.querySelector('.message-input');
    sendButton.addEventListener('click', () => {
        const content = messageInput.value.trim();
        if (content && currentChatRoomId && stompClient?.connected) {
            stompClient.send('/app/sendMessage', {}, JSON.stringify({ chatRoomId: currentChatRoomId, content }));
            messageInput.value = '';
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
        }
    });

    messageInput.addEventListener('keypress', e => {
        if (e.key === 'Enter') sendButton.click();
    });

    document.querySelector('.back-button')?.addEventListener('click', () => {
        document.querySelector('.personal-chat').classList.remove('visible');
        currentChatRoomId = null;
        document.getElementById('messagesList').classList.add('visible');
        saveChatState();
    });

    document.querySelector('.block-option')?.addEventListener('click', () => {
        if (confirm("정말로 이 사용자를 차단하시겠습니까?")) {
            if (currentChatRoomId && stompClient?.connected) {
                stompClient.send("/app/blockUser", {}, JSON.stringify({chatRoomId: currentChatRoomId}));
                document.querySelector('.personal-chat').classList.remove('visible');
                currentChatRoomId = null;
                document.getElementById('messagesList').classList.add('visible');
                stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({uuid: window.currentUser}));
                saveChatState();
            }
        }
    });

    document.querySelector('.leave-option')?.addEventListener('click', () => {
        if (confirm("정말로 이 채팅방을 나가시겠습니까?")) {
            if (currentChatRoomId && stompClient?.connected) {
                stompClient.send("/app/leaveChatRoom", {}, JSON.stringify({chatRoomId: currentChatRoomId}));
                document.querySelector('.personal-chat').classList.remove('visible');
                currentChatRoomId = null;
                document.getElementById('messagesList').classList.add('visible');
                stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({uuid: window.currentUser}));
                saveChatState();
            }
        }
    });

    document.querySelector('.tab-group')?.addEventListener('click', () => {
        activeTab = 'GROUP';
        document.querySelector('.tab-group').classList.add('active');
        document.querySelector('.tab-personal').classList.remove('active');
        if (stompClient?.connected) {
            stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: window.currentUser }));
        }
        saveChatState();
    });

    document.querySelector('.tab-personal')?.addEventListener('click', () => {
        activeTab = 'PRIVATE';
        document.querySelector('.tab-personal').classList.add('active');
        document.querySelector('.tab-group').classList.remove('active');
        if (stompClient?.connected) {
            stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: window.currentUser }));
        }
        saveChatState();
    });

    const messagesList = document.getElementById('messagesList');
    const openButton = document.getElementById('openChat');
    const closeButton = document.getElementById('closeChat');
    const chatWindow = document.querySelector('.personal-chat');

    function update() {
        if (state.isChatOpen) {
            messagesList.classList.add('visible');
            openButton.classList.add('hidden');
            closeButton.classList.remove('hidden');

            if (currentChatRoomId) {
                if (stompClient?.connected) {
                    stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: window.currentUser }));
                }
            }
        } else {
            messagesList.classList.remove('visible');
            chatWindow.classList.remove('visible');
            openButton.classList.remove('hidden');
            closeButton.classList.add('hidden');
        }
        saveChatState();
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