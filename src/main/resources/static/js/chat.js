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
    let renderedMessageIds = new Map();
    let currentPage = 0;
    const pageSize = 50;
    let state = { isChatOpen: false, isChatRoomOpen: false, isLoading: false };
    let isChatRoomsLoaded = false;
    let offlineTimers = new Map();
    let notices = new Map();

    // 상태 저장
    function saveChatState() {
        const chatState = { isChatOpen: state.isChatOpen, isChatRoomOpen: state.isChatRoomOpen, currentChatRoomId, activeTab };
        localStorage.setItem('chatState', JSON.stringify(chatState));
    }

    // 상태 로드
    function loadChatState() {
        try {
            const savedState = localStorage.getItem('chatState');
            if (savedState) {
                const parsedState = JSON.parse(savedState);
                state.isChatOpen = parsedState.isChatOpen;
                state.isChatRoomOpen = parsedState.isChatRoomOpen;
                currentChatRoomId = parsedState.currentChatRoomId;
                activeTab = parsedState.activeTab || 'PRIVATE';
            }
        } catch (e) {
            console.error('Failed to load chat state:', e);
            state.isChatOpen = false;
            state.isChatRoomOpen = false;
            currentChatRoomId = null;
            activeTab = 'PRIVATE';
        }
    }

    // 서버 연결
    async function connect() {
        if (Date.now() - lastConnectTime < connectRateLimit || isConnected) {
            if (isConnected) refreshChatRooms();
            return;
        }

        state.isLoading = true;
        updateChatUI();

        const socket = new SockJS('/chat');
        stompClient = Stomp.over(socket);
        stompClient.heartbeat = { outgoing: 5000, incoming: 5000 };
        stompClient.onWebSocketClose = function() {
            isConnected = false;
            isChatRoomsLoaded = false;
            showError("연결이 끊겼습니다. 재연결을 시도합니다.");
            setTimeout(connect, 1000);
        };

        const timeout = setTimeout(() => {
            if (!isConnected) {
                showError("서버 연결 시간이 초과되었습니다.");
                state.isLoading = false;
                updateChatUI();
            }
        }, CONNECTION_TIMEOUT);

        stompClient.connect({}, frame => {
            clearTimeout(timeout);
            isConnected = true;
            retryCount = 0;
            state.isLoading = false;
            lastConnectTime = Date.now();
            currentUser = frame.headers['user-name'];
            if (!currentUser) {
                // window.location.href = "/login";
                return;
            }
            subscribeToTopics();
            stompClient.send("/app/initialStatus", {}, "{}");
            refreshChatRooms();
            setInterval(() => {
                if (!stompClient?.connected) {
                    console.warn("WebSocket disconnected, reconnecting...");
                    connect();
                }
            }, 5000);
        }, error => {
            clearTimeout(timeout);
            state.isLoading = false;
            if (retryCount < maxRetries) {
                retryCount++;
                setTimeout(connect, 1000 * retryCount);
            } else {
                showError("채팅 서버에 연결할 수 없습니다.");
                // window.location.href = "/login";
            }
            isConnected = false;
            updateChatUI();
        });
    }

    // 온라인 상태 확인 요청
    function checkOnlineStatus(chatRoomId) {
        if (stompClient?.connected && chatRoomId) {
            stompClient.send("/app/onlineStatus", {}, JSON.stringify({ chatRoomId }));
        }
    }

    // 온라인 상태 업데이트
    function updateOnlineStatus(uuid, lastOnlineTimestamp, isOnline) {
        if (offlineTimers.has(uuid)) {
            clearInterval(offlineTimers.get(uuid));
            offlineTimers.delete(uuid);
        }

        const chat = chatRoomsCache.find(c => {
            if (c.type === 'PRIVATE') {
                const targetUuid = c.requester?.uuid === currentUser ? c.owner?.uuid : c.requester?.uuid;
                return targetUuid === uuid && c.id === currentChatRoomId && state.isChatRoomOpen;
            } else if (c.type === 'GROUP') {
                return c.participants?.some(p => p.uuid === uuid) && c.id === currentChatRoomId && state.isChatRoomOpen;
            }
            return false;
        });

        if (!chat) return;

        if (chat.type === 'PRIVATE') {
            const statusElement = document.querySelector('.chat-status');
            if (!statusElement) return;
            if (isOnline) {
                statusElement.textContent = '온라인';
                statusElement.style.color = '#00cc00';
            } else if (lastOnlineTimestamp) {
                const updateOfflineStatus = () => {
                    const minutesAgo = Math.floor((Date.now() - lastOnlineTimestamp) / 60000);
                    let relativeText = minutesAgo < 1 ? '방금 전' : minutesAgo < 60 ? `${minutesAgo}분 전` : `${Math.floor(minutesAgo / 60)}시간 전`;
                    statusElement.textContent = `마지막 접속 ${relativeText}`;
                    statusElement.style.color = '#666';
                };
                updateOfflineStatus();
                const timer = setInterval(updateOfflineStatus, 60000);
                offlineTimers.set(uuid, timer);
            }
        } else {
            const indicator = document.querySelector(`.status-indicator[data-uuid="${uuid}"]`);
            if (indicator) indicator.style.backgroundColor = isOnline ? '#4caf50' : '#666';
        }
    }

    // 총 메시지 수 요청
    function getMessageCount(chatId) {
        return new Promise((resolve, reject) => {
            if (!chatId || !stompClient?.connected) {
                reject(new Error('Chat ID or connection not available'));
                return;
            }
            stompClient.send("/app/getMessageCount", {}, JSON.stringify({ id: chatId }));
            const subscription = stompClient.subscribe(`/user/${currentUser}/topic/messageCount`, message => {
                const count = JSON.parse(message.body);
                subscription.unsubscribe();
                resolve(count);
            }, error => {
                subscription.unsubscribe();
                reject(error);
            });
        });
    }

    // 공지사항 가져오기
    function fetchNotice(chatRoomId) {
        if (stompClient?.connected && chatRoomId) {
            stompClient.send("/app/getNotice", {}, JSON.stringify({ chatRoomId }));
        }
    }

    // 공지사항 렌더링
    function renderNotice(chat) {
        const noticeSection = document.getElementById('noticeSection');
        if (!noticeSection) return;

        const noticeView = document.getElementById('noticeView');
        const noticeEmpty = document.getElementById('noticeEmpty');
        const noticePreview = document.getElementById('noticePreview');
        const noticeContent = document.getElementById('noticeContent');
        const noticeEditBtn = document.querySelector('.notice-edit');
        const noticeDeleteBtn = document.querySelector('.notice-delete');
        const noticeAddBtn = document.querySelector('.notice-add');
        const noticeToggle = document.querySelector('.notice-toggle');

        // 개인 채팅이면 공지사항 섹션 숨기기
        if (chat.type !== 'GROUP') {
            noticeSection.style.display = 'none';
            noticeView.style.display = 'none';
            noticeEmpty.style.display = 'none';
            noticePreview.textContent = '';
            noticeContent.innerHTML = '';
            noticeEditBtn.style.display = 'none';
            noticeDeleteBtn.style.display = 'none';
            noticeAddBtn.style.display = 'none';
            noticeToggle.style.display = 'none';
            return;
        }

        let notice = notices.get(chat.id);
        if (!notice) {
            notice = { content: null, expanded: chat.expanded || false };
            notices.set(chat.id, notice);
        }
        const isOwner = chat.owner?.uuid === currentUser;

        noticeToggle.removeEventListener('click', toggleNoticeHandler);
        noticePreview.removeEventListener('click', previewClickHandler);

        if (!notice.content) {
            if (isOwner) {
                noticeSection.style.display = 'block';
                noticeView.style.display = 'none';
                noticeEmpty.style.display = 'block';
                noticeAddBtn.style.display = 'inline-block';
            } else {
                noticeSection.style.display = 'none';
            }
            return;
        }

        noticeSection.style.display = 'block';
        noticeView.style.display = 'block';
        noticeEmpty.style.display = 'none';

        noticePreview.textContent = notice.content.split('\n')[0];
        noticeContent.innerHTML = notice.content
            .split('\n')
            .map(line => `<p class="notice-text">${line}</p>`)
            .join('');

        noticeEditBtn.style.display = isOwner ? 'inline-block' : 'none';
        noticeDeleteBtn.style.display = isOwner ? 'inline-block' : 'none';
        noticeToggle.style.display = 'inline-block';

        const isExpanded = notice.expanded !== undefined ? notice.expanded : chat.expanded || false;
        console.log('Rendering with expanded:', isExpanded);
        noticeToggle.setAttribute('aria-expanded', isExpanded);
        noticeContent.classList.toggle('expanded', isExpanded);
        noticePreview.classList.toggle('hidden', isExpanded);

        noticeToggle.addEventListener('click', toggleNoticeHandler);
        noticePreview.addEventListener('click', previewClickHandler);
    }

    function toggleNoticeHandler() {
        const noticeContent = document.getElementById('noticeContent');
        const noticePreview = document.getElementById('noticePreview');
        const noticeToggle = document.querySelector('.notice-toggle');
        const notice = notices.get(currentChatRoomId);

        if (!noticeContent || !noticePreview || !noticeToggle || !notice) {
            console.error('Missing elements:', { noticeContent, noticePreview, noticeToggle, notice });
            return;
        }

        const currentExpanded = notice.expanded !== undefined ? notice.expanded : false;
        const newExpanded = !currentExpanded;
        console.log('Toggling to:', newExpanded);

        notice.expanded = newExpanded;
        notices.set(currentChatRoomId, notice);

        if (stompClient?.connected && currentChatRoomId) {
            const payload = JSON.stringify({ chatRoomId: currentChatRoomId, expanded: newExpanded });
            console.log('Sending at:', new Date().toISOString(), payload);
            stompClient.send("/app/toggleNoticeState", {}, payload);
        } else {
            console.error('WebSocket not connected or chatRoomId missing');
        }

        // 즉시 UI 업데이트
        noticeToggle.setAttribute('aria-expanded', newExpanded);
        noticeContent.classList.toggle('expanded', newExpanded);
        noticePreview.classList.toggle('hidden', newExpanded);
    }

    function previewClickHandler() {
        const noticeToggle = document.querySelector('.notice-toggle');
        if (noticeToggle) noticeToggle.click();
    }

    // 공지사항 모달 설정
    function setupNoticeModal() {
        const noticeModal = document.getElementById('noticeModal');
        const modalTitle = document.getElementById('modalTitle');
        const noticeForm = document.getElementById('noticeForm');
        const noticeTextInput = document.getElementById('noticeText');
        const submitNoticeBtn = document.getElementById('submitNotice');
        const cancelNoticeBtn = document.getElementById('cancelNotice');
        const noticeAddBtn = document.querySelector('.notice-add');
        const noticeEditBtn = document.querySelector('.notice-edit');
        const noticeDeleteBtn = document.querySelector('.notice-delete');

        function openModal(mode, text = '') {
            noticeModal.style.display = 'flex';
            modalTitle.textContent = mode === 'add' ? '공지사항 등록' : '공지사항 수정';
            noticeTextInput.value = text;
        }

        function closeModal() {
            noticeModal.style.display = 'none';
            noticeTextInput.value = '';
        }

        noticeAddBtn?.addEventListener('click', () => openModal('add'));
        noticeEditBtn?.addEventListener('click', () => {
            const notice = notices.get(currentChatRoomId);
            if (notice) openModal('edit', notice.content);
        });

        noticeDeleteBtn?.addEventListener('click', () => {
            if (confirm('공지사항을 삭제하시겠습니까?') && stompClient?.connected) {
                stompClient.send("/app/deleteNotice", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
            }
        });

        cancelNoticeBtn?.addEventListener('click', closeModal);

        noticeForm?.addEventListener('submit', (e) => {
            e.preventDefault();
            const content = noticeTextInput.value.trim();
            if (!content) {
                alert('공지사항 내용을 입력해주세요.');
                return;
            }
            if (!stompClient?.connected) {
                alert('서버에 연결되지 않았습니다.');
                return;
            }

            const payload = { chatRoomId: currentChatRoomId, content };
            if (modalTitle.textContent === '공지사항 등록') {
                stompClient.send("/app/createNotice", {}, JSON.stringify(payload));
            } else {
                stompClient.send("/app/updateNotice", {}, JSON.stringify(payload));
            }
            closeModal();
        });
    }

    // 토픽 구독
    function subscribeToTopics() {
        stompClient.subscribe(`/user/${currentUser}/topic/chatrooms`, message => {
            const updatedChatRooms = JSON.parse(message.body);
            console.log('Received chatrooms update:', updatedChatRooms);
            chatRoomsCache = updatedChatRooms;
            isChatRoomsLoaded = true;
            chatRoomsCache.forEach(chat => {
                if (chat.type === 'GROUP' && !notices.has(chat.id)) {
                    fetchNotice(chat.id);
                }
            });
            renderChatList(chatRoomsCache);
            // 현재 채팅방이 목록에서 사라졌는지 확인
            if (state.isChatRoomOpen && currentChatRoomId) {
                const chat = chatRoomsCache.find(c => c.id === currentChatRoomId);
                if (!chat) {
                    console.log('Current chat room removed, resetting window');
                    resetChatWindow();
                } else {
                    openPersonalChat(chat);
                }
            }
            updateChatUI();
        });

        stompClient.subscribe(`/user/${currentUser}/topic/messages`, message => {
            const items = JSON.parse(message.body);
            const processedItems = Array.isArray(items) ? items : [items];
            processedItems.forEach(item => {
                console.log('Received message:', {
                    id: item.id,
                    chatRoomId: item.chatRoomId,
                    content: item.content,
                    sender: item.sender,
                    timestamp: item.timestamp
                });
                handleMessage(item);
                if (item.chatRoomId === currentChatRoomId && state.isChatRoomOpen && item.id) {
                    setTimeout(() => {
                        console.log('New message received, marking as read:', item.id);
                        markMessageAsRead(item.id);
                    }, 500);
                }
            });
            state.isLoading = false;
        });

        stompClient.subscribe(`/user/${currentUser}/topic/errors`, message => {
            showError(message.body);
        });

        stompClient.subscribe(`/user/${currentUser}/topic/readUpdate`, message => {
            const update = JSON.parse(message.body);
            updateUnreadCount(update.chatRoomId, update.unreadCount);
        });

        stompClient.subscribe(`/user/${currentUser}/topic/notifications`, message => {
            const notification = JSON.parse(message.body);
            handleNotification(notification);
        });

        stompClient.subscribe(`/user/${currentUser}/topic/notificationUpdate`, message => {
            const update = JSON.parse(message.body);
            const chat = chatRoomsCache.find(c => c.id === update.chatRoomId);
            if (chat) {
                chat.notificationEnabled = update.notificationEnabled;
                if (currentChatRoomId === update.chatRoomId) updateNotificationToggle();
            }
        });

        stompClient.subscribe(`/user/${currentUser}/topic/onlineStatus`, message => {
            const status = JSON.parse(message.body);
            updateOnlineStatus(status.uuid, status.lastOnline, status.isOnline);
        });

        stompClient.subscribe(`/user/${currentUser}/topic/notice`, message => {
            const notice = JSON.parse(message.body);
            const chat = chatRoomsCache.find(c => c.id === notice.chatRoomId);
            if (chat) {
                if (notice.content) {
                    notices.set(notice.chatRoomId, { content: notice.content, expanded: notice.expanded || chat.expanded || false });
                } else {
                    notices.delete(notice.chatRoomId);
                }
                if (currentChatRoomId === notice.chatRoomId) {
                    renderNotice(chat);
                }
            }
        });

        stompClient.subscribe(`/user/${currentUser}/topic/noticeState`, message => {
            const update = JSON.parse(message.body);
            const notice = notices.get(update.chatRoomId);
            if (notice) {
                notice.expanded = update.expanded;
                notices.set(update.chatRoomId, notice);
                const chat = chatRoomsCache.find(c => c.id === update.chatRoomId);
                if (chat) {
                    chat.expanded = update.expanded;
                    if (update.chatRoomId === currentChatRoomId) {
                        renderNotice(chat);
                    }
                }
            }
        });
    }

    // 채팅방 목록 새로고침
    function refreshChatRooms() {
        if (stompClient?.connected) {
            console.log('Refreshing chat rooms for user:', currentUser);
            stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: currentUser }));
        }
    }

    // 메시지 새로고침
    function refreshMessages(chatId = currentChatRoomId, page = currentPage) {
        return new Promise((resolve, reject) => {
            if (!chatId || !stompClient?.connected) {
                reject(new Error('Chat ID or connection not available'));
                return;
            }
            state.isLoading = true;
            const subscription = stompClient.subscribe(`/user/${currentUser}/topic/messages`, (message) => {
                const items = JSON.parse(message.body);
                const processedItems = Array.isArray(items) ? items : [items];
                subscription.unsubscribe();
                state.isLoading = false;
                resolve(processedItems);
            }, error => {
                subscription.unsubscribe();
                state.isLoading = false;
                reject(error);
            });

            stompClient.send("/app/getMessages", {}, JSON.stringify({ id: chatId, page, size: pageSize }));
        });
    }

    // 메시지 읽음 처리
    function markMessagesAsRead() {
        if (!stompClient?.connected || !currentChatRoomId || !state.isChatRoomOpen) {
            console.warn('Mark skipped:', {
                connected: stompClient?.connected,
                chatRoomId: currentChatRoomId,
                isChatRoomOpen: state.isChatRoomOpen
            });
            return;
        }
        console.log('Marking messages as read for chatRoomId:', currentChatRoomId);
        stompClient.send("/app/markMessagesAsRead", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
    }

    // 메시지 처리
    function handleMessage(item) {
        console.log('Received message item:', item);
        const chat = chatRoomsCache.find(c => c.id === item.chatRoomId);
        if (!chat) {
            console.warn(`Chat not found for chatRoomId: ${item.chatRoomId}`);
            return;
        }

        const messageIds = renderedMessageIds.get(chat.id) || new Set();
        renderedMessageIds.set(chat.id, messageIds);

        if (!messageIds.has(item.id)) {
            messageIds.add(item.id);
            if (item.chatRoomId === currentChatRoomId && state.isChatRoomOpen) {
                renderMessage(item, 'append');
                const messagesContainer = document.querySelector('.messages-container');
                if (isScrollable) messagesContainer.scrollTop = messagesContainer.scrollHeight;
            }
        }

        // 최근 메시지 정보 업데이트
        chat.lastMessageSender = {
            uuid: item.sender?.uuid || null,
            name: item.sender?.name || null,
            lastMessage: item.content && item.content !== 'undefined' ? item.content : null,
            lastMessageTime: item.timestamp
        };
        chat.lastMessageTime = item.timestamp;

        console.log(`Updated chat ID: ${chat.id}, lastMessageSender:`, chat.lastMessageSender);

        if (state.isChatOpen && !state.isChatRoomOpen) {
            setTimeout(() => renderChatList(chatRoomsCache), 0);
        }
    }

    // 읽지 않은 메시지 수 업데이트
    function updateUnreadCount(chatRoomId, unreadCount) {
        const chat = chatRoomsCache.find(c => c.id === chatRoomId);
        if (chat) {
            chat.unreadCount = unreadCount;
            if (state.isChatOpen && !state.isChatRoomOpen) {
                renderChatList(chatRoomsCache);
            }
        }
    }

    // 채팅 요청 처리
    function handleRequest(chatId, action) {
        if (stompClient?.connected) {
            stompClient.send("/app/handleChatRequest", {}, JSON.stringify({ chatRoomId: chatId, action }));
            if (action === 'APPROVE' && chatId === currentChatRoomId) {
                const chat = chatRoomsCache.find(c => c.id === chatId);
                if (chat) openPersonalChat(chat);
            } else if (['REJECT', 'BLOCK'].includes(action)) {
                // 낙관적 업데이트: 채팅방 제거
                chatRoomsCache = chatRoomsCache.filter(c => c.id !== chatId);
                renderChatList(chatRoomsCache);
                resetChatWindow();
            }
        }
    }

    // 알림 처리
    function handleNotification(item) {
        if (!item.senderName || (item.sender && item.sender.uuid === currentUser)) return;
        const chat = chatRoomsCache.find(c => c.id === item.chatRoomId);
        if (chat?.notificationEnabled && (!state.isChatRoomOpen || item.chatRoomId !== currentChatRoomId)) {
            showPushNotification({
                senderName: item.senderName || "시스템 알림",
                content: item.content || "",
                timestamp: item.timestamp,
                chatRoomId: item.chatRoomId,
                messageId: item.messageId
            });
        }
    }

    // 푸시 알림 표시
    function showPushNotification(notification) {
        const container = document.getElementById('notificationContainer');
        if (!container) return;
        container.style.visibility = 'visible';
        const nameText = container.querySelector('#notificationName');
        const messageText = container.querySelector('#notificationMessage');
        const timestampText = container.querySelector('.timestamp-text');
        const avatarContainer = container.querySelector('#avatarContainer');

        nameText.textContent = notification.senderName;
        messageText.textContent = notification.content;
        timestampText.textContent = notification.timestamp ?
            new Date(notification.timestamp).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit', hour12: true }) : "";

        while (avatarContainer.firstChild) avatarContainer.removeChild(avatarContainer.firstChild);
        const avatarDiv = document.createElement('div');
        avatarDiv.className = 'avatar';
        avatarDiv.textContent = notification.senderName.slice(0, 2);
        avatarContainer.appendChild(avatarDiv);

        container.style.display = 'block';
        container.style.opacity = '1';
        container.style.transform = 'translateX(0)';
        container.style.cursor = 'pointer';

        container.onclick = () => {
            const chat = chatRoomsCache.find(c => c.id === notification.chatRoomId);
            if (chat) {
                openPersonalChat(chat).then(() => {
                    const messageElement = document.getElementById(`message-${notification.messageId}`);
                    if (messageElement) {
                        messageElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        messageElement.style.backgroundColor = '#f0f0f0';
                        setTimeout(() => messageElement.style.backgroundColor = '', 2000);
                    }
                });
            }
            container.style.opacity = '0';
            container.style.transform = 'translateX(400px)';
            setTimeout(() => container.style.display = 'none', 300);
        };

        setTimeout(() => {
            if (container.style.display === 'block') {
                container.style.opacity = '0';
                container.style.transform = 'translateX(400px)';
                setTimeout(() => container.style.visibility = 'hidden', 300);
            }
        }, 5000);
    }

    // 채팅 목록 렌더링
    function renderChatList(chatRooms) {
        const chatList = document.getElementById('chatList');
        if (!chatList) return;

        chatList.innerHTML = '';
        if (state.isLoading) {
            chatList.innerHTML = '<p>채팅 목록을 불러오는 중...</p>';
            return;
        }
        if (!chatRooms?.length) {
            chatList.innerHTML = '<p>채팅방이 없습니다.</p>';
            return;
        }

        let groupUnread = 0, personalUnread = 0;
        chatRooms.filter(chat => activeTab === chat.type).forEach(chat => {
            const isRequest = chat.status === 'PENDING';
            const isRequester = chat.requester?.uuid === currentUser;
            const isOwner = chat.owner?.uuid === currentUser;
            const isClosed = chat.status === 'CLOSED' || chat.status === 'BLOCKED';

            console.log(`Chat ID: ${chat.id}, lastMessageSender:`, chat.lastMessageSender);

            const item = document.createElement('article');
            item.className = `chat-item ${isRequest ? 'request-item' : ''} ${isClosed ? 'closed-item' : ''}`;
            if (chat.status !== 'PENDING') {
                item.addEventListener('click', () => openPersonalChat(chat));
                item.style.cursor = 'pointer';
            }

            const chatName = chat.type === 'GROUP' ? (chat.name || 'Unnamed Group') :
                (isRequester ? chat.owner?.name : chat.requester?.name) || 'Unknown';
            const lastMessageDate = chat.lastMessageTime ? new Date(chat.lastMessageTime) : null;
            const today = new Date();
            const isToday = lastMessageDate && lastMessageDate.toDateString() === today.toDateString();
            const lastMessageTime = lastMessageDate ?
                (isToday ?
                    lastMessageDate.toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit', hour12: true }) :
                    lastMessageDate.toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: 'numeric', minute: '2-digit', hour12: true })) : '';

            let avatarHtml = '';
            if (chat.type === 'GROUP' && chat.clubImage) {
                avatarHtml = `
                <div class="chat-avatar">
                    <div class="avatar group has-image">
                        <img class="avatar-image" src="/upload/${chat.clubImage}" style="display: block;" loading="lazy" />
                        <span class="avatar-text" style="display: none;">${chatName.slice(0, 2)}</span>
                    </div>
                </div>`;
            } else if (chat.type === 'PRIVATE' && (isRequester ? chat.owner?.profileImage : chat.requester?.profileImage)) {
                const profileImage = isRequester ? chat.owner?.profileImage : chat.requester?.profileImage;
                avatarHtml = `
                <div class="chat-avatar">
                    <div class="avatar has-image">
                        <img class="avatar-image" src="${profileImage}" style="display: block;" loading="lazy" />
                        <span class="avatar-text" style="display: none;">${chatName.slice(0, 2)}</span>
                    </div>
                </div>`;
            } else {
                avatarHtml = `
                <div class="chat-avatar">
                    <div class="avatar has-text">
                        <img class="avatar-image" style="display: none;" />
                        <span class="avatar-text" style="display: block;">${chatName.slice(0, 2)}</span>
                    </div>
                </div>`;
            }

            let chatPreview = '';
            if (isRequest) {
                chatPreview = isRequester ? '승인 대기중입니다' : `요청 사유: ${chat.requestReason || '없음'}`;
            } else if (chat.lastMessageSender?.lastMessage && chat.lastMessageSender.lastMessage.trim() !== '') {
                if (chat.lastMessageSender.name) {
                    const senderName = chat.lastMessageSender.name || 'Unknown';
                    chatPreview = `${senderName}: ${chat.lastMessageSender.lastMessage}`;
                } else {
                    chatPreview = chat.lastMessageSender.lastMessage;
                }
            } else {
                chatPreview = '대화가 없습니다.';
            }

            console.log(`Chat ID: ${chat.id}, chatPreview: "${chatPreview}"`);

            item.innerHTML = `
            ${avatarHtml}
            <div class="chat-content">
                <div class="chat-header">
                    <div class="chat-title-group">
                        <h3 class="chat-name">${chatName}</h3>
                        ${chat.unreadCount > 0 ? `<span class="unread-count">${chat.unreadCount}</span>` : ''}
                    </div>
                    <div class="chat-meta"><span class="chat-time">${lastMessageTime}</span></div>
                </div>
                <p class="chat-preview">${chatPreview}</p>
                ${isRequest && isOwner && !isRequester ? `
                    <div class="request-actions">
                        <button class="action-button approve">승인</button>
                        <button class="action-button reject">거부</button>
                        <button class="action-button block">차단</button>
                    </div>` : ''}
            </div>
        `;

            if (isRequest && isOwner && !isRequester) {
                item.querySelector('.approve').addEventListener('click', () => handleRequest(chat.id, 'APPROVE'));
                item.querySelector('.reject').addEventListener('click', () => handleRequest(chat.id, 'REJECT'));
                item.querySelector('.block').addEventListener('click', () => handleRequest(chat.id, 'BLOCK'));
            }

            chatList.appendChild(item);
            if (chat.type === 'GROUP') groupUnread += chat.unreadCount || 0;
            else personalUnread += chat.unreadCount || 0;

            const avatarImage = item.querySelector('.chat-avatar').querySelector('.avatar-image');
            if (avatarImage) {
                avatarImage.onerror = () => {
                    const avatar = avatarImage.closest('.avatar');
                    avatar.classList.remove('has-image');
                    avatar.classList.add('has-text');
                    avatarImage.style.display = 'none';
                    const avatarText = avatar.querySelector('.avatar-text');
                    avatarText.style.display = 'block';
                };
            }
        });

        updateUnreadCounts(groupUnread, personalUnread);
    }

    // 읽지 않은 메시지 수 UI 업데이트
    function updateUnreadCounts(groupUnread, personalUnread) {
        const groupElement = document.getElementById('groupUnreadCount');
        const personalElement = document.getElementById('personalUnreadCount');
        if (groupElement) groupElement.textContent = groupUnread > 0 ? groupUnread : '';
        if (personalElement) personalElement.textContent = personalUnread > 0 ? personalUnread : '';
    }

    // 참가자 목록 렌더링
    function renderParticipantsList(chat) {
        const chatWindow = document.querySelector('.personal-chat');
        let participantsSidebar = chatWindow.querySelector('.participants-sidebar');
        if (participantsSidebar) participantsSidebar.remove();

        participantsSidebar = document.createElement('aside');
        participantsSidebar.className = 'participants-sidebar';
        participantsSidebar.innerHTML = `
            <button class="participants-close-button" aria-label="참여자 목록 닫기">
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                    <path d="M12 4L4 12M4 4L12 12" stroke="#666" stroke-width="2" stroke-linecap="round"></path>
                </svg>
            </button>
            <header class="sidebar-header">
                <h2 class="participant-count">참여자 목록 (${chat.participants.length})</h2>
            </header>
            <ul class="participant-list"></ul>
        `;

        const participantList = participantsSidebar.querySelector('.participant-list');
        chat.participants.forEach(participant => {
            const isOwner = participant.uuid === chat.owner?.uuid;
            const avatarHtml = participant.profileImage ?
                `<div class="avatar participant-avatar ${isOwner ? 'avatar-leader' : ''} has-image">
                    <img class="avatar-image" src="${participant.profileImage}" style="display: block;" loading="lazy" />
                    <span class="avatar-text" style="display: none;">${participant.name.slice(0, 2)}</span>
                </div>` :
                `
                <div class="avatar participant-avatar ${isOwner ? 'avatar-leader' : ''} has-text">
                    <img class="avatar-image" style="display: none;" />
                    <span class="avatar-text" style="display: block;">${participant.name.slice(0, 2)}</span>
                </div>`;
            const participantItem = document.createElement('li');
            participantItem.className = 'participant-item';
            participantItem.innerHTML = `
                <div class="participant-avatar-container">
                    ${avatarHtml}
                    <div class="status-indicator" data-uuid="${participant.uuid}" style="background-color: #666"></div>
                </div>
                <div class="user-info">
                    <div class="user-name-container">
                        <span class="user-name">${participant.name}</span>
                        ${isOwner ? '<span class="role-badge">모임장</span>' : ''}
                    </div>
                </div>
            `;
            const avatarImage = participantItem.querySelector('.avatar-image');
            if (avatarImage) {
                avatarImage.onerror = () => {
                    const avatar = avatarImage.closest('.avatar');
                    avatar.classList.remove('has-image');
                    avatar.classList.add('has-text');
                    avatarImage.style.display = 'none';
                    const avatarText = avatar.querySelector('.avatar-text');
                    if (avatarText) avatarText.style.display = 'block';
                };
            }
            participantList.appendChild(participantItem);
        });

        chatWindow.appendChild(participantsSidebar);
        const optionsMenu = document.querySelector('.options-menu');
        const existingButton = optionsMenu.querySelector('.user-list');
        if (existingButton) existingButton.remove();

        const userListButton = document.createElement('button');
        userListButton.classList.add('user-list');
        userListButton.textContent = '참여자 목록';
        optionsMenu.appendChild(userListButton);

        let isOpen = false;
        userListButton.addEventListener('click', () => {
            isOpen = !isOpen;
            participantsSidebar.style.transform = isOpen ? 'translateX(0)' : 'translateX(240px)';
        });
        participantsSidebar.querySelector('.participants-close-button').addEventListener('click', () => {
            isOpen = false;
            participantsSidebar.style.transform = 'translateX(240px)';
        });

        checkOnlineStatus(chat.id);
    }

    const markedMessageIds = new Set();
    function markMessageAsRead(messageId) {
        if (!stompClient?.connected || !currentChatRoomId || !state.isChatRoomOpen || !messageId) {
            console.warn('Mark message skipped:', {
                connected: stompClient?.connected,
                chatRoomId: currentChatRoomId,
                isChatRoomOpen: state.isChatRoomOpen,
                messageId
            });
            return;
        }
        if (markedMessageIds.has(messageId)) {
            console.log('Message already marked as read:', messageId);
            return;
        }
        markedMessageIds.add(messageId);
        console.log('Marking message as read:', { chatRoomId: currentChatRoomId, messageId });
        stompClient.send(
            "/app/markMessageAsRead",
            {},
            JSON.stringify({ chatRoomId: currentChatRoomId, messageId })
        );
    }

    // 개인 채팅 열기
    async function openPersonalChat(chat) {
        if (!chat || !chat.id || isChatOpening) return;

        isChatOpening = true;
        currentChatRoomId = chat.id;
        state.isChatRoomOpen = true;
        state.isChatOpen = false;

        const chatWindow = document.querySelector('.personal-chat');
        let messagesContainer = chatWindow.querySelector('.messages-container');
        if (!messagesContainer) {
            messagesContainer = document.createElement('div');
            messagesContainer.className = 'messages-container';
            chatWindow.appendChild(messagesContainer);
        }

        if (!renderedMessageIds.has(chat.id)) {
            renderedMessageIds.set(chat.id, new Set());
            const totalMessages = await getMessageCount(chat.id);
            const lastPage = Math.max(0, Math.ceil(totalMessages / pageSize) - 1);

            let allMessages = [];
            for (let page = 0; page <= lastPage; page++) {
                const messages = await getMessagesWithoutSubscription(chat.id, page);
                allMessages = allMessages.concat(messages);
            }

            allMessages.forEach(msg => {
                if (!renderedMessageIds.get(chat.id).has(msg.id)) {
                    renderedMessageIds.get(chat.id).add(msg.id);
                    renderMessage(msg, 'append');
                }
            });
        }

        chatWindow.classList.add('visible');
        document.getElementById('messagesList').classList.remove('visible');

        const chatName = chat.type === 'GROUP' ? (chat.name || 'Unnamed Group') :
            (chat.requester?.uuid === currentUser ? chat.owner?.name : chat.requester?.name) || 'Unknown';
        chatWindow.querySelector('.chat-name').textContent = chatName;

        const avatar = chatWindow.querySelector('.avatar.personal');
        const avatarImage = avatar.querySelector('.avatar-image');
        const avatarText = avatar.querySelector('.avatar-text');
        const statusIndicator = avatar.querySelector('.status-indicator');
        const chatstatus = chatWindow.querySelector('.profile-info').querySelector('.chat-status');

        avatar.classList.remove('has-image', 'has-text', 'group');
        if (chat.type === 'GROUP' && chat.clubImage) {
            avatar.classList.add('group', 'has-image');
            avatarImage.src = `/upload/${chat.clubImage}`;
            avatarImage.style.display = 'block';
            avatarText.style.display = 'none';
            if (statusIndicator) statusIndicator.style.display = 'none';
            chatstatus.textContent = '';
        } else if (chat.type === 'PRIVATE' && (chat.requester?.uuid === currentUser ? chat.owner?.profileImage : chat.requester?.profileImage)) {
            const profileImage = chat.requester?.uuid === currentUser ? chat.owner?.profileImage : chat.requester?.profileImage;
            avatar.classList.add('has-image');
            avatarImage.src = profileImage;
            avatarImage.style.display = 'block';
            avatarText.style.display = 'none';
            if (statusIndicator) statusIndicator.style.display = 'block';
        } else {
            avatar.classList.add('has-text');
            avatarImage.style.display = 'none';
            avatarText.textContent = chatName.slice(0, 2);
            avatarText.style.display = 'block';
        }

        if (avatarImage) {
            avatarImage.onerror = () => {
                const avatar = avatarImage.closest('.avatar');
                avatar.classList.remove('has-image');
                avatar.classList.add('has-text');
                avatarImage.style.display = 'none';
                const avatarText = avatar.querySelector('.avatar-text');
                avatarText.textContent = chatName.slice(0, 2);
                avatarText.style.display = 'block';
            };
        }

        const optionsMenu = document.querySelector('.options-menu');
        optionsMenu.innerHTML = '';

        if (chat.type === 'PRIVATE') {
            const blockButton = document.createElement('button');
            blockButton.className = 'block-option';
            blockButton.textContent = '차단하기';
            optionsMenu.appendChild(blockButton);

            const leaveButton = document.createElement('button');
            leaveButton.className = 'leave-option';
            leaveButton.textContent = '나가기';
            optionsMenu.appendChild(leaveButton);

            const noticeSection = document.getElementById('noticeSection');
            if (noticeSection) noticeSection.style.display = 'none';
            checkOnlineStatus(chat.id);
        } else if (chat.type === 'GROUP') {
            if (chat.owner?.uuid !== currentUser) {
                const leaveButton = document.createElement('button');
                leaveButton.className = 'leave-option';
                leaveButton.textContent = '나가기';
                optionsMenu.appendChild(leaveButton);
            }
            renderParticipantsList(chat);
            fetchNotice(chat.id);
            renderNotice(chat);
        }

        markMessagesAsRead();
        updateNotificationToggle();
        const messageInput = document.querySelector('.message-input');
        const sendButton = document.querySelector('.send-button');
        updateChatInput(chat, messageInput, sendButton);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;

        saveChatState();
        updateChatUI();
        isChatOpening = false;
    }

    function getMessagesWithoutSubscription(chatId, page) {
        return new Promise((resolve, reject) => {
            if (!chatId || !stompClient?.connected) {
                reject(new Error('Chat ID or connection not available'));
                return;
            }
            state.isLoading = true;
            stompClient.send("/app/getMessages", {}, JSON.stringify({ id: chatId, page, size: pageSize }));
            const subscription = stompClient.subscribe(`/user/${currentUser}/topic/messages`, message => {
                const items = JSON.parse(message.body);
                const processedItems = Array.isArray(items) ? items : [items];
                subscription.unsubscribe();
                state.isLoading = false;
                resolve(processedItems);
            }, error => {
                subscription.unsubscribe();
                state.isLoading = false;
                reject(error);
            });
        });
    }

    // 메시지 렌더링
    function renderMessage(item, position = 'append') {
        const messagesContainer = document.querySelector('.messages-container');
        if (!messagesContainer) return;

        const lastMessage = position === 'append' ? messagesContainer.lastElementChild : messagesContainer.firstElementChild;
        const lastDate = lastMessage?.dataset.date;
        const currentDate = item.timestamp ? new Date(item.timestamp).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' }) : '';

        if (!lastDate || lastDate !== currentDate) {
            const dateElement = document.createElement('article');
            dateElement.className = 'date-notification';
            dateElement.dataset.date = currentDate;
            dateElement.innerHTML = `<time class="date-text">${currentDate}</time>`;
            position === 'prepend' ? messagesContainer.insertBefore(dateElement, messagesContainer.firstChild) : messagesContainer.appendChild(dateElement);
        }

        const element = document.createElement('article');
        element.id = `message-${item.id}`;
        if (item.type === 'SYSTEM') {
            element.className = 'system-notification';
            element.innerHTML = `<p class="system-text">${item.content}</p>`;
        } else {
            const isOwnMessage = item.sender?.uuid === currentUser;
            element.className = isOwnMessage ? 'message-sent' : 'message-received';
            const timeStr = item.timestamp ? new Date(item.timestamp).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit', hour12: true }) : '';

            if (isOwnMessage) {
                element.innerHTML = `
                <header class="message-header"><time class="timestamp">${timeStr}</time></header>
                <p class="message-text">${item.content}</p>`;
            } else {
                const chat = chatRoomsCache.find(c => c.id === item.chatRoomId);
                let profileImage = null;
                if (chat) {
                    if (chat.type === 'PRIVATE') {
                        const isRequester = chat.requester?.uuid === currentUser;
                        profileImage = isRequester ? chat.owner?.profileImage : chat.requester?.profileImage;
                    } else if (chat.type === 'GROUP') {
                        const participant = chat.participants?.find(p => p.uuid === item.sender?.uuid);
                        profileImage = participant?.profileImage;
                    }
                }

                const avatarHtml = profileImage ?
                    `
                <div class="avatar has-image">
                    <img class="avatar-image" src="${profileImage}" style="display: block;" />
                    <span class="avatar-text" style="display: none;">${item.sender.name.slice(0, 2)}</span>
                </div>` :
                    `
                <div class="avatar has-text">
                    <img class="avatar-image" style="display: none;" />
                    <span class="avatar-text" style="display: block;">${item.sender.name.slice(0, 2)}</span>
                </div>`;

                element.innerHTML = `
                ${avatarHtml}
                <div class="message-content">
                    <header class="message-header">
                        <h2 class="user-name">${item.sender.name}</h2>
                        <time class="timestamp">${timeStr}</time>
                    </header>
                    <p class="message-text">${item.content}</p>
                </div>`;

                const avatarImage = element.querySelector('.avatar-image');
                if (avatarImage) {
                    avatarImage.onerror = () => {
                        const avatar = avatarImage.closest('.avatar');
                        avatar.classList.remove('has-image');
                        avatar.classList.add('has-text');
                        avatarImage.style.display = 'none';
                        const avatarText = avatar.querySelector('.avatar-text');
                        avatarText.style.display = 'block';
                    };
                }
            }
        }
        element.dataset.date = currentDate;

        element.style.opacity = '0';
        if (position === 'prepend') {
            const previousHeight = messagesContainer.scrollHeight;
            messagesContainer.insertBefore(element, messagesContainer.firstChild);
            messagesContainer.scrollTop = messagesContainer.scrollTop + (messagesContainer.scrollHeight - previousHeight);
        } else {
            messagesContainer.appendChild(element);
            if (isScrollable) messagesContainer.scrollTop = messagesContainer.scrollHeight;
        }

        setTimeout(() => {
            element.style.transition = 'opacity 0.3s ease-in';
            element.style.opacity = '1';
        }, 0);
    }

    // 알림 토글 업데이트
    function updateNotificationToggle() {
        const toggleButton = document.querySelector('.notification-toggle');
        if (!toggleButton || !currentChatRoomId) return;
        const chat = chatRoomsCache.find(c => c.id === currentChatRoomId);
        const isEnabled = chat?.notificationEnabled !== false;
        toggleButton.setAttribute('aria-pressed', isEnabled.toString());
        const icon = toggleButton.querySelector('.notification-icon');
        if (icon) icon.style.fill = isEnabled ? '#333' : '#ccc';
    }

    // 채팅 입력 UI 업데이트
    function updateChatInput(chat, messageInput, sendButton) {
        const isDisabled = chat.status === 'CLOSED' || chat.status === 'BLOCKED';
        messageInput.disabled = isDisabled;
        sendButton.disabled = isDisabled;
        messageInput.placeholder = isDisabled ? "채팅방이 종료되었습니다." : "메시지를 입력하세요.";
    }

    // 에러 메시지 표시
    function showError(message) {
        const errorDiv = document.createElement('div');
        errorDiv.className = 'error-message';
        errorDiv.textContent = message;
        document.body.appendChild(errorDiv);
        setTimeout(() => errorDiv.remove(), 5000);
    }

    // 이벤트 리스너 설정
    function setupEventListeners() {
        const messagesContainer = document.querySelector('.messages-container');
        if (messagesContainer && !messagesContainer.dataset.listenerAdded) {
            messagesContainer.addEventListener('scroll', e => {
                const { scrollHeight, scrollTop, clientHeight } = e.target;
                isScrollable = Math.abs(scrollHeight - scrollTop - clientHeight) < 5;
            });
            messagesContainer.dataset.listenerAdded = 'true';
        }

        const chatOptions = document.querySelector('.chat-options');
        const optionsMenu = document.querySelector('.options-menu');
        const optionsButton = document.querySelector('.options-button');

        if (optionsButton) {
            optionsButton.addEventListener('click', () => {
                optionsMenu.style.display = optionsMenu.style.display === 'block' ? 'none' : 'block';
            });
        }

        document.addEventListener('click', event => {
            if (!optionsButton?.contains(event.target) && !optionsMenu?.contains(event.target)) {
                optionsMenu.style.display = 'none';
            }
        });

        if (chatOptions) {
            chatOptions.addEventListener('click', (event) => {
                const target = event.target;
                if (target.classList.contains('notification-toggle') || target.closest('.notification-toggle')) {
                    if (!currentChatRoomId || !stompClient?.connected) return;
                    const chat = chatRoomsCache.find(c => c.id === currentChatRoomId);
                    if (!chat) return;
                    const action = chat.notificationEnabled ? 'OFF' : 'ON';
                    stompClient.send("/app/toggleNotification", {}, JSON.stringify({ chatRoomId: currentChatRoomId, action }));
                }
            });
        }

        if (optionsMenu) {
            optionsMenu.addEventListener('click', (event) => {
                const target = event.target;

                if (target.classList.contains('block-option')) {
                    if (confirm("정말로 이 사용자를 차단하시겠습니까?") && currentChatRoomId && stompClient?.connected) {
                        stompClient.send("/app/blockUser", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
                        // 낙관적 업데이트: 채팅방 즉시 제거
                        chatRoomsCache = chatRoomsCache.filter(c => c.id !== currentChatRoomId);
                        renderedMessageIds.delete(currentChatRoomId);
                        notices.delete(currentChatRoomId);
                        renderChatList(chatRoomsCache);
                        resetChatWindow();
                    }
                }

                if (target.classList.contains('leave-option')) {
                    if (confirm("정말로 이 채팅방을 나가시겠습니까?") && currentChatRoomId && stompClient?.connected) {
                        stompClient.send("/app/leaveChatRoom", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
                        // 낙관적 업데이트: 채팅방 즉시 제거
                        chatRoomsCache = chatRoomsCache.filter(c => c.id !== currentChatRoomId);
                        renderedMessageIds.delete(currentChatRoomId);
                        notices.delete(currentChatRoomId);
                        renderChatList(chatRoomsCache);
                        resetChatWindow();
                    }
                }
            });
        }

        const sendButton = document.querySelector('.send-button');
        const messageInput = document.querySelector('.message-input');
        if (sendButton) sendButton.addEventListener('click', sendMessage);
        if (messageInput) messageInput.addEventListener('keypress', e => e.key === 'Enter' && sendMessage());

        const backButton = document.querySelector('.back-button');
        if (backButton) backButton.addEventListener('click', resetChatWindow);

        const groupTab = document.querySelector('.tab-group');
        const personalTab = document.querySelector('.tab-personal');
        if (groupTab) groupTab.addEventListener('click', () => switchTab('GROUP'));
        if (personalTab) personalTab.addEventListener('click', () => switchTab('PRIVATE'));

        const openButton = document.getElementById('openChat');
        const closeButton = document.getElementById('closeChat');
        if (openButton) {
            openButton.addEventListener('click', () => {
                state.isChatOpen = true;
                state.isChatRoomOpen = false;
                if (currentChatRoomId) {
                    const chat = chatRoomsCache.find(c => c.id === currentChatRoomId);
                    if (chat) openPersonalChat(chat);
                }
                updateChatUI();
            });
        }
        if (closeButton) {
            closeButton.addEventListener('click', () => {
                state.isChatOpen = false;
                state.isChatRoomOpen = false;
                updateChatUI();
                saveChatState();
            });
        }

        setupNoticeModal();
    }

    // 메시지 전송
    function sendMessage() {
        const messageInput = document.querySelector('.message-input');
        let content = messageInput.value.trim();
        if (content.length > MAX_MESSAGE_LENGTH || (Date.now() - lastSendTime < sendRateLimit)) {
            showError(content.length > MAX_MESSAGE_LENGTH ? `최대 ${MAX_MESSAGE_LENGTH}자까지 가능합니다.` : "메시지를 너무 빨리 보낼 수 없습니다.");
            return;
        }
        if (!stompClient?.connected) {
            showError("서버와 연결이 끊겼습니다. 다시 시도해주세요.");
            connect();
            return;
        }
        if (content && currentChatRoomId) {
            content = content.replace(/[<>&"']/g, '');
            stompClient.send('/app/sendMessage', {}, JSON.stringify({ chatRoomId: currentChatRoomId, content }));
            lastSendTime = Date.now();
            messageInput.value = '';
        }
    }

    // 탭 전환
    function switchTab(tab) {
        activeTab = tab;
        updateTabUI();
        renderChatList(chatRoomsCache);
        saveChatState();
    }

    // 채팅 창 초기화
    function resetChatWindow() {
        document.querySelector('.personal-chat').classList.remove('visible');
        currentChatRoomId = null;
        state.isChatRoomOpen = false;
        state.isChatOpen = true;
        document.getElementById('messagesList').classList.add('visible');
        // refreshChatRooms 호출 제거: 서버 응답에 의존
        saveChatState();
        updateChatUI();
    }

    // 채팅 UI 업데이트
    function updateChatUI() {
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

    return {
        connect,
        setupEventListeners,
        loadChatState,
        updateChatUI,
        updateTabUI,
    };
})();

document.addEventListener('DOMContentLoaded', () => {
    chatApp.loadChatState();
    chatApp.connect();
    chatApp.setupEventListeners();
    chatApp.updateChatUI();
    chatApp.updateTabUI();
});