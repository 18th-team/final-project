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
    let messageSubscription = null;
    let currentPage = 0;
    const pageSize = 50;
    let state = { isChatOpen: false, isChatRoomOpen: false, isLoading: false };
    let isChatRoomsLoaded = false; // chatRoomsCache가 채워졌는지 여부
    let offlineTimers = new Map(); // 오프라인 사용자별 타이머
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
    function connect() {
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
            isChatRoomsLoaded = false; // 연결 끊김 시 플래그 초기화
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
                window.location.href = "/login";
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
                window.location.href = "/login";
            }
            isConnected = false;
            updateChatUI();
        });
    }
    // 온라인 상태 확인 요청
    function checkOnlineStatus(chatRoomId) {
        if (stompClient?.connected && chatRoomId) {
            stompClient.send("/app/onlineStatus", {}, JSON.stringify({ chatRoomId: chatRoomId }));
        }
    }
    // 온라인 상태 업데이트
    function updateOnlineStatus(uuid, lastOnlineTimestamp, isOnline, lastOnlineRelative) {
        console.log(`Updating status - uuid: ${uuid}, isOnline: ${isOnline}, lastOnline: ${lastOnlineTimestamp}`);

        // 기존 타이머 제거
        if (offlineTimers.has(uuid)) {
            clearInterval(offlineTimers.get(uuid));
            offlineTimers.delete(uuid);
        }
        console.log('chatRoomsCache:', chatRoomsCache);
        console.log('Finding chat for uuid:', uuid);
        // 현재 열린 채팅방에서 uuid가 관련 있는지 확인
        const chat = chatRoomsCache.find(c => {
            if (c.type === 'PRIVATE') {
                const targetUuid = c.requester?.uuid === currentUser ? c.owner?.uuid : c.requester?.uuid;
                return targetUuid === uuid && c.id === currentChatRoomId && state.isChatRoomOpen;
            } else if (c.type === 'GROUP') {
                return c.participants?.some(p => p.uuid === uuid) && c.id === currentChatRoomId && state.isChatRoomOpen;
            }
            return false;
        });
        console.log('Found chat:', chat);
        // 보호 조건: chat이 없으면 함수 종료
        if (!chat) {
            console.log(`No chat found for uuid: ${uuid}, skipping status update.`);
            return;
        }

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
                if (!offlineTimers.has(uuid)) {
                    const timer = setInterval(updateOfflineStatus, 60000);
                    offlineTimers.set(uuid, timer);
                }
            }
        } else {
            const indicator = document.querySelector(`.status-indicator[data-uuid="${uuid}"]`);
            if (indicator) indicator.style.backgroundColor = isOnline ? '#4caf50' : '#666';
        }
    }
    function updateChatStatus(chat, uuid, text, color) {
        if (chat && state.isChatRoomOpen && chat.id === currentChatRoomId) {
            const statusElement = document.querySelector('.chat-status');
            if (statusElement) {
                statusElement.textContent = text;
                statusElement.style.color = color;
            }
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

    // 토픽 구독
    function subscribeToTopics() {
        stompClient.subscribe('/user/' + currentUser + '/topic/chatrooms', message => {
            chatRoomsCache = JSON.parse(message.body);
            isChatRoomsLoaded = true;
            console.log('chatRoomsCache updated:', chatRoomsCache);
            renderChatList(chatRoomsCache);
            if (state.isChatRoomOpen && currentChatRoomId) {
                const chat = chatRoomsCache.find(c => c.id === currentChatRoomId);
                if (chat) openPersonalChat(chat);
                else resetChatWindow();
            }
            updateChatUI();
        });

        stompClient.subscribe('/user/' + currentUser + '/topic/messages', message => {
            const items = JSON.parse(message.body);
            const processedItems = Array.isArray(items) ? items : [items];
            processedItems.forEach(item => {
                handleMessage(item);
                if (item.chatRoomId === currentChatRoomId && state.isChatRoomOpen) {
                    markMessagesAsRead();
                }
            });
            state.isLoading = false;
        });

        stompClient.subscribe('/user/' + currentUser + '/topic/errors', message => {
            showError(message.body);
        });

        stompClient.subscribe('/user/' + currentUser + '/topic/readUpdate', message => {
            const update = JSON.parse(message.body);
            updateUnreadCount(update.chatRoomId, update.unreadCount);
        });

        stompClient.subscribe('/user/' + currentUser + '/topic/notifications', message => {
            const update = JSON.parse(message.body);
            handleNotification(update);
        });

        stompClient.subscribe('/user/' + currentUser + '/topic/notificationUpdate', message => {
            const update = JSON.parse(message.body);
            const chat = chatRoomsCache.find(c => c.id === update.chatRoomId);
            if (chat) {
                chat.notificationEnabled = update.notificationEnabled;
                if (currentChatRoomId === update.chatRoomId) updateNotificationToggle();
            }
        });
        // 온라인 상태 구독
        stompClient.subscribe('/user/' + currentUser + '/topic/onlineStatus', message => {
            const status = JSON.parse(message.body);
            console.log('Received online status:', status);
            updateOnlineStatus(status.uuid, status.lastOnline, status.isOnline, status.lastOnlineRelative);
        });
    }

    // 채팅방 목록 새로고침
    function refreshChatRooms() {
        if (stompClient?.connected) {
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
            console.log(`Requesting messages for chat ${chatId}, page ${page}`);
        });
    }

    // 메시지 읽음 처리
    function markMessagesAsRead() {
        if (Date.now() - lastMarkTime < markCooldown || !stompClient?.connected || !currentChatRoomId || !state.isChatRoomOpen) return;
        stompClient.send("/app/markMessagesAsRead", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
        lastMarkTime = Date.now();
    }

    // 메시지 처리
    function handleMessage(item) {
        console.log('Handling message:', item);
        const chat = chatRoomsCache.find(c => c.id === item.chatRoomId);
        if (!chat) {
            console.log(`Chat room ${item.chatRoomId} not found in cache.`);
            return;
        }

        const items = Array.isArray(item) ? item : [item];
        console.log(`Received ${items.length} messages for chat ${item.chatRoomId}, page ${currentPage}`);

        if (!renderedMessageIds.has(chat.id)) {
            renderedMessageIds.set(chat.id, new Set());
        }
        const messageIds = renderedMessageIds.get(chat.id);

        items.forEach(msg => {
            if (!messageIds.has(msg.id)) {
                messageIds.add(msg.id);
                console.log(`Message ${msg.id} not rendered yet. Current chat room: ${currentChatRoomId}, Message chat room: ${msg.chatRoomId}, isChatRoomOpen: ${state.isChatRoomOpen}`);
                if (msg.chatRoomId === currentChatRoomId && state.isChatRoomOpen) {
                    renderMessage(msg, 'append');
                } else {
                    console.log(`Message ${msg.id} skipped: Not current chat room or chat not open.`);
                }
            } else {
                console.log(`Message ${msg.id} already rendered, skipping.`);
            }
        });

        if (items.length > 0) {
            const latestMsg = items[items.length - 1]; // 최신 메시지
            chat.lastMessage = latestMsg.content;
            chat.lastMessageTime = latestMsg.timestamp;
        }

        if (state.isChatOpen && !state.isChatRoomOpen) {
            setTimeout(() => renderChatList(chatRoomsCache), 0);
        }

        state.isLoading = false;
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
                resetChatWindow();
            }
        }
    }

    // 알림 처리
    function handleNotification(item) {
        console.log('handleNotification called with:', item);
        if (!item.senderName || (item.sender && item.sender.uuid === currentUser)) {
            console.log('Notification skipped: no senderName or self-message');
            return;
        }
        const chat = chatRoomsCache.find(c => c.id === item.chatRoomId);
        console.log('Chat found:', chat);
        if (chat?.notificationEnabled && (!state.isChatRoomOpen || item.chatRoomId !== currentChatRoomId)) {
            console.log('Showing push notification');
            showPushNotification({
                senderName: item.senderName || "시스템 알림",
                content: item.content || "",
                timestamp: item.timestamp,
                chatRoomId: item.chatRoomId
            });
        }else{
            console.log('Notification not shown due to conditions');
        }
    }

    // 푸시 알림 표시
    function showPushNotification(notification) {
        const container = document.getElementById('notificationContainer');
        if (!container) return;

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
        setTimeout(() => {
            container.style.opacity = '0';
            container.style.transform = 'translateX(400px)';
            setTimeout(() => container.style.display = 'none', 300);
        }, 5000);
    }

    // 채팅 목록 렌더링
    function renderChatList(chatRooms) {
        const chatList = document.getElementById('chatList');
        if (!chatList) return;

        const fragment = document.createDocumentFragment();
        if (state.isLoading) {
            const loadingP = document.createElement('p');
            loadingP.textContent = '채팅 목록을 불러오는 중...';
            fragment.appendChild(loadingP);
        } else if (!chatRooms?.length) {
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
                avatarInnerDiv.textContent = chatName.slice(0, 2);
                // profileImage 적용
                const profileImage = chat.type === 'PRIVATE' ? (isRequester ? chat.owner?.profileImage : chat.requester?.profileImage) : null;
                console.log('Chat ID:', chat.id, 'Type:', chat.type, 'isRequester:', isRequester, 'Profile Image:', profileImage);
                if (profileImage) {
                    avatarInnerDiv.style.backgroundImage = `url("${profileImage}")`;
                    avatarInnerDiv.style.backgroundSize = 'cover';
                    avatarInnerDiv.style.backgroundPosition = 'center';
                    avatarInnerDiv.textContent = '';
                }
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
                    titleGroupDiv.appendChild(unreadSpan);
                }
                headerDiv.appendChild(metaDiv);
                contentDiv.appendChild(headerDiv);

                const previewP = document.createElement('p');
                previewP.className = 'chat-preview';
                previewP.textContent = isRequest ? (isRequester ? '승인 대기중입니다' : `요청 사유: ${chat.requestReason || '없음'}`) :
                    (chat.lastMessage || '대화가 없습니다.');
                contentDiv.appendChild(previewP);

                if (isRequest && isOwner && !isRequester) {
                    const actionsDiv = document.createElement('div');
                    actionsDiv.className = 'request-actions';
                    ['APPROVE:승인', 'REJECT:거부', 'BLOCK:차단'].forEach(action => {
                        const [act, text] = action.split(':');
                        const button = document.createElement('button');
                        button.className = `action-button ${act.toLowerCase()}`;
                        button.textContent = text;
                        button.addEventListener('click', () => handleRequest(chat.id, act));
                        actionsDiv.appendChild(button);
                    });
                    contentDiv.appendChild(actionsDiv);
                }

                item.appendChild(contentDiv);
                fragment.appendChild(item);

                if (chat.type === 'GROUP') groupUnread += chat.unreadCount || 0;
                else personalUnread += chat.unreadCount || 0;
            });

            updateUnreadCounts(groupUnread, personalUnread);
        }

        while (chatList.firstChild) chatList.removeChild(chatList.firstChild);
        chatList.appendChild(fragment);
    }

    // 읽지 않은 메시지 수 UI 업데이트
    function updateUnreadCounts(groupUnread, personalUnread) {
        const groupElement = document.getElementById('groupUnreadCount');
        const personalElement = document.getElementById('personalUnreadCount');
        if (groupElement) groupElement.textContent = groupUnread > 0 ? groupUnread : '';
        if (personalElement) personalElement.textContent = personalUnread > 0 ? personalUnread : '';
    }
    // 참가자 목록 렌더링 함수
    function renderParticipantsList(chat) {
        const chatWindow = document.querySelector('.personal-chat');
        let participantsSidebar = chatWindow.querySelector('.participants-sidebar');
        const optionsMenu = document.querySelector('.options-menu');

        // 기존 참여자 목록 제거
        if (participantsSidebar) {
            participantsSidebar.remove();
        }

        // 새로운 참여자 목록 사이드바 생성
        participantsSidebar = document.createElement('aside');
        participantsSidebar.className = 'participants-sidebar';
        participantsSidebar.setAttribute('aria-label', '참여자 목록');

        // 닫기 버튼 추가
        const closeButton = document.createElement('button');
        closeButton.className = 'participants-close-button';
        closeButton.setAttribute('aria-label', '참여자 목록 닫기');
        closeButton.innerHTML = `
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                <path d="M12 4L4 12M4 4L12 12" stroke="#666" stroke-width="2" stroke-linecap="round"></path>
            </svg>
        `;
        participantsSidebar.appendChild(closeButton);

        // 헤더 추가
        const header = document.createElement('header');
        header.className = 'sidebar-header';
        const h2 = document.createElement('h2');
        h2.className = 'participant-count';
        h2.textContent = `참여자 목록 (${chat.participants.length})`;
        header.appendChild(h2);
        participantsSidebar.appendChild(header);

        // 참여자 목록 ul 생성
        const participantList = document.createElement('ul');
        participantList.className = 'participant-list';

        // 참여자 목록 아이템 추가
        chat.participants.forEach(participant => {
            const isOwner = participant.uuid === chat.owner?.uuid; // 모임장 여부 확인
            const listItem = document.createElement('li');
            listItem.className = 'participant-item';

            // 아바타 컨테이너
            const avatarContainer = document.createElement('div');
            avatarContainer.className = 'participant-avatar-container';

            const participantAvatar = document.createElement('div');
            participantAvatar.className = `participant-avatar ${isOwner ? 'avatar-leader' : ''}`;
            participantAvatar.textContent = participant.name.slice(0, 2);
            if (participant.profileImage) {
                participantAvatar.style.backgroundImage = `url("${participant.profileImage}")`;
                participantAvatar.style.backgroundSize = 'cover';
                participantAvatar.style.backgroundPosition = 'center';
                participantAvatar.textContent = '';
            }
            avatarContainer.appendChild(participantAvatar);

            const statusIndicator = document.createElement('div');
            statusIndicator.className = 'status-indicator';
            statusIndicator.dataset.uuid = participant.uuid;
            statusIndicator.style.backgroundColor = '#666'; // 기본값: 오프라인
            avatarContainer.appendChild(statusIndicator);

            listItem.appendChild(avatarContainer);

            // 유저 정보
            const userInfo = document.createElement('div');
            userInfo.className = 'user-info';

            const userNameContainer = document.createElement('div');
            userNameContainer.className = 'user-name-container';

            const userName = document.createElement('span');
            userName.className = 'user-name';
            userName.textContent = participant.name;
            userNameContainer.appendChild(userName);

            if (isOwner) {
                const roleBadge = document.createElement('span');
                roleBadge.className = 'role-badge';
                roleBadge.textContent = '모임장';
                userNameContainer.appendChild(roleBadge);
            }

            userInfo.appendChild(userNameContainer);
            listItem.appendChild(userInfo);

            participantList.appendChild(listItem);
        });

        participantsSidebar.appendChild(participantList);
        chatWindow.appendChild(participantsSidebar);

        // "유저 목록" 버튼 추가
        const existingButton = optionsMenu.querySelector('.user-list');
        if (existingButton) {
            existingButton.remove();
        }
        const userListButton = document.createElement('button');
        userListButton.classList.add('user-list');
        userListButton.textContent = '유저 목록';
        optionsMenu.appendChild(userListButton);

        // 버튼 클릭 시 사이드바 토글
        let isOpen = false;
        userListButton.addEventListener('click', () => {
            isOpen = !isOpen;
            participantsSidebar.style.transform = isOpen ? 'translateX(0)' : 'translateX(240px)';
        });

        // 닫기 버튼 이벤트
        closeButton.addEventListener('click', () => {
            isOpen = false;
            participantsSidebar.style.transform = 'translateX(240px)';
        });

        checkOnlineStatus(chat.id); // 참여자 상태 확인
    }
    // chatRoomsCache가 로드될 때까지 대기하는 함수
    async function waitForChatRooms() {
        if (isChatRoomsLoaded) return;
        return new Promise(resolve => {
            const interval = setInterval(() => {
                if (isChatRoomsLoaded) {
                    clearInterval(interval);
                    resolve();
                }
            }, 100);
        });
    }

    // 개인 채팅 열기
    async function openPersonalChat(chat) {
        console.log('openPersonalChat called with chat:', chat);
        if (!chat || !chat.id || isChatOpening) {
            console.error('Invalid chat object or chat is already opening:', chat);
            return;
        }
        if (!chatRoomsCache || chatRoomsCache.length === 0) {
            console.log("chatRoomsCache is empty, refreshing...");
            await refreshChatRooms();
        }

        // chatRoomsCache에서 chat 객체를 다시 확인
        if (!chatRoomsCache.length) {
            console.error('chatRoomsCache is still empty after waiting');
            showError('채팅방 목록을 불러올 수 없습니다. 다시 시도해주세요.');
            return;
        }
        chat = chatRoomsCache.find(c => c.id === chat.id);
        if (!chat) {
            console.error('Chat not found in chatRoomsCache:', chat.id);
            showError('채팅방을 찾을 수 없습니다. 다시 시도해주세요.');
            return;
        }
        isChatOpening = true;

        currentChatRoomId = chat.id;
        state.isChatRoomOpen = true;
        state.isChatOpen = false;

        // 총 메시지 수 요청
        let totalMessages;
        try {
            totalMessages = await getMessageCount(chat.id);
            console.log(`Total messages in chat ${chat.id}: ${totalMessages}`);
        } catch (error) {
            console.error('Failed to get message count:', error);
            totalMessages = 0; // 기본값
        }

        // 마지막 페이지 계산
        const lastPage = Math.max(0, Math.ceil(totalMessages / pageSize) - 1);

        // renderedMessageIds 초기화
        renderedMessageIds.set(chat.id, new Set());

        const chatWindow = document.querySelector('.personal-chat');
        let messagesContainer = chatWindow.querySelector('.messages-container');
        if (!messagesContainer) {
            console.warn('Messages container missing, creating one');
            messagesContainer = document.createElement('div');
            messagesContainer.className = 'messages-container';
            chatWindow.appendChild(messagesContainer);
        }

        // messagesContainer 초기화
        while (messagesContainer.firstChild) {
            messagesContainer.removeChild(messagesContainer.firstChild);
        }
        // 모든 메시지를 저장할 배열
        let allMessages = [];
        // 모든 페이지의 메시지를 순차적으로 로드 (첫 페이지부터 마지막 페이지까지)
        for (let page = 0; page <= lastPage; page++) {
            console.log(`Requesting messages for chat ${chat.id}, page ${page}`);
            try {
                const messages = await refreshMessages(chat.id, page);
                allMessages = allMessages.concat(messages);
            } catch (error) {
                console.error(`Failed to load messages for page ${page}:`, error);
            }
        }

        allMessages.forEach(msg => {
            if (!renderedMessageIds.get(chat.id).has(msg.id)) {
                renderedMessageIds.get(chat.id).add(msg.id);
                if (msg.chatRoomId === currentChatRoomId && state.isChatRoomOpen) {
                    renderMessage(msg, 'append');
                }
            }
        });
        // currentPage를 0으로 설정
        currentPage = 0;

        document.getElementById('messagesList').classList.remove('visible');
        chatWindow.classList.add('visible');

        const chatName = chat.type === 'GROUP' ? (chat.name || 'Unnamed Group') :
            (chat.requester?.uuid === currentUser ? chat.owner?.name : chat.requester?.name) || 'Unknown';
        chatWindow.querySelector('.chat-name').textContent = chatName;
        chatWindow.querySelector('.avatar').textContent = chatName.slice(0, 2);


        const profileImage = chat.type === 'PRIVATE' ?
            (chat.requester?.uuid === currentUser ? chat.owner?.profileImage : chat.requester?.profileImage) : null;
        if(profileImage) {
            chatWindow.querySelector('.avatar').style.backgroundImage = `url("${profileImage}")`;
            chatWindow.querySelector('.avatar').style.backgroundSize = 'cover';
            chatWindow.querySelector('.avatar').style.backgroundPosition = 'center';
            chatWindow.querySelector('.avatar').textContent = ''; // 텍스트 제거
            console.log('Applied styles to avatar:', chatWindow.querySelector('.avatar').style.backgroundImage);
        }
        // 참가자 목록 렌더링 추가 (그룹 채팅에만)
        const optionsMenu = document.querySelector('.options-menu');
        // 기존 옵션 제거
        const existingBlockOption = optionsMenu.querySelector('.block-option');
        if (existingBlockOption) existingBlockOption.remove();
        const existingLeaveOption = optionsMenu.querySelector('.leave-option');
        if (existingLeaveOption) existingLeaveOption.remove();

        // 차단하기 옵션: 개인 채팅일 때만 추가
        if (chat.type === 'PRIVATE') {
            const blockOption = document.createElement('button');
            blockOption.className = 'block-option';
            blockOption.textContent = '차단하기';
            optionsMenu.appendChild(blockOption);
            blockOption.addEventListener('click', () => {
                if (confirm("정말로 이 사용자를 차단하시겠습니까?") && currentChatRoomId && stompClient?.connected) {
                    stompClient.send("/app/blockUser", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
                    resetChatWindow();
                }
            });
        }

        // 나가기 옵션: 그룹 채팅이면서 모임장이 아닌 경우에만 추가
        if (chat.type === 'GROUP' && chat.owner?.uuid !== currentUser) {
            const leaveOption = document.createElement('button');
            leaveOption.className = 'leave-option';
            leaveOption.textContent = '나가기';
            optionsMenu.appendChild(leaveOption);
            leaveOption.addEventListener('click', () => {
                if (confirm("정말로 이 채팅방을 나가시겠습니까?") && currentChatRoomId && stompClient?.connected) {
                    stompClient.send("/app/leaveChatRoom", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
                    resetChatWindow();
                }
            });
        }

        if (chat.type === 'GROUP') {
            document.querySelector(".chat-status").textContent = "";
            console.log('Processing group chat with participants:', chat.participants);
            renderParticipantsList(chat);
        } else {
            console.log('Processing private chat, removing user list button and sidebar');
            const existingButton = optionsMenu.querySelector('.user-list');
            if (existingButton) existingButton.remove();
            const participantsSidebar = chatWindow.querySelector('.participants-sidebar');
            if (participantsSidebar) participantsSidebar.remove();
        }
        markMessagesAsRead();
        updateNotificationToggle();
        checkOnlineStatus(chat.id); // 채팅방 열 때 상태 다시 확인
        const messageInput = document.querySelector('.message-input');
        const sendButton = document.querySelector('.send-button');
        updateChatInput(chat, messageInput, sendButton);
        saveChatState();
        updateChatUI();

        isChatOpening = false;
    }

    function renderMessage(item, position = 'append') {
        const messagesContainer = document.querySelector('.messages-container');
        if (!messagesContainer) {
            console.error('Messages container not found in DOM');
            return;
        }
        console.log(`Rendering message: ${item.id}, position: ${position}`);

        const lastMessage = position === 'append' ? messagesContainer.lastElementChild : messagesContainer.firstElementChild;
        const lastDate = lastMessage?.dataset.date;
        const currentDate = item.timestamp ? new Date(item.timestamp).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' }) : '';
        console.log(`Current date: ${currentDate}, Last date: ${lastDate}`);
        if (!lastDate || lastDate !== currentDate) {
            const dateElement = document.createElement('article');
            dateElement.className = 'date-notification';
            dateElement.dataset.date = currentDate;
            const time = document.createElement('time');
            time.className = 'date-text';
            time.textContent = currentDate;
            dateElement.appendChild(time);
            if (position === 'prepend') {
                messagesContainer.insertBefore(dateElement, messagesContainer.firstChild);
            } else {
                messagesContainer.appendChild(dateElement);
            }
            console.log('Date element added:', dateElement);
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
            console.log(`Message type: ${item.type}, isOwnMessage: ${isOwnMessage}, timeStr: ${timeStr}`);
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
                avatar.textContent = item.sender.name.slice(0, 2);
                element.appendChild(avatar);
                const profileImage = item.sender?.profileImage;
                if (profileImage){
                    avatar.style.backgroundImage = `url("${profileImage}")`;
                    avatar.style.backgroundSize = 'cover';
                    avatar.style.backgroundPosition = 'center';
                    avatar.textContent = ''; // 텍스트 제거
                    console.log('Applied styles to avatar:', avatar.style.backgroundImage);
                }
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
            console.log(`Message ${item.id} added to DOM:`, element);
        }, 0);
        messagesContainer.offsetHeight;
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
        const sendButton = document.querySelector('.send-button');
        const messageInput = document.querySelector('.message-input');
        if (sendButton) sendButton.addEventListener('click', sendMessage);
        if (messageInput) messageInput.addEventListener('keypress', e => e.key === 'Enter' && sendMessage());
        const backButton = document.querySelector('.back-button');
        if (backButton) backButton.addEventListener('click', resetChatWindow);
        const blockOption = document.querySelector('.block-option');
        if (blockOption) {
            blockOption.addEventListener('click', () => {
                if (confirm("정말로 이 사용자를 차단하시겠습니까?") && currentChatRoomId && stompClient?.connected) {
                    stompClient.send("/app/blockUser", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
                    resetChatWindow();
                }
            });
        }
        const leaveOption = document.querySelector('.leave-option');
        if (leaveOption) {
            leaveOption.addEventListener('click', () => {
                if (confirm("정말로 이 채팅방을 나가시겠습니까?") && currentChatRoomId && stompClient?.connected) {
                    stompClient.send("/app/leaveChatRoom", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
                    resetChatWindow();
                }
            });
        }
        const notificationToggle = document.querySelector('.notification-toggle');
        if (notificationToggle) {
            notificationToggle.addEventListener('click', () => {
                if (!currentChatRoomId || !stompClient?.connected) return;
                const chat = chatRoomsCache.find(c => c.id === currentChatRoomId);
                const action = (chat?.notificationEnabled !== false) ? 'OFF' : 'ON';
                stompClient.send("/app/toggleNotification", {}, JSON.stringify({ chatRoomId: currentChatRoomId, action }));
            });
        }
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
    }
    // 메시지 전송
    function sendMessage() {
        const messageInput = document.querySelector('.message-input');
        let content = messageInput.value.trim();
        if (content.length > MAX_MESSAGE_LENGTH || (Date.now() - lastSendTime < sendRateLimit)) {
            showError(content.length > MAX_MESSAGE_LENGTH ? `최대 ${MAX_MESSAGE_LENGTH}자까지 가능합니다.` : "메시지를 너무 빨리 보낼 수 없습니다.");
            return;
        }
        if (content && currentChatRoomId && stompClient?.connected) {
            content = content.replace(/[<>&"']/g, '');
            stompClient.send('/app/sendMessage', {}, JSON.stringify({ chatRoomId: currentChatRoomId, content }));
            lastSendTime = Date.now();
            messageInput.value = '';
        } else {
            showError("연결 상태가 올바르지 않습니다. 다시 시도해주세요.");
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
        refreshChatRooms();
        saveChatState();
        if (messageSubscription) {
            messageSubscription.unsubscribe();
            messageSubscription = null;
        }
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
        waitForChatRooms
    };
})();

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', async () => {
    chatApp.loadChatState();
    chatApp.connect();
    chatApp.setupEventListeners();
    // chatRoomsCache가 로드될 때까지 대기한 후 UI 업데이트
    await chatApp.waitForChatRooms();
    chatApp.updateChatUI();
    chatApp.updateTabUI();
});