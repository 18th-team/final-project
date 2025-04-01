const chatApp = (function() {
    let stompClient = null;
    let activeTab = 'PRIVATE';
    let currentChatRoomId = null;
    let chatRoomsCache = []; // 채팅방 데이터 캐싱
    let isConnected = false;
    let isScrollable = true;
    let retryCount = 0;
    let currentUser = null; // window.currentUser 대신 클로저 변수
    const maxRetries = 5;
    const MAX_MESSAGE_LENGTH = 1000;
    const CONNECTION_TIMEOUT = 10000;
    let lastSendTime = 0;
    const sendRateLimit = 1000;
    let lastConnectTime = 0;
    const connectRateLimit = 2000; // 연결 속도 제한 추가

    let state = { isChatOpen: false, isChatRoomOpen: false, isLoading: false };

    // 상태 저장
    function saveChatState() {
        const chatState = { isChatOpen: state.isChatOpen, isChatRoomOpen: state.isChatRoomOpen, currentChatRoomId, activeTab };
        localStorage.setItem('chatState', JSON.stringify(chatState));
        console.log("Saved chat state:", chatState);
    }


    // WebSocket 연결
    function connect() {
        const now = Date.now();
        if (now - lastConnectTime < connectRateLimit) {
            console.warn("Connection attempt too frequent");
            return;
        }
        if (isConnected) {
            refreshMessages();
            return;
        }

        state.isLoading = true;
        updateChatUI();

        const socket = new SockJS('/chat', null, { transports: ['websocket'] });
        stompClient = Stomp.over(socket);

        const timeout = setTimeout(() => {
            if (!isConnected) {
                console.error("Connection timeout after", CONNECTION_TIMEOUT, "ms");
                showError("서버 연결 시간이 초과되었습니다.");
                state.isLoading = false;
                updateChatUI();
            }
        }, CONNECTION_TIMEOUT);

        stompClient.connect({}, frame => {
            clearTimeout(timeout);
            console.log("Connected:", frame);
            isConnected = true;
            retryCount = 0;
            state.isLoading = false;
            lastConnectTime = now;
            const currentUserFromHeader = frame.headers['user-name'];
            if (!currentUserFromHeader) {
                console.error("No user-name in header, redirecting to login");
                window.location.href = "/login";
                return;
            }
            currentUser = currentUserFromHeader; // 클로저 변수에 저장
            console.log("Current user:", currentUser);

            subscribeToTopics();
            stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: currentUser }));
        }, error => {
            clearTimeout(timeout);
            console.error("Connection error:", error);
            state.isLoading = false;
            if (retryCount < maxRetries) {
                retryCount++;
                console.log(`Retrying connection (${retryCount}/${maxRetries})...`);
                setTimeout(connect, 1000 * retryCount);
            } else {
                showError("채팅 서버에 연결할 수 없습니다. 로그인 페이지로 이동합니다.");
                window.location.href = "/login";
            }
            isConnected = false;
            updateChatUI();
        });
    }

    // 토픽 구독
    function subscribeToTopics() {
        stompClient.subscribe('/user/' + currentUser + '/topic/chatrooms', message => {
            try {
                chatRoomsCache = JSON.parse(message.body); // JSON 파싱 오류 방지
                console.log('Received chatRooms:', chatRoomsCache);
                renderChatList(chatRoomsCache);
                if (state.isChatRoomOpen && currentChatRoomId) {
                    const chatToOpen = chatRoomsCache.find(chat => chat.id === currentChatRoomId);
                    if (chatToOpen) {
                        console.log("Reopening last chat room:", chatToOpen);
                        openPersonalChat(chatToOpen);
                    } else {
                        console.warn("Last chat room not found, resetting currentChatRoomId");
                        currentChatRoomId = null;
                        state.isChatRoomOpen = false;
                        state.isChatOpen = true;
                        saveChatState();
                    }
                }
                updateChatUI();
            } catch (e) {
                console.error("Failed to parse chatrooms message:", e);
                showError("채팅 목록을 불러오는 데 실패했습니다.");
            }
        }, error => {
            console.error("Subscription to /topic/chatrooms failed:", error);
            showError("채팅 목록 구독에 실패했습니다.");
        });

        stompClient.subscribe('/user/' + currentUser + '/topic/messages', message => {
            try {
                const items = JSON.parse(message.body);
                console.log('Received messages:', items);
                if (Array.isArray(items)) {
                    if (items.length === 0) console.log("No messages received for chatRoomId:", currentChatRoomId);
                    items.forEach(item => {
                        console.log("Rendering item:", item);
                        renderMessage(item);
                    });
                } else if (items.chatRoomId === currentChatRoomId) {
                    console.log("Rendering single item:", items);
                    renderMessage(items);
                }
            } catch (e) {
                console.error("Failed to parse messages:", e);
                showError("메시지를 불러오는 데 실패했습니다.");
            }
        });

        stompClient.subscribe('/user/' + currentUser + '/topic/errors', message => {
            const errorMsg = message.body;
            console.error('Error from server:', errorMsg);
            showError(errorMsg);
        });
    }

    // 메시지 새로고침
    function refreshMessages() {
        if (currentChatRoomId && stompClient?.connected) {
            console.log("Sending getMessages for chatRoomId:", currentChatRoomId);
            stompClient.send("/app/getMessages", {}, JSON.stringify({ id: currentChatRoomId }));
        } else {
            console.warn("Cannot refresh messages: currentChatRoomId is null or stompClient not connected");
        }
    }

    // 채팅 목록 렌더링
    function renderChatList(chatRooms) {
        const chatList = document.getElementById('chatList');
        if (!chatList) return;

        // 기존 innerHTML 제거
        while (chatList.firstChild) {
            chatList.removeChild(chatList.firstChild);
        }

        if (state.isLoading) {
            const loadingP = document.createElement('p');
            loadingP.textContent = '채팅 목록을 불러오는 중...';
            chatList.appendChild(loadingP);
            return;
        }

        if (!Array.isArray(chatRooms) || chatRooms.length === 0) {
            const emptyP = document.createElement('p');
            emptyP.textContent = '채팅방이 없습니다.';
            chatList.appendChild(emptyP);
            return;
        }

        let groupUnread = 0, personalUnread = 0;

        chatRooms.filter(chat => activeTab === chat.type).forEach(chat => {
            const isRequest = chat.status === 'PENDING';
            const isRequester = chat.requester?.uuid === currentUser;
            const isOwner = chat.owner?.uuid === currentUser;
            const isClosed = chat.status === 'CLOSED' || chat.status === 'BLOCKED';

            const item = document.createElement('article');
            item.className = `chat-item ${isRequest ? 'request-item' : ''} ${isClosed ? 'closed-item' : ''}`;
            if (['ACTIVE', 'CLOSED', 'BLOCKED'].includes(chat.status)) {
                item.addEventListener('click', () => openPersonalChat(chat));
                item.style.cursor = 'pointer';
            }

            const chatName = chat.type === 'GROUP' ? (chat.name || 'Unnamed Group') :
                (isRequester ? chat.owner?.name : chat.requester?.name) || 'Unknown';
            const lastMessageTime = chat.lastMessageTime ?
                new Date(chat.lastMessageTime).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) : '';
            const avatarText = chatName.slice(0, 2);

            // chat-avatar
            const avatarDiv = document.createElement('div');
            avatarDiv.className = 'chat-avatar';
            const avatarInnerDiv = document.createElement('div');
            avatarInnerDiv.className = `avatar ${chat.type === 'GROUP' ? 'avatar-design' : 'avatar-request'}`;
            const avatarSpan = document.createElement('span');
            avatarSpan.textContent = avatarText;
            avatarInnerDiv.appendChild(avatarSpan);
            avatarDiv.appendChild(avatarInnerDiv);
            item.appendChild(avatarDiv);

            // chat-content
            const contentDiv = document.createElement('div');
            contentDiv.className = 'chat-content';

            // chat-header
            const headerDiv = document.createElement('div');
            headerDiv.className = 'chat-header';

            const titleGroupDiv = document.createElement('div');
            titleGroupDiv.className = 'chat-title-group';
            const h3 = document.createElement('h3');
            h3.className = 'chat-name';
            h3.textContent = chatName;
            titleGroupDiv.appendChild(h3);
            if (chat.type === 'GROUP' && chat.participants) {
                const memberCountSpan = document.createElement('span');
                memberCountSpan.className = 'member-count';
                memberCountSpan.textContent = chat.participants.length;
                titleGroupDiv.appendChild(memberCountSpan);
            }
            headerDiv.appendChild(titleGroupDiv);

            const metaDiv = document.createElement('div');
            metaDiv.className = 'chat-meta';
            const timeSpan = document.createElement('span');
            timeSpan.className = 'chat-time';
            timeSpan.textContent = lastMessageTime;
            metaDiv.appendChild(timeSpan);
            if (chat.unreadCount > 0) {
                const unreadSpan = document.createElement('span');
                unreadSpan.className = 'unread-count';
                unreadSpan.textContent = chat.unreadCount;
                metaDiv.appendChild(unreadSpan);
            }
            headerDiv.appendChild(metaDiv);
            contentDiv.appendChild(headerDiv);

            // chat-preview
            const previewP = document.createElement('p');
            previewP.className = 'chat-preview';
            previewP.textContent = isRequest ? (isRequester ? '승인 대기중입니다' : `요청 사유: ${chat.requestReason || '없음'}`) : (chat.lastMessage || '대화가 없습니다.');
            contentDiv.appendChild(previewP);

            // request-actions
            if (isRequest && isOwner && !isRequester) {
                const actionsDiv = document.createElement('div');
                actionsDiv.className = 'request-actions';

                const acceptButton = document.createElement('button');
                acceptButton.className = 'action-button accept';
                acceptButton.textContent = '승인';
                acceptButton.addEventListener('click', () => chatApp.handleRequest(chat.id, 'APPROVE'));

                const rejectButton = document.createElement('button');
                rejectButton.className = 'action-button reject';
                rejectButton.textContent = '거부';
                rejectButton.addEventListener('click', () => chatApp.handleRequest(chat.id, 'REJECT'));

                const blockButton = document.createElement('button');
                blockButton.className = 'action-button block';
                blockButton.textContent = '차단';
                blockButton.addEventListener('click', () => chatApp.handleRequest(chat.id, 'BLOCK'));

                actionsDiv.appendChild(acceptButton);
                actionsDiv.appendChild(rejectButton);
                actionsDiv.appendChild(blockButton);
                contentDiv.appendChild(actionsDiv);
            }

            item.appendChild(contentDiv);
            chatList.appendChild(item);

            if (chat.type === 'GROUP') groupUnread += chat.unreadCount || 0;
            else personalUnread += chat.unreadCount || 0;
        });

        updateUnreadCounts(groupUnread, personalUnread);
    }

    // 안 읽은 메시지 수 업데이트
    function updateUnreadCounts(groupUnread, personalUnread) {
        const groupElement = document.getElementById('groupUnreadCount');
        const personalElement = document.getElementById('personalUnreadCount');

        if (!groupElement) console.warn("Element 'groupUnreadCount' not found in DOM");
        if (!personalElement) console.warn("Element 'personalUnreadCount' not found in DOM");

        const groupValue = Number(groupUnread) || 0;
        const personalValue = Number(personalUnread) || 0;

        if (groupElement) groupElement.textContent = groupValue.toString();
        if (personalElement) personalElement.textContent = personalValue.toString();
    }

    // 채팅 요청 처리
    function handleRequest(chatId, action) {
        if (stompClient?.connected) {
            stompClient.send("/app/handleChatRequest", {}, JSON.stringify({ chatRoomId: chatId, action }));
        } else {
            showError("서버에 연결되어 있지 않습니다.");
        }
    }

    // 메시지 렌더링
    function renderMessage(item) {
        const messagesContainer = document.querySelector('.messages-container');
        if (!messagesContainer || (item.chatRoomId && item.chatRoomId !== currentChatRoomId)) return;

        const element = document.createElement('article');
        if (item.date) {
            element.className = 'date-notification';
            const time = document.createElement('time');
            time.className = 'date-text';
            time.textContent = item.date;
            element.appendChild(time);
        } else if (item.type === 'SYSTEM') {
            element.className = 'system-notification';
            const p = document.createElement('p');
            p.className = 'system-text';
            p.textContent = item.content;
            element.appendChild(p);
        } else {
            const isOwnMessage = item.sender?.uuid === currentUser;
            element.className = isOwnMessage ? 'message-sent' : 'message-received';
            const timeStr = item.timestamp ? new Date(item.timestamp).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit', hour12: true }) : '';

            if (isOwnMessage) {
                const header = document.createElement('header');
                header.className = 'message-header';
                const time = document.createElement('time');
                time.className = 'timestamp';
                time.textContent = timeStr;
                header.appendChild(time);
                element.appendChild(header);

                const p = document.createElement('p');
                p.className = 'message-text';
                p.textContent = item.content;
                element.appendChild(p);
            } else {
                const avatar = document.createElement('div');
                avatar.className = 'avatar';
                avatar.setAttribute('aria-label', `${item.sender.name}의 아바타`);
                avatar.textContent = item.sender.name.slice(0, 2);
                element.appendChild(avatar);

                const contentDiv = document.createElement('div');
                contentDiv.className = 'message-content';

                const header = document.createElement('header');
                header.className = 'message-header';
                const h2 = document.createElement('h2');
                h2.className = 'user-name';
                h2.textContent = item.sender.name;
                const time = document.createElement('time');
                time.className = 'timestamp';
                time.textContent = timeStr;
                header.appendChild(h2);
                header.appendChild(time);
                contentDiv.appendChild(header);

                const p = document.createElement('p');
                p.className = 'message-text';
                p.textContent = item.content;
                contentDiv.appendChild(p);

                element.appendChild(contentDiv);
            }
        }
        messagesContainer.appendChild(element);
        if (isScrollable) messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    // 개인 채팅 열기
    function openPersonalChat(chat) {
        if (!chat || !chat.id) {
            console.error("Invalid chat object:", chat);
            return;
        }
        currentChatRoomId = chat.id;
        state.isChatRoomOpen = true;
        state.isChatOpen = false;
        const chatWindow = document.querySelector('.personal-chat');
        document.getElementById('messagesList').classList.remove('visible');
        chatWindow.classList.add('visible');

        const chatName = chat.type === 'GROUP' ? (chat.name || 'Unnamed Group') :
            (chat.requester?.uuid === currentUser ? chat.owner?.name : chat.requester?.name) || 'Unknown';
        chatWindow.querySelector('.chat-name').textContent = chatName;
        chatWindow.querySelector('.avatar span').textContent = chatName.slice(0, 2);

        const messagesContainer = document.querySelector('.messages-container');
        while (messagesContainer.firstChild) {
            messagesContainer.removeChild(messagesContainer.firstChild); // innerHTML 대신 노드 제거
        }
        refreshMessages();

        const messageInput = document.querySelector('.message-input');
        const sendButton = document.querySelector('.send-button');
        updateChatInput(chat, messageInput, sendButton);
        saveChatState();
    }

    // 채팅 입력 상태 업데이트
    function updateChatInput(chat, messageInput, sendButton) {
        if (chat.status === 'CLOSED' || chat.status === 'BLOCKED') {
            messageInput.disabled = true;
            sendButton.disabled = true;
            messageInput.placeholder = "채팅방이 종료되었습니다.";
        } else {
            messageInput.disabled = false;
            sendButton.disabled = false;
            messageInput.placeholder = "메시지를 입력하세요.";
        }
    }

    // 에러 메시지 표시
    function showError(message) {
        const errorDiv = document.createElement('div');
        errorDiv.className = 'error-message';
        errorDiv.textContent = message;
        document.body.appendChild(errorDiv);
        setTimeout(() => errorDiv.remove(), 5000);
    }

    // 탭 UI 업데이트
    function updateTabUI() {
        const groupTab = document.querySelector('.tab-group');
        const personalTab = document.querySelector('.tab-personal');
        if (activeTab === 'GROUP') {
            groupTab?.classList.add('active');
            personalTab?.classList.remove('active');
        } else {
            personalTab?.classList.add('active');
            groupTab?.classList.remove('active');
        }
    }

    // 이벤트 리스너 설정
    function setupEventListeners() {
        const messagesContainer = document.querySelector('.messages-container');
        messagesContainer?.addEventListener('scroll', e => {
            const { scrollHeight, scrollTop, clientHeight } = e.target;
            isScrollable = Math.abs(scrollHeight - scrollTop - clientHeight) < 5;
        });

        const optionsMenu = document.querySelector('.options-menu');
        document.querySelector('.options-button')?.addEventListener('click', () => {
            optionsMenu.style.display = optionsMenu.style.display === 'block' ? 'none' : 'block';
        });

        document.addEventListener('click', event => {
            const optionsButton = document.querySelector('.options-button');
            if (!optionsButton.contains(event.target) && !optionsMenu.contains(event.target)) {
                optionsMenu.style.display = 'none';
            }
        });

        const sendButton = document.querySelector('.send-button');
        const messageInput = document.querySelector('.message-input');
        sendButton.addEventListener('click', sendMessage);
        messageInput.addEventListener('keypress', e => e.key === 'Enter' && sendMessage());

        document.querySelector('.back-button')?.addEventListener('click', () => {
            document.querySelector('.personal-chat').classList.remove('visible');
            state.isChatRoomOpen = false;
            state.isChatOpen = true;
            currentChatRoomId = null;
            document.getElementById('messagesList').classList.add('visible');
            saveChatState();
            if (stompClient?.connected) {
                stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: currentUser }));
            }
        });

        document.querySelector('.block-option')?.addEventListener('click', () => {
            if (confirm("정말로 이 사용자를 차단하시겠습니까?") && currentChatRoomId && stompClient?.connected) {
                stompClient.send("/app/blockUser", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
                resetChatWindow();
            }
        });

        document.querySelector('.leave-option')?.addEventListener('click', () => {
            if (confirm("정말로 이 채팅방을 나가시겠습니까?") && currentChatRoomId && stompClient?.connected) {
                stompClient.send("/app/leaveChatRoom", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
                resetChatWindow();
            }
        });

        document.querySelector('.tab-group')?.addEventListener('click', () => switchTab('GROUP'));
        document.querySelector('.tab-personal')?.addEventListener('click', () => switchTab('PRIVATE'));

        const openButton = document.getElementById('openChat');
        const closeButton = document.getElementById('closeChat');
        openButton?.addEventListener('click', () => {
            if (currentChatRoomId) {
                const chat = chatRoomsCache.find(c => c.id === currentChatRoomId);
                if (chat) {
                    state.isChatRoomOpen = true;
                    state.isChatOpen = false;
                    openPersonalChat(chat);
                } else {
                    console.warn("Chat room not found in cache for id:", currentChatRoomId);
                    state.isChatOpen = true;
                    state.isChatRoomOpen = false;
                    if (stompClient?.connected) {
                        stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: currentUser }));
                    }
                }
            } else {
                state.isChatOpen = true;
                state.isChatRoomOpen = false;
                if (stompClient?.connected) {
                    stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: currentUser }));
                }
            }
            updateChatUI();
        });
        closeButton?.addEventListener('click', () => {
            state.isChatOpen = false;
            state.isChatRoomOpen = false;
            updateChatUI();
            saveChatState();
        });
    }

    // 메시지 전송
    function sendMessage() {
        const messageInput = document.querySelector('.message-input');
        let content = messageInput.value.trim();
        if (content.length > MAX_MESSAGE_LENGTH) {
            showError(`메시지가 너무 깁니다. 최대 ${MAX_MESSAGE_LENGTH}자까지 가능합니다.`);
            return;
        }
        content = content.replace(/[<>&"']/g, '');
        const now = Date.now();
        if (now - lastSendTime < sendRateLimit) {
            showError("메시지를 너무 빨리 보낼 수 없습니다.");
            return;
        }
        if (content && currentChatRoomId && stompClient?.connected) {
            stompClient.send('/app/sendMessage', {}, JSON.stringify({ chatRoomId: currentChatRoomId, content }));
            lastSendTime = now;
            messageInput.value = '';
            document.querySelector('.messages-container').scrollTop = document.querySelector('.messages-container').scrollHeight;
        }
    }

    // 탭 전환
    function switchTab(tab) {
        activeTab = tab;
        updateTabUI();
        if (stompClient?.connected) {
            stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: currentUser }));
        }
        saveChatState();
    }

    // 채팅 창 초기화
    function resetChatWindow() {
        document.querySelector('.personal-chat').classList.remove('visible');
        currentChatRoomId = null;
        state.isChatRoomOpen = false;
        state.isChatOpen = true;
        document.getElementById('messagesList').classList.add('visible');
        if (stompClient?.connected) {
            stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: currentUser }));
        }
        saveChatState();
    }

    // UI 업데이트
    function updateChatUI() {
        const messagesList = document.getElementById('messagesList');
        const openButton = document.getElementById('openChat');
        const closeButton = document.getElementById('closeChat');
        const chatWindow = document.querySelector('.personal-chat');
        const chatList = document.getElementById('chatList');

        console.log("updateChatUI called with state:", state);

        if (state.isChatRoomOpen) {
            messagesList.classList.remove('visible');
            if (chatWindow) chatWindow.classList.add('visible');
            openButton.classList.add('hidden');
            closeButton.classList.remove('hidden');
        } else if (state.isChatOpen) {
            messagesList.classList.add('visible');
            if (chatWindow) chatWindow.classList.remove('visible');
            openButton.classList.add('hidden');
            closeButton.classList.remove('hidden');
            if (state.isLoading && chatList) {
                while (chatList.firstChild) {
                    chatList.removeChild(chatList.firstChild);
                }
                const loadingP = document.createElement('p');
                loadingP.textContent = '채팅 목록을 불러오는 중...';
                chatList.appendChild(loadingP);
            }
        } else {
            messagesList.classList.remove('visible');
            if (chatWindow) chatWindow.classList.remove('visible');
            openButton.classList.remove('hidden');
            closeButton.classList.add('hidden');
        }
    }

    // 외부에서 호출 가능한 함수 노출
    return {
        connect: connect,
        handleRequest: handleRequest, // renderChatList에서 사용
        setupEventListeners: setupEventListeners // DOMContentLoaded에서 호출
    };
})();

// 초기화
document.addEventListener('DOMContentLoaded', () => {
    chatApp.connect();
    chatApp.setupEventListeners();
});