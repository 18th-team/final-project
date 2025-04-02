const chatApp = (function() {
    let stompClient = null;
    let activeTab = 'PRIVATE';
    let currentChatRoomId = null;
    let chatRoomsCache = [];
    let isConnected = false;
    let isScrollable = true;
    let retryCount = 0;
    let currentUser = null;
    const maxRetries = 5;
    const MAX_MESSAGE_LENGTH = 1000;
    const CONNECTION_TIMEOUT = 10000;
    let lastSendTime = 0;
    const sendRateLimit = 1000;
    let lastConnectTime = 0;
    const connectRateLimit = 2000;
    let isChatOpening = false;
    let lastMarkTime = 0;
    const markCooldown = 1000;
    let renderedMessageIds = new Set();

    let state = { isChatOpen: false, isChatRoomOpen: false, isLoading: false, isUpdating: false };

    function saveChatState() {
        const chatState = { isChatOpen: state.isChatOpen, isChatRoomOpen: state.isChatRoomOpen, currentChatRoomId, activeTab };
        localStorage.setItem('chatState', JSON.stringify(chatState));
        console.log("Saved chat state:", chatState);
    }

    function connect() {
        const now = Date.now();
        if (now - lastConnectTime < connectRateLimit) {
            console.warn("Connection attempt too frequent");
            return;
        }
        if (isConnected) {
            refreshChatRooms();
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
            currentUser = frame.headers['user-name'];
            if (!currentUser) {
                console.error("No user-name in header, redirecting to login");
                window.location.href = "/login";
                return;
            }
            console.log("Current user:", currentUser);

            subscribeToTopics();
            refreshChatRooms();
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

    function subscribeToTopics() {
        stompClient.subscribe('/user/' + currentUser + '/topic/chatrooms', message => {
            try {
                chatRoomsCache = JSON.parse(message.body);
                console.log('Received chatRooms:', chatRoomsCache);
                renderChatList(chatRoomsCache);
                if (state.isChatRoomOpen && currentChatRoomId) {
                    const chatToOpen = chatRoomsCache.find(chat => chat.id === currentChatRoomId);
                    if (chatToOpen) {
                        console.log("Reopening last chat room:", chatToOpen);
                        openPersonalChat(chatToOpen);
                    } else {
                        console.warn("Last chat room not found, resetting");
                        resetChatWindow();
                    }
                }
                updateChatUI();
            } catch (e) {
                console.error("Failed to parse chatrooms message:", e);
                showError("채팅 목록을 불러오는 데 실패했습니다.");
            }
        });

        stompClient.subscribe('/user/' + currentUser + '/topic/messages', message => {
            try {
                const items = JSON.parse(message.body);
                console.log(`[${new Date().toISOString()}] Received messages:`, items);
                if (Array.isArray(items)) {
                    items.forEach(item => handleMessage(item));
                } else {
                    handleMessage(items);
                }
            } catch (e) {
                console.error("Failed to parse messages:", e);
                showError("메시지를 불러오는 데 실패했습니다.");
            }
        });

        stompClient.subscribe('/user/' + currentUser + '/topic/errors', message => {
            console.error('Error from server:', message.body);
            showError(message.body);
        });

        stompClient.subscribe('/user/' + currentUser + '/topic/readUpdate', message => {
            const update = JSON.parse(message.body);
            console.log(`[${new Date().toISOString()}] Read update:`, update);
            updateUnreadCount(update.chatRoomId, update.unreadCount);
        });

        stompClient.subscribe('/user/' + currentUser + '/topic/notifications', message => {
            const update = JSON.parse(message.body);
            console.log(`[${new Date().toISOString()}] Notification received:`, update);
            handleNotification(update);
        });
    }

    function refreshChatRooms() {
        if (stompClient?.connected) {
            console.log(`[${new Date().toISOString()}] Refreshing chat rooms`);
            stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: currentUser }));
        }
    }

    function refreshMessages(chatId = currentChatRoomId) {
        if (chatId && stompClient?.connected) {
            const chat = chatRoomsCache.find(c => c.id === chatId);
            if (!chat?.messages) {
                console.log(`[${new Date().toISOString()}] Fetching messages for chatRoomId: ${chatId}`);
                stompClient.send("/app/getMessages", {}, JSON.stringify({ id: chatId }));
            } else {
                console.log(`[${new Date().toISOString()}] Using cached messages for chatRoomId: ${chatId}`);
                chat.messages.forEach(msg => handleMessage(msg));
            }
        }
    }

    function markMessagesAsRead() {
        const now = Date.now();
        if (now - lastMarkTime < markCooldown || !stompClient?.connected || !currentChatRoomId || !state.isChatRoomOpen) {
            return;
        }
        console.log(`[${new Date().toISOString()}] Marking messages as read for chatRoomId: ${currentChatRoomId}`);
        stompClient.send("/app/markMessagesAsRead", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
        lastMarkTime = now;
    }

    function handleMessage(item) {
        const chat = chatRoomsCache.find(c => c.id === item.chatRoomId);
        if (!chat) return;

        if (!chat.messages) chat.messages = [];
        if (!chat.messages.some(m => m.id === item.id)) {
            chat.messages.push(item);
        }

        if (item.chatRoomId === currentChatRoomId && !renderedMessageIds.has(item.id)) {
            renderedMessageIds.add(item.id);
            renderMessage(item);
        }
        renderChatList(chatRoomsCache); // 채팅 목록 갱신
    }

    function updateUnreadCount(chatRoomId, unreadCount) {
        const chat = chatRoomsCache.find(c => c.id === chatRoomId);
        if (chat) {
            chat.unreadCount = unreadCount;
            renderChatList(chatRoomsCache);
        }
    }

    function handleRequest(chatId, action) {
        if (stompClient?.connected) {
            console.log(`[${new Date().toISOString()}] Sending handleChatRequest for chatId: ${chatId}, action: ${action}`);
            stompClient.send("/app/handleChatRequest", {}, JSON.stringify({ chatRoomId: chatId, action }));
            if (action === 'APPROVE' && chatId === currentChatRoomId) {
                const chat = chatRoomsCache.find(c => c.id === chatId);
                if (chat) openPersonalChat(chat);
            } else if (['REJECT', 'BLOCK'].includes(action)) {
                resetChatWindow();
            }
        } else {
            showError("서버에 연결되어 있지 않습니다.");
        }
    }

    function handleNotification(item) {
        if (!item.sender || item.sender.uuid === currentUser) return;

        const chat = chatRoomsCache.find(c => c.id === item.chatRoomId);
        const isNotificationEnabled = chat?.notificationEnabled !== false;

        if (isNotificationEnabled && (!state.isChatRoomOpen || item.chatRoomId !== currentChatRoomId)) {
            showPushNotification({
                senderName: item.sender?.name || "Unknown",
                content: item.content || "",
                timestamp: item.timestamp,
                chatRoomId: item.chatRoomId
            });
        }
    }

    function showPushNotification(notification) {
        const container = document.getElementById('notificationContainer');
        if (!container) {
            console.warn("Notification container not found in DOM");
            return;
        }

        const nameText = container.querySelector('#notificationName');
        const messageText = container.querySelector('#notificationMessage');
        const timestampText = container.querySelector('.timestamp-text');
        const avatarContainer = container.querySelector('#avatarContainer');

        if (!nameText || !messageText || !timestampText || !avatarContainer) {
            console.error("Notification container elements not found");
            return;
        }

        nameText.textContent = notification.senderName;
        messageText.textContent = notification.content;
        timestampText.textContent = notification.timestamp ?
            new Date(notification.timestamp).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit', hour12: true }) : "";

        while (avatarContainer.firstChild) {
            avatarContainer.removeChild(avatarContainer.firstChild);
        }
        const avatarDiv = document.createElement('div');
        avatarDiv.className = 'avatar';
        const avatarSpan = document.createElement('span');
        avatarSpan.textContent = notification.senderName.slice(0, 2);
        avatarDiv.appendChild(avatarSpan);
        avatarContainer.appendChild(avatarDiv);

        container.style.display = 'block';
        container.style.opacity = '1';
        container.style.transform = 'translateX(0)';

        setTimeout(() => {
            container.style.opacity = '0';
            container.style.transform = 'translateX(400px)';
            setTimeout(() => {
                container.style.display = 'none';
            }, 300);
        }, 5000);
    }

    function renderChatList(chatRooms) {
        const chatList = document.getElementById('chatList');
        if (!chatList) return;

        const fragment = document.createDocumentFragment();

        if (state.isLoading) {
            const loadingP = document.createElement('p');
            loadingP.textContent = '채팅 목록을 불러오는 중...';
            fragment.appendChild(loadingP);
        } else if (!Array.isArray(chatRooms) || chatRooms.length === 0) {
            const emptyP = document.createElement('p');
            emptyP.textContent = '채팅방이 없습니다.';
            fragment.appendChild(emptyP);
        } else {
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

                const avatarDiv = document.createElement('div');
                avatarDiv.className = 'chat-avatar';
                const avatarInnerDiv = document.createElement('div');
                avatarInnerDiv.className = `avatar ${chat.type === 'GROUP' ? 'avatar-design' : 'avatar-request'}`;
                const avatarSpan = document.createElement('span');
                avatarSpan.textContent = chatName.slice(0, 2);
                avatarInnerDiv.appendChild(avatarSpan);
                avatarDiv.appendChild(avatarInnerDiv);
                item.appendChild(avatarDiv);

                const contentDiv = document.createElement('div');
                contentDiv.className = 'chat-content';

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

                const previewP = document.createElement('p');
                previewP.className = 'chat-preview';
                previewP.textContent = isRequest ? (isRequester ? '승인 대기중입니다' : `요청 사유: ${chat.requestReason || '없음'}`) : (chat.lastMessage || '대화가 없습니다.');
                contentDiv.appendChild(previewP);

                if (isRequest && isOwner && !isRequester) {
                    const actionsDiv = document.createElement('div');
                    actionsDiv.className = 'request-actions';

                    const acceptButton = document.createElement('button');
                    acceptButton.className = 'action-button accept';
                    acceptButton.textContent = '승인';
                    acceptButton.addEventListener('click', () => handleRequest(chat.id, 'APPROVE'));

                    const rejectButton = document.createElement('button');
                    rejectButton.className = 'action-button reject';
                    rejectButton.textContent = '거부';
                    rejectButton.addEventListener('click', () => handleRequest(chat.id, 'REJECT'));

                    const blockButton = document.createElement('button');
                    blockButton.className = 'action-button block';
                    blockButton.textContent = '차단';
                    blockButton.addEventListener('click', () => handleRequest(chat.id, 'BLOCK'));

                    actionsDiv.appendChild(acceptButton);
                    actionsDiv.appendChild(rejectButton);
                    actionsDiv.appendChild(blockButton);
                    contentDiv.appendChild(actionsDiv);
                }

                item.appendChild(contentDiv);
                fragment.appendChild(item);

                if (chat.type === 'GROUP') groupUnread += chat.unreadCount || 0;
                else personalUnread += chat.unreadCount || 0;
            });

            updateUnreadCounts(groupUnread, personalUnread);
        }

        while (chatList.firstChild) {
            chatList.removeChild(chatList.firstChild);
        }
        chatList.appendChild(fragment);
    }

    function updateUnreadCounts(groupUnread, personalUnread) {
        const groupElement = document.getElementById('groupUnreadCount');
        const personalElement = document.getElementById('personalUnreadCount');

        if (!groupElement) console.warn("Element 'groupUnreadCount' not found in DOM");
        if (!personalElement) console.warn("Element 'personalUnreadCount' not found in DOM");

        const groupValue = Number(groupUnread) || 0;
        const personalValue = Number(personalUnread) || 0;

        if (groupElement) groupElement.textContent = groupValue > 0 ? groupValue.toString() : '';
        if (personalElement) personalElement.textContent = personalValue > 0 ? personalValue.toString() : '';
    }

    function openPersonalChat(chat) {
        if (!chat || !chat.id || isChatOpening) return;
        isChatOpening = true;

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
        if (chat.messages && chat.messages.length > 0) {
            console.log(`[${new Date().toISOString()}] Rendering cached messages for chatRoomId: ${chat.id}`);
            while (messagesContainer.firstChild) {
                messagesContainer.removeChild(messagesContainer.firstChild);
            }
            renderedMessageIds.clear();
            chat.messages.forEach(msg => handleMessage(msg));
        } else {
            while (messagesContainer.firstChild) {
                messagesContainer.removeChild(messagesContainer.firstChild);
            }
            renderedMessageIds.clear();
            refreshMessages(chat.id);
        }

        markMessagesAsRead();
        updateNotificationToggle();

        const messageInput = document.querySelector('.message-input');
        const sendButton = document.querySelector('.send-button');
        updateChatInput(chat, messageInput, sendButton);
        saveChatState();
        updateChatUI();

        isChatOpening = false;
    }

    function renderMessage(item) {
        const messagesContainer = document.querySelector('.messages-container');
        if (!messagesContainer) return;

        const lastMessage = messagesContainer.lastElementChild;
        const lastDate = lastMessage?.dataset.date;
        const currentDate = item.timestamp ? new Date(item.timestamp).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' }) : '';

        if (!lastDate || lastDate !== currentDate) {
            const dateElement = document.createElement('article');
            dateElement.className = 'date-notification';
            dateElement.dataset.date = currentDate;
            const time = document.createElement('time');
            time.className = 'date-text';
            time.textContent = currentDate;
            dateElement.appendChild(time);
            messagesContainer.appendChild(dateElement);
        }

        const element = document.createElement('article');
        if (item.type === 'SYSTEM') {
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

        element.dataset.date = currentDate;
        element.style.opacity = '0';
        messagesContainer.appendChild(element);
        requestAnimationFrame(() => {
            element.style.transition = 'opacity 0.3s ease-in';
            element.style.opacity = '1';
            if (isScrollable) messagesContainer.scrollTop = messagesContainer.scrollHeight;
        });
    }

    function updateNotificationToggle() {
        const toggleButton = document.querySelector('.notification-toggle');
        if (!toggleButton || !currentChatRoomId) return;

        const chat = chatRoomsCache.find(c => c.id === currentChatRoomId);
        const isEnabled = chat?.notificationEnabled !== false;
        toggleButton.setAttribute('aria-pressed', isEnabled);
        const icon = toggleButton.querySelector('.notification-icon');
        if (icon) icon.style.fill = isEnabled ? '#333' : '#ccc';
    }

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

    function showError(message) {
        const errorDiv = document.createElement('div');
        errorDiv.className = 'error-message';
        errorDiv.textContent = message;
        document.body.appendChild(errorDiv);
        setTimeout(() => errorDiv.remove(), 5000);
    }

    function setupEventListeners() {
        const messagesContainer = document.querySelector('.messages-container');
        if (messagesContainer && !messagesContainer.dataset.listenerAdded) {
            messagesContainer.addEventListener('scroll', e => {
                const { scrollHeight, scrollTop, clientHeight } = e.target;
                isScrollable = Math.abs(scrollHeight - scrollTop - clientHeight) < 5;
            });
            messagesContainer.dataset.listenerAdded = 'true';
        }

        const optionsMenu = document.querySelector('.options-menu');
        const optionsButton = document.querySelector('.options-button');
        if (optionsButton && !optionsButton.dataset.listenerAdded) {
            optionsButton.addEventListener('click', () => {
                optionsMenu.style.display = optionsMenu.style.display === 'block' ? 'none' : 'block';
            });
            optionsButton.dataset.listenerAdded = 'true';
        }

        document.addEventListener('click', event => {
            if (!optionsButton.contains(event.target) && !optionsMenu.contains(event.target)) {
                optionsMenu.style.display = 'none';
            }
        });

        const sendButton = document.querySelector('.send-button');
        const messageInput = document.querySelector('.message-input');
        if (sendButton && !sendButton.dataset.listenerAdded) {
            sendButton.addEventListener('click', sendMessage);
            sendButton.dataset.listenerAdded = 'true';
        }
        if (messageInput && !messageInput.dataset.listenerAdded) {
            messageInput.addEventListener('keypress', e => e.key === 'Enter' && sendMessage());
            messageInput.dataset.listenerAdded = 'true';
        }

        const backButton = document.querySelector('.back-button');
        if (backButton && !backButton.dataset.listenerAdded) {
            backButton.addEventListener('click', resetChatWindow);
            backButton.dataset.listenerAdded = 'true';
        }

        const blockOption = document.querySelector('.block-option');
        if (blockOption && !blockOption.dataset.listenerAdded) {
            blockOption.addEventListener('click', () => {
                if (confirm("정말로 이 사용자를 차단하시겠습니까?") && currentChatRoomId && stompClient?.connected) {
                    stompClient.send("/app/blockUser", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
                    resetChatWindow();
                }
            });
            blockOption.dataset.listenerAdded = 'true';
        }

        const leaveOption = document.querySelector('.leave-option');
        if (leaveOption && !leaveOption.dataset.listenerAdded) {
            leaveOption.addEventListener('click', () => {
                if (confirm("정말로 이 채팅방을 나가시겠습니까?") && currentChatRoomId && stompClient?.connected) {
                    stompClient.send("/app/leaveChatRoom", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
                    resetChatWindow();
                }
            });
            leaveOption.dataset.listenerAdded = 'true';
        }

        const notificationToggle = document.querySelector('.notification-toggle');
        if (notificationToggle && !notificationToggle.dataset.listenerAdded) {
            notificationToggle.addEventListener('click', () => {
                if (!currentChatRoomId || !stompClient?.connected) return;
                const chat = chatRoomsCache.find(c => c.id === currentChatRoomId);
                const isEnabled = chat?.notificationEnabled !== false;
                const action = isEnabled ? 'OFF' : 'ON';
                console.log(`[${new Date().toISOString()}] Sending toggleNotification for chatRoomId: ${currentChatRoomId}, action: ${action}`);
                stompClient.send("/app/toggleNotification", {}, JSON.stringify({ chatRoomId: currentChatRoomId, action }));
            });
            notificationToggle.dataset.listenerAdded = 'true';
        }

        const groupTab = document.querySelector('.tab-group');
        const personalTab = document.querySelector('.tab-personal');
        if (groupTab && !groupTab.dataset.listenerAdded) {
            groupTab.addEventListener('click', () => switchTab('GROUP'));
            groupTab.dataset.listenerAdded = 'true';
        }
        if (personalTab && !personalTab.dataset.listenerAdded) {
            personalTab.addEventListener('click', () => switchTab('PRIVATE'));
            personalTab.dataset.listenerAdded = 'true';
        }

        const openButton = document.getElementById('openChat');
        const closeButton = document.getElementById('closeChat');
        if (openButton && !openButton.dataset.listenerAdded) {
            openButton.addEventListener('click', () => {
                state.isChatOpen = true;
                state.isChatRoomOpen = false;
                if (currentChatRoomId) {
                    const chat = chatRoomsCache.find(c => c.id === currentChatRoomId);
                    if (chat) openPersonalChat(chat);
                }
                updateChatUI();
            });
            openButton.dataset.listenerAdded = 'true';
        }
        if (closeButton && !closeButton.dataset.listenerAdded) {
            closeButton.addEventListener('click', () => {
                state.isChatOpen = false;
                state.isChatRoomOpen = false;
                updateChatUI();
                saveChatState();
            });
            closeButton.dataset.listenerAdded = 'true';
        }
    }

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
            console.log(`[${new Date().toISOString()}] Sending message for chatRoomId: ${currentChatRoomId}`);
            stompClient.send('/app/sendMessage', {}, JSON.stringify({ chatRoomId: currentChatRoomId, content }));
            lastSendTime = now;
            messageInput.value = '';
        }
    }

    function switchTab(tab) {
        activeTab = tab;
        updateTabUI();
        renderChatList(chatRoomsCache);
        saveChatState();
    }

    function resetChatWindow() {
        document.querySelector('.personal-chat').classList.remove('visible');
        currentChatRoomId = null;
        state.isChatRoomOpen = false;
        state.isChatOpen = true;
        document.getElementById('messagesList').classList.add('visible');
        refreshChatRooms();
        saveChatState();
    }

    function updateChatUI() {
        if (state.isUpdating) return;
        state.isUpdating = true;

        const messagesList = document.getElementById('messagesList');
        const openButton = document.getElementById('openChat');
        const closeButton = document.getElementById('closeChat');
        const chatWindow = document.querySelector('.personal-chat');

        if (state.isChatRoomOpen) {
            messagesList.classList.remove('visible');
            chatWindow?.classList.add('visible');
            openButton.classList.add('hidden');
            closeButton.classList.remove('hidden');
        } else if (state.isChatOpen) {
            messagesList.classList.add('visible');
            chatWindow?.classList.remove('visible');
            openButton.classList.add('hidden');
            closeButton.classList.remove('hidden');
        } else {
            messagesList.classList.remove('visible');
            chatWindow?.classList.remove('visible');
            openButton.classList.remove('hidden');
            closeButton.classList.add('hidden');
        }

        state.isUpdating = false;
    }

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

    return {
        connect: connect,
        handleRequest: handleRequest,
        setupEventListeners: setupEventListeners
    };
})();

document.addEventListener('DOMContentLoaded', () => {
    chatApp.connect();
    chatApp.setupEventListeners();
});