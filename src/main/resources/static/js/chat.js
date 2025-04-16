// 최종 개선 사항 반영 버전 (디버깅 로그 제거)

// --- Constants ---
const CONSTANTS = {
    API_PREFIX: '/app',
    TOPIC_PREFIX: '/user',
    MAX_RETRIES: 5,
    MAX_MESSAGE_LENGTH: 1000,
    CONNECTION_TIMEOUT: 10000,
    SEND_RATE_LIMIT: 500, // 메시지 발송 간격 제한 (ms)
    CONNECT_RATE_LIMIT: 2000, // 재연결 시도 간격 제한 (ms)
    MARK_COOLDOWN: 1000, // 읽음 처리 요청 간격 (ms) - 현재 미사용
    PAGE_SIZE: 50, // 한 번에 불러올 메시지/채팅방 개수
    CHAT_TYPE: {
        PRIVATE: 'PRIVATE',
        GROUP: 'GROUP',
    },
    CHAT_STATUS: {
        PENDING: 'PENDING', // 승인 대기
        ACTIVE: 'ACTIVE',   // 활성 상태
        CLOSED: 'CLOSED',   // 종료됨
        BLOCKED: 'BLOCKED', // 차단됨
        APPROVED: 'APPROVE', // Action: 승인
        REJECTED: 'REJECT',   // Action: 거부
    },
    MESSAGE_TYPE: {
        SYSTEM: 'SYSTEM', // 시스템 메시지
        NORMAL: 'NORMAL', // 일반 메시지
    },
    SOCKET_URL: '/chat', // 웹소켓 접속 경로
    DEFAULT_TAB: 'PRIVATE', // 기본 활성 탭
    LOCAL_STORAGE_KEY: 'chatState', // 로컬 스토리지 키
    NOTIFICATION_ACTION: {
        ON: 'ON', // 알림 켜기
        OFF: 'OFF', // 알림 끄기
    }
};

// --- Utility Functions ---
const Utils = {
    // Debounce: 마지막 호출 후 일정 시간 동안 추가 호출이 없으면 함수 실행
    debounce: (func, delay) => {
        let timeoutId;
        return (...args) => {
            clearTimeout(timeoutId);
            timeoutId = setTimeout(() => func.apply(this, args), delay);
        };
    },
    // Throttle: 일정 시간 간격으로 최대 한 번만 함수 실행
    throttle: (func, limit) => {
        let inThrottle;
        let lastResult;
        return (...args) => {
            if (!inThrottle) {
                inThrottle = true;
                setTimeout(() => inThrottle = false, limit);
                lastResult = func.apply(this, args);
            }
            return lastResult;
        };
    },
    // HTML 특수 문자 이스케이프 (XSS 방지)
    escapeHTML: (str) => {
        if (!str) return '';
        const element = document.createElement('div');
        element.textContent = str;
        return element.innerHTML;
    },
    // 시간 포맷 (HH:MM AM/PM)
    formatTimestamp: (timestamp) => {
        if (!timestamp) return "";
        try {
            const date = new Date(timestamp);
            if (isNaN(date.getTime())) return ""; // 유효하지 않은 날짜 처리
            return date.toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit', hour12: true });
        } catch (e) {
            console.error("Error formatting timestamp:", e, timestamp);
            return "";
        }
    },
    // 날짜 포맷 (YYYY년 M월 D일)
    formatDate: (timestamp) => {
        if (!timestamp) return "";
        try {
            const date = new Date(timestamp);
            if (isNaN(date.getTime())) return "";
            return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' });
        } catch (e) {
            console.error("Error formatting date:", e, timestamp);
            return "";
        }
    },
    // 상대 시간 포맷 (방금 전, N분 전, N시간 전)
    formatRelativeTime: (timestamp) => {
        if (!timestamp) return "";
        try {
            const date = new Date(timestamp);
            if (isNaN(date.getTime())) return "";
            const minutesAgo = Math.floor((Date.now() - date.getTime()) / 60000);
            if (minutesAgo < 1) return '방금 전';
            if (minutesAgo < 60) return `${minutesAgo}분 전`;
            return `${Math.floor(minutesAgo / 60)}시간 전`;
        } catch (e) {
            console.error("Error formatting relative time:", e, timestamp);
            return "";
        }
    }
};

// --- DOM Element Cache ---
const DOMElements = {
    chatContainer: null, openChatButton: null, closeChatButton: null, messagesListContainer: null,
    personalChatContainer: null, chatList: null, groupTab: null, personalTab: null,
    groupUnreadCount: null, personalUnreadCount: null, loadingIndicator: null, chatHeader: null,
    chatName: null, chatStatus: null, personalAvatar: null,
    messagesContainer: null, messageInput: null, sendButton: null,
    backButton: null, chatOptions: null, optionsButton: null, optionsMenu: null,
    notificationToggle: null, notificationIcon: null, noticeSection: null, noticeView: null,
    noticeEmpty: null, noticePreview: null, noticeContent: null, noticeEditButton: null,
    noticeDeleteButton: null, noticeAddButton: null, noticeToggleButton: null, noticeModal: null,
    noticeModalTitle: null, noticeForm: null, noticeTextInput: null, submitNoticeButton: null,
    cancelNoticeButton: null, notificationContainer: null, notificationName: null,
    notificationMessage: null, notificationTimestamp: null, notificationAvatarContainer: null,
    errorContainer: null,
    userSearchArea: null, // 검색 영역 Div
    userSearchInput: null, // 검색 입력창
    userSearchButton: null, // 검색 버튼
    userSearchResults: null, // 검색 결과 Div
    chatButton: null,
    cancelChatButton: null,
    ChatTextInput: null,
    ChatModal: null,


    initialize() {
        const elements = [
            'chatContainer', 'openChat', 'closeChat', 'messagesList', 'chatList',
            'groupUnreadCount', 'personalUnreadCount', 'loadingIndicator', 'noticeSection',
            'noticeView', 'noticeEmpty', 'noticePreview', 'noticeContent', 'noticeModal',
            'modalTitle', 'noticeForm', 'noticeText', 'submitNotice', 'cancelNotice',
            'notificationContainer', 'notificationName', 'notificationMessage', 'avatarContainer', 'submitChat','cancelChat', 'ChatText', 'ChatModal'

        ];
        const selectors = {
            personalChatContainer: '.personal-chat', groupTab: '.tab-group', personalTab: '.tab-personal',
            chatHeader: '.personal-chat .chat-header', chatName: '.personal-chat .chat-name',
            chatStatus: '.personal-chat .chat-status', personalAvatar: '.personal-chat .avatar.personal',
            messagesContainer: '.messages-container', messageInput: '.message-input',
            sendButton: '.send-button', backButton: '.back-button', chatOptions: '.chat-options',
            optionsButton: '.options-button', optionsMenu: '.options-menu',
            notificationToggle: '.notification-toggle', notificationIcon: '.notification-icon',
            noticeEditButton: '.notice-edit', noticeDeleteButton: '.notice-delete',
            noticeAddButton: '.notice-add', noticeToggleButton: '.notice-toggle',
            notificationTimestamp: '#notificationContainer .timestamp-text',
            notificationAvatarContainer:"#avatarContainer",
            userSearchArea:'#userSearchArea',userSearchInput:'#user-search-input',
            userSearchButton:'#user-search-button', userSearchResults:'#user-search-results',
        };

        elements.forEach(id => {
            const key = id === 'modalTitle' ? 'noticeModalTitle' :
                id === 'noticeText' ? 'noticeTextInput' :
                    id === 'submitNotice' ? 'submitNoticeButton' :
                        id === 'cancelNotice' ? 'cancelNoticeButton' :
                            id === 'submitChat' ? 'chatButton' :        // 매핑 추가
                                id === 'cancelChat' ? 'cancelChatButton' : // 매핑 추가
                                    id === 'ChatText' ? 'ChatTextInput' :      // 매핑 추가
                                        id;
            try {
                this[key] = document.getElementById(id);
                if (!this[key] && !['loadingIndicator', 'chatContainer'].includes(key)) {
                    console.warn(`[DOM Cache] Element not found for ID: ${id}`);
                }
            } catch (e) {
                console.error(`[DOM Cache] Error finding element by ID: ${id}`, e);
            }
        });

        Object.keys(selectors).forEach(key => {
            try {
                this[key] = document.querySelector(selectors[key]);
                if (!this[key]) {
                    console.warn(`[DOM Cache] Element not found for selector: ${selectors[key]} (key: ${key})`);
                }
            } catch(e) {
                console.error(`[DOM Cache] Error finding element by selector: ${selectors[key]} (key: ${key})`, e);
            }
        });

        this.errorContainer = document.body;
        this.openChatButton = document.getElementById('openChat');
        if (!this.openChatButton) {
            console.error("[DEBUG] #openChat button not found during initialization!"); // <<< 로그 추가
        }
        this.closeChatButton = document.getElementById('closeChat');
        if (!this.closeChatButton) {
            console.error("[DEBUG] #closeChat button not found during initialization!"); // <<< 로그 추가
        }
        this.messagesListContainer = document.getElementById('messagesList');
        if (!this.messagesListContainer) {
            console.error("[DEBUG] #messagesList container not found during initialization!"); // <<< 로그 추가
        }
        console.log("DOMElements initialized.");
    }
};


// --- State Manager ---
const StateManager = {
    state: {
        isConnected: false,
        isChatOpen: false,
        isChatRoomOpen: false,
        isLoading: false,
        isChatRoomsLoaded: false,
        activeTab: CONSTANTS.DEFAULT_TAB,
        currentChatRoomId: null,
        currentUser: null,
        chatRoomsCache: [],
        renderedMessageIds: new Map(),
        notices: new Map(),
        offlineTimers: new Map(),
        isScrollable: true,
        isChatOpening: false,
        oldestPageLoaded: new Map(),
        isFetchingPrevious: new Map(),
    },

    getOldestPageLoaded(chatRoomId) {
        return this.state.oldestPageLoaded.get(Number(chatRoomId));
    },
    setOldestPageLoaded(chatRoomId, page) {
        this.state.oldestPageLoaded.set(Number(chatRoomId), page);
    },
    setFetchingPrevious(chatRoomId, isFetching) {
        this.state.isFetchingPrevious.set(Number(chatRoomId), isFetching);
    },
    isFetchingPrevious(chatRoomId) {
        return this.state.isFetchingPrevious.get(Number(chatRoomId)) ?? false;
    },
    clearPaginationState(chatRoomId) {
        const numId = Number(chatRoomId);
        this.state.oldestPageLoaded.delete(numId);
        this.state.isFetchingPrevious.delete(numId);
    },

    getState() {
        return this.state;
    },

    setState(newState) {
        let stateChanged = false;
        for (const key in newState) {
            if (Object.hasOwnProperty.call(newState, key) && this.state[key] !== newState[key]) {
                this.state[key] = newState[key];
                stateChanged = true;
            }
        }
        if (stateChanged) {
            UIManager.updateChatUI(this.state);
            this.saveChatState();
        }
    },

    setConnected(isConnected, user = null) {
        const newState = { isConnected, isLoading: false };
        if (isConnected && user) {
            newState.currentUser = user;
        } else {
            newState.currentUser = null;
            newState.isChatRoomsLoaded = false;
            this.clearAllOfflineTimers();
        }
        this.setState(newState);
    },

    setLoading(isLoading) {
        if (this.state.isLoading !== isLoading) {
            this.setState({ isLoading });
            UIManager.showLoading(isLoading);
        }
    },

    openChatList() {
        console.log("[DEBUG] StateManager.openChatList called. Setting state: isChatOpen=true, isChatRoomOpen=false"); // <<< 로그 추가
        this.setState({ isChatOpen: true, isChatRoomOpen: false });
    },

    closeChatApp() {
        console.log("[DEBUG] StateManager.closeChatApp called. Setting state: isChatOpen=false, isChatRoomOpen=false"); // <<< 로그 추가
        this.setState({ isChatOpen: false, isChatRoomOpen: false });
    },

    openChatRoom(chatRoomId, initialLastPage) {
        const numChatRoomId = Number(chatRoomId);
        if (!numChatRoomId) return;
        if (this.state.currentChatRoomId && this.state.currentChatRoomId !== numChatRoomId) {
            this.clearPaginationState(this.state.currentChatRoomId);
            ChatApp.clearMarkedMessages(this.state.currentChatRoomId);
        }
        this.setState({
            isChatOpen: false,
            isChatRoomOpen: true,
            currentChatRoomId: numChatRoomId
        });
        this.state.oldestPageLoaded.set(numChatRoomId, initialLastPage);
        this.state.isFetchingPrevious.set(numChatRoomId, false);
    },

    closeChatRoom() {
        const closedRoomId = this.state.currentChatRoomId;
        if (closedRoomId) {
            this.clearPaginationState(closedRoomId);
            ChatApp.clearMarkedMessages(closedRoomId);
        }
        this.setState({ isChatOpen: true, isChatRoomOpen: false, currentChatRoomId: null });
        UIManager.resetChatWindow(); // Close 후 UI 리셋
        // --- *** 추가: 채팅 목록 강제 렌더링 *** ---
        console.log("[DEBUG] Re-rendering chat list after closing chat room.");
        // getFilteredChatRooms()는 현재 activeTab 기준으로 필터링함
        UIManager.renderChatList(this.getFilteredChatRooms(), this.state.currentUser, false);
        this.calculateAndRenderUnreadCounts(); // 안읽은 개수도 갱신
        // --- *** 추가 끝 *** ---
        console.log("[DEBUG] StateManager.closeChatRoom finished");
    },

    switchTab(tab) {
        if (Object.values(CONSTANTS.CHAT_TYPE).includes(tab) && this.state.activeTab !== tab) {
            this.setState({ activeTab: tab });
            UIManager.updateTabUI(tab);
            UIManager.renderChatList(
                this.getFilteredChatRooms(),
                this.state.currentUser,
                false
            );
        }
    },

    updateChatRooms(rooms) {
        if (!Array.isArray(rooms)) {
            console.warn("Received invalid rooms data:", rooms);
            return;
        }
        const currentRoomId = this.state.currentChatRoomId;
        const isRoomOpen = this.state.isChatRoomOpen;
        const roomMap = new Map(rooms.map(room => [room.id, room]));

/*        const oldCache = this.state.chatRoomsCache; // 이전 캐시 참조 (상태 비교용)*/

        // 이전 캐시 상태 참조 (참여자 비교용)
        const previousChatData = isRoomOpen && currentRoomId ? this.findChatRoom(currentRoomId) : null;

        this.setState({ chatRoomsCache: rooms, isChatRoomsLoaded: true, isLoading: false });

        // 현재 열린 채팅방 상태 변경 확인 및 처리
        if (isRoomOpen && currentRoomId) {
            const currentChatData = roomMap.get(currentRoomId); // 업데이트된 새 데이터

            if (!currentChatData) { // 채팅방이 목록에서 사라진 경우
                console.warn(`Current chat room ${currentRoomId} removed from list, closing.`);
                this.closeChatRoom();
            } else { // 채팅방이 여전히 존재하면 세부 변경 확인
                // --- *** 참여자 목록 변경 시 전체 재렌더링으로 변경 *** ---
                const getParticipantUuids = (chat) => new Set(chat?.participants?.map(p => p.uuid) || []);
                const previousParticipantUuids = getParticipantUuids(previousChatData);
                const currentParticipantUuids = getParticipantUuids(currentChatData);

                if (previousParticipantUuids.size !== currentParticipantUuids.size ||
                    ![...previousParticipantUuids].every(uuid => currentParticipantUuids.has(uuid)))
                {
                    console.log(`Participant list changed for open chat ${currentRoomId}. Re-rendering participant list.`);
                    // <<< 수정: DOM 직접 조작 대신 전체 목록 다시 렌더링 >>>
                    UIManager.renderParticipantsList(currentChatData, this.state.currentUser);
                    // 온라인 상태 확인은 renderParticipantsList 내부 또는 여기서 호출 유지 가능
                    WebSocketManager.checkOnlineStatus(currentRoomId);
                }


/*                // 3. 채팅방 이름 등 다른 정보 변경 시 UI 업데이트 (선택적)
                if (previousChatData && previousChatData.name !== currentChatData.name) {
                    if(DOMElements.chatName) DOMElements.chatName.textContent = currentChatData.name;
                }*/
            }
        }

        if (!isRoomOpen || this.state.isChatOpen) {
            UIManager.renderChatList(this.getFilteredChatRooms(), this.state.currentUser, false);
        }
        this.calculateAndRenderUnreadCounts();

        rooms.forEach(chat => {
            if (chat.type === CONSTANTS.CHAT_TYPE.GROUP && !this.state.notices.has(chat.id)) {
                WebSocketManager.fetchNotice(chat.id);
            }
        });
    },

    addRenderedMessage(chatRoomId, messageId) {
        if (!chatRoomId || !messageId) return;
        const numChatRoomId = Number(chatRoomId);
        const messageSet = this.state.renderedMessageIds.get(numChatRoomId) || new Set();
        messageSet.add(messageId);
        this.state.renderedMessageIds.set(numChatRoomId, messageSet);
    },

    hasRenderedMessage(chatRoomId, messageId) {
        return this.state.renderedMessageIds.get(Number(chatRoomId))?.has(messageId) ?? false;
    },

    clearRenderedMessages(chatRoomId) {
        this.state.renderedMessageIds.delete(Number(chatRoomId));
    },

    updateChatRoomWithMessage(message) {
        const chat = this.findChatRoom(message.chatRoomId);
        if (!chat) return;
        chat.lastMessageSender = {
            uuid: message.sender?.uuid ?? null,
            name: message.sender?.name ?? null,
            lastMessage: message.content ?? null,
            lastMessageTime: message.timestamp
        };
        try {
            chat.lastMessageTime = message.timestamp ? new Date(message.timestamp).getTime() : 0;
        } catch (e) { chat.lastMessageTime = 0; }

        if (this.state.isChatOpen && !this.state.isChatRoomOpen) {
            UIManager.renderChatList(this.getFilteredChatRooms(), this.state.currentUser, false);
            this.calculateAndRenderUnreadCounts();
        }
    },

    updateUnreadCount(chatRoomId, unreadCount) {
        const chat = this.findChatRoom(chatRoomId);
        if (chat && chat.unreadCount !== unreadCount) {
            chat.unreadCount = unreadCount;
            if (this.state.isChatOpen && !this.state.isChatRoomOpen) {
                UIManager.renderChatList(this.getFilteredChatRooms(), this.state.currentUser, false);
            }
            this.calculateAndRenderUnreadCounts();
        }
    },

    updateNotificationSetting(chatRoomId, isEnabled) {
        const chat = this.findChatRoom(chatRoomId);
        if (chat) {
            chat.notificationEnabled = isEnabled;
            if (Number(chatRoomId) === this.state.currentChatRoomId && this.state.isChatRoomOpen) {
                UIManager.updateNotificationToggle(isEnabled);
            }
        }
    },

    removeChatRoom(chatRoomId) {
        const numChatRoomId = Number(chatRoomId);
        const currentOpenRoomId = this.state.currentChatRoomId;
        const newCache = this.state.chatRoomsCache.filter(c => c.id !== numChatRoomId);
        this.state.renderedMessageIds.delete(numChatRoomId);
        this.state.notices.delete(numChatRoomId);
        this.clearPaginationState(numChatRoomId);
        ChatApp.clearMarkedMessages(numChatRoomId);

        this.setState({ chatRoomsCache: newCache }); // Update cache before closing/rendering

        if (currentOpenRoomId === numChatRoomId) {
            this.closeChatRoom(); // This calls resetChatWindow inside
        } else if (this.state.isChatOpen) {
            UIManager.renderChatList(this.getFilteredChatRooms(), this.state.currentUser, false);
            this.calculateAndRenderUnreadCounts();
        }
    },

    updateNotice(chatRoomId, content, expanded) {
        const chat = this.findChatRoom(chatRoomId);
        if (!chat) return;
        const numChatRoomId = Number(chatRoomId);
        if (content === null || content === undefined) {
            this.state.notices.delete(numChatRoomId);
            chat.expanded = false;
        } else {
            const currentNotice = this.state.notices.get(numChatRoomId) || {};
            const finalExpanded = expanded !== undefined ? expanded : (currentNotice.expanded !== undefined ? currentNotice.expanded : (chat.expanded || false));
            this.state.notices.set(numChatRoomId, { content, expanded: finalExpanded });
            chat.expanded = finalExpanded;
        }
        if (numChatRoomId === this.state.currentChatRoomId && this.state.isChatRoomOpen) {
            UIManager.renderNotice(chat, this.state.notices.get(numChatRoomId), this.state.currentUser);
        }
    },

    updateNoticeExpansion(chatRoomId, isExpanded) {
        const numChatRoomId = Number(chatRoomId);
        const notice = this.state.notices.get(numChatRoomId);
        const chat = this.findChatRoom(numChatRoomId);
        if (notice && chat) {
            notice.expanded = isExpanded;
            chat.expanded = isExpanded;
            this.state.notices.set(numChatRoomId, notice);
            if (numChatRoomId === this.state.currentChatRoomId && this.state.isChatRoomOpen) {
                UIManager.renderNotice(chat, notice, this.state.currentUser);
            }
        } else {
            console.warn(`Notice or Chat not found for expansion update: ${numChatRoomId}`);
        }
    },

    updateOnlineStatus(uuid, lastOnlineTimestamp, isOnline) {
        if (this.state.offlineTimers.has(uuid)) {
            clearInterval(this.state.offlineTimers.get(uuid));
            this.state.offlineTimers.delete(uuid);
        }
        const currentChat = this.findChatRoom(this.state.currentChatRoomId);
        if (!currentChat || !this.state.isChatRoomOpen) return;
        if (currentChat.type === CONSTANTS.CHAT_TYPE.PRIVATE) {
            const otherUserUuid = currentChat.requester?.uuid === this.state.currentUser
                ? currentChat.owner?.uuid : currentChat.requester?.uuid;
            if (otherUserUuid === uuid) {
                UIManager.updatePrivateChatStatus(isOnline, lastOnlineTimestamp);
                if (!isOnline && lastOnlineTimestamp) {
                    const timer = setInterval(() => { UIManager.updatePrivateChatStatus(false, lastOnlineTimestamp); }, 60000);
                    this.state.offlineTimers.set(uuid, timer);
                }
            }
        } else if (currentChat.type === CONSTANTS.CHAT_TYPE.GROUP) {
            UIManager.updateGroupParticipantStatus(uuid, isOnline);
        }
    },

    clearAllOfflineTimers() {
        this.state.offlineTimers.forEach(timer => clearInterval(timer));
        this.state.offlineTimers.clear();
    },

    findChatRoom(chatRoomId) {
        const idToFind = Number(chatRoomId);
        if (isNaN(idToFind)) return undefined;
        return this.state.chatRoomsCache.find(c => c.id === idToFind);
    },

    getFilteredChatRooms() {
        const sortedCache = [...this.state.chatRoomsCache].sort((a, b) => {
            const timeA = a.lastMessageTime instanceof Date ? a.lastMessageTime.getTime() : (Number(a.lastMessageTime) || 0);
            const timeB = b.lastMessageTime instanceof Date ? b.lastMessageTime.getTime() : (Number(b.lastMessageTime) || 0);
            return timeB - timeA;
        });
        return sortedCache.filter(chat => chat.type === this.state.activeTab);
    },

    calculateAndRenderUnreadCounts() {
        let groupUnread = 0, personalUnread = 0;
        this.state.chatRoomsCache.forEach(chat => {
            const count = chat.unreadCount || 0;
            if (chat.type === CONSTANTS.CHAT_TYPE.GROUP) groupUnread += count;
            else personalUnread += count;
        });
        UIManager.updateUnreadCounts(groupUnread, personalUnread);
    },

    saveChatState() {
        try {
            const chatState = {
                isChatOpen: this.state.isChatOpen,
                isChatRoomOpen: this.state.isChatRoomOpen,
                currentChatRoomId: this.state.currentChatRoomId,
                activeTab: this.state.activeTab
            };
            localStorage.setItem(CONSTANTS.LOCAL_STORAGE_KEY, JSON.stringify(chatState));
        } catch (e) { console.error('Failed to save chat state:', e); }
    },

    loadChatState() {
        try {
            const savedState = localStorage.getItem(CONSTANTS.LOCAL_STORAGE_KEY);
            if (savedState) {
                const parsedState = JSON.parse(savedState);
                console.log("[DEBUG] Loaded chat state from localStorage:", JSON.stringify(parsedState));
                this.state.isChatOpen = parsedState.isChatOpen ?? false;
                this.state.isChatRoomOpen = parsedState.isChatRoomOpen ?? false;
                this.state.currentChatRoomId = parsedState.currentChatRoomId ?? null;
                this.state.activeTab = parsedState.activeTab ?? CONSTANTS.DEFAULT_TAB;
            } else {
                console.log("[DEBUG] No chat state found in localStorage.");
            }
        } catch (e) {
            console.error('Failed to load chat state:', e);
            this.state.isChatOpen = false;
            this.state.isChatRoomOpen = false;
            this.state.currentChatRoomId = null;
            this.state.activeTab = CONSTANTS.DEFAULT_TAB;
            localStorage.removeItem(CONSTANTS.LOCAL_STORAGE_KEY);
        }
    }
};


// --- WebSocket Manager ---
const WebSocketManager = {
    stompClient: null,
    retryCount: 0,
    lastConnectTime: 0,
    connectionTimeoutId: null,
    subscriptions: {}, // Map<destination, subscriptionObject>

    // Stomp 클라이언트 연결 시도
    connect() {
        const now = Date.now();
        const state = StateManager.getState();

        // 이미 연결되어 있거나, 너무 자주 재연결 시도하거나, 이미 연결 중이면 중단
        if (state.isConnected || now - this.lastConnectTime < CONSTANTS.CONNECT_RATE_LIMIT || state.isLoading) {
            console.log('Connection attempt skipped (connected, rate limited, or already connecting).');
            return;
        }

        StateManager.setLoading(true); // 연결 시작 시 로딩 상태
        this.lastConnectTime = now;

        try {
            // SockJS 소켓 생성 및 Stomp 클라이언트 초기화
            const socket = new SockJS(CONSTANTS.SOCKET_URL);
            this.stompClient = Stomp.over(socket);
            this.stompClient.heartbeat.outgoing = 5000; // 5초마다 클라이언트 -> 서버 핑
            this.stompClient.heartbeat.incoming = 5000; // 5초마다 서버 -> 클라이언트 핑 기대
            this.stompClient.debug = null; // Stomp 디버그 로그 비활성화 (필요시 주석 해제)

            // 이전 타임아웃 제거 (중복 방지)
            if (this.connectionTimeoutId) clearTimeout(this.connectionTimeoutId);

            // 연결 타임아웃 설정
            this.connectionTimeoutId = setTimeout(() => {
                if (!StateManager.getState().isConnected) {
                    console.error("Connection timed out.");
                    UIManager.showError("서버 연결 시간이 초과되었습니다.");
                    // 연결 오류 처리 (재시도 로직 포함)
                    this.handleConnectionError(new Error("Connection Timeout")); // Error 객체 전달
                }
            }, CONSTANTS.CONNECTION_TIMEOUT);

            // Stomp 연결 시도
            this.stompClient.connect(
                {}, // Headers (필요시 인증 토큰 등 추가)
                this.onConnect.bind(this), // 성공 콜백
                this.handleConnectionError.bind(this) // 실패 콜백
            );

            // 웹소켓 연결이 끊어졌을 때 처리 (네트워크 문제 등)
            this.stompClient.onWebSocketClose = () => {
                console.warn("WebSocket closed unexpectedly.");
                this.clearSubscriptions(); // 구독 정리
                StateManager.setConnected(false); // 연결 상태 업데이트
                StateManager.clearAllOfflineTimers(); // 오프라인 타이머 정리
                // 재연결 시도
                UIManager.showError("연결이 끊겼습니다. 1초 후 재연결을 시도합니다.");
                setTimeout(() => this.connect(), 1000); // 즉시 재연결 시도 (간격 조절 가능)
            };

        } catch (error) { // SockJS 생성 오류 등 초기화 단계 오류
            console.error("SockJS or Stomp initialization failed:", error);
            UIManager.showError("채팅 연결 초기화에 실패했습니다.");
            this.handleConnectionError(error); // 연결 오류로 간주하고 처리
        }
    },

    // Stomp 연결 성공 시 콜백
    onConnect(frame) {
        clearTimeout(this.connectionTimeoutId); // 연결 성공 시 타임아웃 제거
        this.connectionTimeoutId = null;
        this.retryCount = 0; // 연결 성공 시 재시도 횟수 초기화

        // 서버로부터 사용자 UUID 받기 (frame 헤더 확인 필요)
        const currentUser = frame.headers['user-name'];
        if (!currentUser) {
            console.error("User not identified from server frame headers!");
            StateManager.setLoading(false);
            UIManager.showError("사용자 인증 정보를 확인할 수 없습니다.");
            // 로그인 페이지로 리다이렉트 등 추가 처리 필요
            this.disconnect(); // 연결 시도 중단
            return;
        }

        StateManager.setConnected(true, currentUser); // 연결 상태 및 사용자 정보 업데이트
        console.log('Connected to WebSocket server. User:', currentUser);

        // 필요한 토픽 구독 시작
        this.subscribeToTopics(currentUser);
        // 초기 상태 요청 (온라인 사용자 등)
        this.sendInitialStatus();
        // 채팅방 목록 요청
        this.refreshChatRooms();
    },

    // Stomp 연결 실패 또는 오류 발생 시 콜백
    handleConnectionError(error) {
        console.error('STOMP Connection Error/Failure:', error);
        clearTimeout(this.connectionTimeoutId); // 타임아웃 제거
        this.connectionTimeoutId = null;
        this.clearSubscriptions(); // 구독 정리
        StateManager.setConnected(false); // 연결 및 로딩 상태 업데이트
        StateManager.clearAllOfflineTimers(); // 오프라인 타이머 정리

        // 재연결 시도 (최대 횟수까지 지수 백오프 적용 가능)
        if (this.retryCount < CONSTANTS.MAX_RETRIES) {
            this.retryCount++;
            const delay = Math.pow(2, this.retryCount) * 1000; // 예: 2초, 4초, 8초...
            console.log(`Connection failed. Retrying in ${delay / 1000} seconds... (Attempt ${this.retryCount}/${CONSTANTS.MAX_RETRIES})`);
            setTimeout(() => this.connect(), delay);
        } else {
            console.error(`Max retries (${CONSTANTS.MAX_RETRIES}) reached. Could not connect to chat server.`);
            UIManager.showError("채팅 서버에 연결할 수 없습니다. 페이지를 새로고침하거나 나중에 다시 시도하세요.");
            // 영구적 오류 UI 표시 등 추가 처리
        }
    },

    // 현재 연결 상태 반환
    isConnected() {
        // stompClient 존재 여부 및 내부 connected 상태 확인
        return StateManager.getState().isConnected && this.stompClient?.connected;
    },

    // 웹소켓 연결 종료 (사용자 로그아웃, 페이지 이동 시 호출 고려)
    disconnect() {
        if (this.stompClient) {
            this.clearSubscriptions(); // 구독 먼저 해제
            try {
                this.stompClient.disconnect(() => {
                    console.log("Disconnected gracefully.");
                    // 상태 업데이트는 onWebSocketClose 핸들러에서도 처리될 수 있으므로 중복 호출 주의
                    // StateManager.setConnected(false);
                    // StateManager.clearAllOfflineTimers();
                });
            } catch (e) {
                console.error("Error during disconnect:", e);
            } finally {
                this.stompClient = null; // 클라이언트 참조 제거
                // 상태 강제 업데이트 (disconnect 콜백이 비동기이므로)
                if (StateManager.getState().isConnected) {
                    StateManager.setConnected(false);
                }
            }
        }
    },

    // STOMP 메시지 전송 (내부 헬퍼 함수)
    send(destination, headers = {}, body = "") {
        if (!this.isConnected()) {
            console.error("Cannot send message, not connected.", { destination });
            UIManager.showError("서버와 연결되지 않았습니다.");
            // 연결 재시도? 아니면 그냥 실패 처리?
            // this.connect(); // 재연결 시도 옵션
            return false; // 전송 실패
        }
        try {
            // body가 객체면 JSON 문자열로 변환 (일관성 위해 호출부에서 처리 권장)
            const messageBody = typeof body === 'object' ? JSON.stringify(body) : body;
            this.stompClient.send(destination, headers, messageBody);
            // console.log(`Sent to ${destination}:`, messageBody); // 필요시 전송 로그
            return true; // 전송 성공
        } catch (error) {
            console.error("STOMP send error:", error, { destination, body });
            UIManager.showError("메시지 전송 중 오류가 발생했습니다.");
            return false; // 전송 실패
        }
    },

    // STOMP 토픽 구독 (내부 헬퍼 함수)
    subscribe(destination, callback) {
        if (!this.isConnected()) {
            console.error("Cannot subscribe, not connected.", { destination });
            return null;
        }
        // 이미 구독 중인 경우, 기존 구독 해제 후 새로 구독 (중복 방지)
        if (this.subscriptions[destination]) {
            console.warn("Attempting to re-subscribe to", destination, ". Unsubscribing previous one.");
            this.unsubscribe(destination);
        }
        try {
            // 구독 실행 및 콜백 함수 등록
            const subscription = this.stompClient.subscribe(destination, (message) => {
                // 수신된 메시지 처리 콜백
                try {
                    callback(message);
                } catch (e) {
                    console.error(`Error processing message on ${destination}:`, e, message);
                    // 개별 메시지 처리 오류가 전체 앱에 영향 주지 않도록 방어
                }
            });
            this.subscriptions[destination] = subscription; // 구독 정보 저장
            console.log("Subscribed to:", destination);
            return subscription; // 구독 객체 반환
        } catch (error) { // 구독 자체의 실패 (권한 등)
            console.error("STOMP subscribe error:", error, { destination });
            UIManager.showError("토픽 구독 중 오류가 발생했습니다: " + destination);
            return null;
        }
    },

    // 특정 토픽 구독 해지 (내부 헬퍼 함수)
    unsubscribe(destination) {
        const subscription = this.subscriptions[destination];
        if (subscription) {
            try {
                subscription.unsubscribe();
                // console.log("Unsubscribed from:", destination); // 성공 로그 (필요시 주석 해제)
            } catch (e) {
                console.error("Error unsubscribing from", destination, e);
            } finally {
                delete this.subscriptions[destination]; // 구독 정보 맵에서 제거
            }
        } else {
            // console.warn("Attempted to unsubscribe from non-existent subscription:", destination);
        }
    },

    // 모든 활성 구독 해지 (연결 종료 시 등)
    clearSubscriptions() {
        console.log("Clearing all STOMP subscriptions.");
        Object.keys(this.subscriptions).forEach(dest => {
            this.unsubscribe(dest); // 개별 구독 해지 호출
        });
        // 맵 자체를 비우기 (unsubscribe 내부에서 delete 하지만 확인 차원)
        this.subscriptions = {};
    },

    // 필요한 모든 토픽 구독 설정
    subscribeToTopics(currentUser) {
        if (!currentUser) return;
        // 사용자별 토픽 주소 생성 헬퍼
        const userTopic = (path) => `${CONSTANTS.TOPIC_PREFIX}/${currentUser}/topic/${path}`;
        // 안전한 JSON 파싱 헬퍼
        const safeJsonParse = (body) => { try { return JSON.parse(body); } catch (e) { console.error("JSON Parse Error:", e, body); return null; } };

        // 각 토픽 구독 (메시지 처리 콜백 내에서 StateManager 또는 ChatApp 호출)
        this.subscribe(userTopic('chatrooms'), m => { const d = safeJsonParse(m.body); if(d) StateManager.updateChatRooms(d); });
        this.subscribe(userTopic('messages'), m => { const i = safeJsonParse(m.body); if(i) { const ms = Array.isArray(i)?i:(i?[i]:[]); ms.forEach(item => ChatApp.handleIncomingMessage(item)); } });
        this.subscribe(userTopic('errors'), m => UIManager.showError(`서버 오류: ${m.body || '알 수 없는 오류'}`));
        this.subscribe(userTopic('readUpdate'), m => { const u = safeJsonParse(m.body); if(u) StateManager.updateUnreadCount(u.chatRoomId, u.unreadCount); });
        this.subscribe(userTopic('notifications'), m => { const n = safeJsonParse(m.body); if(n) ChatApp.handleIncomingNotification(n); });
        this.subscribe(userTopic('notificationUpdate'), m => { const u = safeJsonParse(m.body); if(u) StateManager.updateNotificationSetting(u.chatRoomId, u.notificationEnabled); });
        this.subscribe(userTopic('onlineStatus'), m => { const s = safeJsonParse(m.body); if(s) StateManager.updateOnlineStatus(s.uuid, s.lastOnline, s.isOnline); });
        this.subscribe(userTopic('notice'), m => { const n = safeJsonParse(m.body); if(n) StateManager.updateNotice(n.chatRoomId, n.content, n.expanded); });
        this.subscribe(userTopic('noticeState'), m => { const u = safeJsonParse(m.body); if(u) StateManager.updateNoticeExpansion(u.chatRoomId, u.expanded); });

        // 주의: getMessageCount, fetchMessagesForChat 등 특정 요청/응답은 임시 구독 사용
    },

    // --- Action Methods ---

    sendInitialStatus() {
        this.send(`${CONSTANTS.API_PREFIX}/initialStatus`, {}, "{}");
    },

    refreshChatRooms() {
        const user = StateManager.getState().currentUser;
        if (user) {
            console.log('Requesting chat rooms refresh for user:', user);
            this.send(`${CONSTANTS.API_PREFIX}/refreshChatRooms`, {}, JSON.stringify({ uuid: user }));
        } else {
            console.warn("Cannot refresh chat rooms, user not logged in.");
        }
    },

    // --- Request/Response Methods (using specific reply topics) ---

    // 특정 채팅방 메시지 목록 요청 (페이지별)
    fetchMessagesForChat(chatId, page) {
        return new Promise((resolve, reject) => {
            const user = StateManager.getState().currentUser;
            if (!chatId || !user || !this.isConnected()) {
                return reject(new Error('Cannot fetch messages: Chat ID, user, or connection missing.'));
            }
            const numChatId = Number(chatId);
            const numPage = Number(page);
            const tempTopic = `${CONSTANTS.TOPIC_PREFIX}/${user}/topic/messages/${numChatId}/${numPage}`;

            let tempSubscription = null;
            const timeoutDuration = 10000; // 타임아웃 10초
            const timeoutId = setTimeout(() => {
                if (tempSubscription) {
                    this.unsubscribe(tempTopic);
                }
                reject(new Error(`Workspace messages timed out for page ${numPage}`)); // 오타 수정됨
            }, timeoutDuration);

            const safeJsonParse = (body) => { try { return JSON.parse(body); } catch (e) { console.error("JSON Parse Error:", e, body); return null; } };

            tempSubscription = this.subscribe(tempTopic, message => {
                clearTimeout(timeoutId);
                this.unsubscribe(tempTopic);
                const items = safeJsonParse(message.body);
                if (items !== null) {
                    const messages = Array.isArray(items) ? items : (items ? [items] : []);
                    console.log(`Received ${messages.length} messages for page ${numPage}`);
                    resolve(messages);
                } else {
                    reject(new Error("Failed to parse fetched messages"));
                }
            });

            if (!tempSubscription) {
                clearTimeout(timeoutId);
                return reject(new Error("Failed to subscribe for message fetch response"));
            }

            console.log(`Requesting messages for chat ${numChatId}, page ${numPage}, replyTo: ${tempTopic}`);
            const sent = this.send(
                `${CONSTANTS.API_PREFIX}/getMessages`, {},
                JSON.stringify({ id: numChatId, page: numPage, size: CONSTANTS.PAGE_SIZE, replyTo: tempTopic })
            );

            if (!sent) {
                clearTimeout(timeoutId);
                this.unsubscribe(tempTopic);
                reject(new Error("Failed to send fetch messages request"));
            }
        });
    },

    // 특정 채팅방 메시지 총 개수 요청
    getMessageCount(chatId) {
        return new Promise((resolve, reject) => {
            const user = StateManager.getState().currentUser;
            if (!chatId || !user || !this.isConnected()) {
                return reject(new Error('Cannot get message count: Chat ID, user, or connection missing.'));
            }
            const numChatId = Number(chatId);
            const replyTopic = `${CONSTANTS.TOPIC_PREFIX}/${user}/topic/messageCount/${numChatId}`;

            let tempSubscription = null;
            const timeoutDuration = 15000; // 타임아웃 15초
            const timeoutId = setTimeout(() => {
                if (tempSubscription) this.unsubscribe(replyTopic);
                reject(new Error('Get message count request timed out'));
            }, timeoutDuration);

            const safeJsonParse = (body) => { try { return JSON.parse(body); } catch (e) { console.error("JSON Parse Error:", e, body); return null; } };

            tempSubscription = this.subscribe(replyTopic, message => {
                clearTimeout(timeoutId);
                this.unsubscribe(replyTopic);
                const countData = safeJsonParse(message.body);
                if (countData && countData.chatId === numChatId && typeof countData.count === 'number') {
                    resolve(countData.count);
                } else {
                    console.error("Invalid message count response format:", countData);
                    reject(new Error('Invalid message count response format'));
                }
            });

            if (!tempSubscription) {
                clearTimeout(timeoutId);
                return reject(new Error('Failed to create temporary subscription for message count'));
            }

            console.log(`Requesting message count for chat ${numChatId}, replyTo: ${replyTopic}`);
            const sent = this.send(`${CONSTANTS.API_PREFIX}/getMessageCount`, {}, JSON.stringify({ id: numChatId, replyTo: replyTopic }));
            if (!sent) {
                clearTimeout(timeoutId);
                this.unsubscribe(replyTopic);
                reject(new Error('Failed to send getMessageCount request'));
            }
        });
    },

    // --- 일반 Action Methods ---
    sendMessage(chatRoomId, content) {
        if (!chatRoomId || !content) return false;
        const cleanContent = Utils.escapeHTML(content.trim());
        if (!cleanContent) return false;
        if (cleanContent.length > CONSTANTS.MAX_MESSAGE_LENGTH) {
            UIManager.showError(`최대 ${CONSTANTS.MAX_MESSAGE_LENGTH}자까지 입력 가능합니다.`);
            return false;
        }
        return this.send(`${CONSTANTS.API_PREFIX}/sendMessage`, {}, JSON.stringify({
            chatRoomId: Number(chatRoomId),
            content: cleanContent
        }));
    },

    markMessageAsRead(chatRoomId, messageId) {
        if (!chatRoomId || !messageId) return false;
        // console.log('Marking message as read:', { chatRoomId, messageId });
        return this.send(`${CONSTANTS.API_PREFIX}/markMessageAsRead`, {}, JSON.stringify({
            chatRoomId: Number(chatRoomId),
            messageId: messageId
        }));
    },

    markAllMessagesAsRead(chatRoomId) {
        if (!chatRoomId) return false;
        console.log('Marking all messages as read for chatRoomId:', chatRoomId);
        return this.send(`${CONSTANTS.API_PREFIX}/markMessagesAsRead`, {}, JSON.stringify({
            chatRoomId: Number(chatRoomId)
        }));
    },

    handleChatRequest(chatRoomId, action) {
        if (!chatRoomId || !action) return false;
        console.log(`Handling chat request for ${chatRoomId}: ${action}`);
        return this.send(`${CONSTANTS.API_PREFIX}/handleChatRequest`, {}, JSON.stringify({
            chatRoomId: Number(chatRoomId),
            action: action
        }));
    },

    toggleNotification(chatRoomId, turnOn) {
        if (!chatRoomId) return false;
        const action = turnOn ? CONSTANTS.NOTIFICATION_ACTION.ON : CONSTANTS.NOTIFICATION_ACTION.OFF;
        console.log(`Toggling notifications ${action} for chat ${chatRoomId}`);
        return this.send(`${CONSTANTS.API_PREFIX}/toggleNotification`, {}, JSON.stringify({
            chatRoomId: Number(chatRoomId),
            action: action
        }));
    },

    blockUser(chatRoomId) {
        if (!chatRoomId) return false;
        console.log(`Blocking user associated with chat ${chatRoomId}`);
        return this.send(`${CONSTANTS.API_PREFIX}/blockUser`, {}, JSON.stringify({ chatRoomId: Number(chatRoomId) }));
    },

    leaveChatRoom(chatRoomId) {
        if (!chatRoomId) return false;
        console.log(`Leaving chat room ${chatRoomId}`);
        return this.send(`${CONSTANTS.API_PREFIX}/leaveChatRoom`, {}, JSON.stringify({ chatRoomId: Number(chatRoomId) }));
    },

    checkOnlineStatus(chatRoomId) {
        if (!chatRoomId) return false;
        // console.log(`Requesting online status for chat ${chatRoomId}`);
        return this.send(`${CONSTANTS.API_PREFIX}/onlineStatus`, {}, JSON.stringify({ chatRoomId: Number(chatRoomId) }));
    },

    // --- Notice Methods ---
    fetchNotice(chatRoomId) {
        if (!chatRoomId) return false;
        console.log(`Workspaceing notice for chat ${chatRoomId}`);
        return this.send(`${CONSTANTS.API_PREFIX}/getNotice`, {}, JSON.stringify({ chatRoomId: Number(chatRoomId) }));
    },

    createNotice(chatRoomId, content) {
        if (!chatRoomId || !content) return false;
        console.log(`Creating notice for chat ${chatRoomId}`);
        return this.send(`${CONSTANTS.API_PREFIX}/createNotice`, {}, JSON.stringify({ chatRoomId: Number(chatRoomId), content }));
    },

    updateNotice(chatRoomId, content) {
        if (!chatRoomId || !content) return false;
        console.log(`Updating notice for chat ${chatRoomId}`);
        return this.send(`${CONSTANTS.API_PREFIX}/updateNotice`, {}, JSON.stringify({ chatRoomId: Number(chatRoomId), content }));
    },

    deleteNotice(chatRoomId) {
        if (!chatRoomId) return false;
        console.log(`Deleting notice for chat ${chatRoomId}`);
        return this.send(`${CONSTANTS.API_PREFIX}/deleteNotice`, {}, JSON.stringify({ chatRoomId: Number(chatRoomId) }));
    },

    toggleNoticeState(chatRoomId, isExpanded) {
        if (!chatRoomId) return false;
        console.log(`Toggling notice expansion to ${isExpanded} for chat ${chatRoomId}`);
        return this.send(`${CONSTANTS.API_PREFIX}/toggleNoticeState`, {}, JSON.stringify({
            chatRoomId: Number(chatRoomId),
            expanded: isExpanded
        }));
    },
};

// --- UI Manager ---
const UIManager = {
    lastRenderedDate: null,
    lastMessageSenderId: null,
    intersectionObserver: null, // Observer 인스턴스 저장용
    noticeModalControls: null, // 모달 제어 함수 저장용
    chatModalControls: null, // 모달 제어 함수 저장용
    // --- Main UI State ---
    updateChatUI(state) {
        const { isChatOpen, isChatRoomOpen } = state;
        console.log(`[DEBUG] updateChatUI called with state: isChatOpen=${isChatOpen}, isChatRoomOpen=${isChatRoomOpen}`); // <<< 로그 추가
        const messagesList = DOMElements.messagesListContainer;
        const personalChat = DOMElements.personalChatContainer;
        const openButton = DOMElements.openChatButton;
        const closeButton = DOMElements.closeChatButton;
        // Log element references just before use
        console.log("[DEBUG] updateChatUI - Element Check:", { messagesList, personalChat, openButton, closeButton }); // <<< 로그 추가
        if (!messagesList || !personalChat || !openButton || !closeButton) {
            console.warn("UI update skipped: one or more main layout elements not found.");
            return;
        }

        personalChat.classList.toggle('visible', isChatRoomOpen);
        messagesList.classList.toggle('visible', isChatOpen && !isChatRoomOpen);
        openButton.classList.toggle('hidden', isChatOpen || isChatRoomOpen);
        // closeButton 로직 수정: 채팅이 열려있거나(목록 또는 채팅방) 닫혀있지 않을 때(=열려 있을 때) 보이도록
        closeButton.classList.toggle('hidden', !(isChatOpen || isChatRoomOpen));

        document.body.classList.toggle('chat-room-open', isChatRoomOpen);
        document.body.classList.toggle('chat-list-open', isChatOpen && !isChatRoomOpen);
        document.body.classList.toggle('chat-closed', !isChatOpen && !isChatRoomOpen);
    },

    updateTabUI(activeTab) {
        const isPersonal = activeTab === CONSTANTS.CHAT_TYPE.PRIVATE;
        DOMElements.groupTab?.classList.toggle('active', !isPersonal);
        DOMElements.personalTab?.classList.toggle('active', isPersonal);
        // --- 검색 영역 표시/숨김 추가 ---
        if (DOMElements.userSearchArea) {
            DOMElements.userSearchArea.style.display = isPersonal ? 'block' : 'none';
        }
        // 탭 전환 시 이전 검색 결과 지우기 (선택적)
        if (DOMElements.userSearchResults) {
            DOMElements.userSearchResults.innerHTML = '';
        }
        if (DOMElements.userSearchInput) {
            DOMElements.userSearchInput.value = '';
        }
        // --- 추가 끝 ---
    },

    showLoading(isLoading) {
        if (DOMElements.loadingIndicator) {
            DOMElements.loadingIndicator.style.display = isLoading ? 'block' : 'none';
        }
        const input = DOMElements.messageInput;
        const button = DOMElements.sendButton;

        if (isLoading) {
            if (input) input.disabled = true;
            if (button) button.disabled = true;
        } else {
            // 로딩 끝나면 현재 채팅방 상태 기준으로 최종 상태 결정
            const currentChat = StateManager.findChatRoom(StateManager.getState().currentChatRoomId);
            this.updateChatInputState(currentChat); // updateChatInputState가 처리
        }
    },

    // --- Chat List ---
    renderChatList(chatRooms, currentUser, isLoading) {
        const listElement = DOMElements.chatList;
        if (!listElement) {
            console.error("Chat list element (#chatList) not found.");
            return;
        }
        listElement.innerHTML = ''; // 기존 목록 삭제

        if (isLoading && !StateManager.getState().isChatRoomsLoaded) {
            listElement.innerHTML = '<p class="loading-text">채팅 목록을 불러오는 중...</p>';
            return;
        }
        if (!chatRooms || chatRooms.length === 0) {
            listElement.innerHTML = '<p class="empty-text">채팅방이 없습니다.</p>';
            return;
        }

        const fragment = document.createDocumentFragment();
        chatRooms.forEach(chat => {
            const item = this.createChatItemElement(chat, currentUser);
            fragment.appendChild(item);
        });
        listElement.appendChild(fragment);
    },

    createChatItemElement(chat, currentUser) {
        const item = document.createElement('article');
        const isRequest = chat.status === CONSTANTS.CHAT_STATUS.PENDING;
        const isRequester = chat.requester?.uuid === currentUser;
        const isOwner = chat.owner?.uuid === currentUser;
        const isClosed = chat.status === CONSTANTS.CHAT_STATUS.CLOSED || chat.status === CONSTANTS.CHAT_STATUS.BLOCKED;

        item.className = `chat-item ${isRequest ? 'request-item' : ''} ${isClosed ? 'closed-item' : ''}`;
        item.dataset.chatId = chat.id;

        const otherUser = isRequester ? chat.owner : chat.requester;
        const chatName = chat.type === CONSTANTS.CHAT_TYPE.GROUP
            ? (chat.name || 'Unnamed Group')
            : (otherUser?.name || 'Unknown User');
        const avatarImage = chat.type === CONSTANTS.CHAT_TYPE.GROUP ? chat.clubImage : otherUser?.profileImage;

        const avatarContainer = document.createElement('div');
        avatarContainer.className = 'chat-avatar';
        avatarContainer.innerHTML = this.createAvatarHtml(avatarImage, chatName, chat.type === CONSTANTS.CHAT_TYPE.GROUP);

        const contentContainer = document.createElement('div');
        contentContainer.className = 'chat-content';

        const header = document.createElement('div');
        header.className = 'chat-header';

        const titleGroup = document.createElement('div');
        titleGroup.className = 'chat-title-group';
        const nameH3 = document.createElement('h3');
        nameH3.className = 'chat-name';
        nameH3.textContent = chatName;
        titleGroup.appendChild(nameH3);

        if (chat.unreadCount > 0) {
            const unreadSpan = document.createElement('span');
            unreadSpan.className = 'unread-count';
            unreadSpan.textContent = chat.unreadCount;
            titleGroup.appendChild(unreadSpan);
        }
        header.appendChild(titleGroup);

        const meta = document.createElement('div');
        meta.className = 'chat-meta';
        const timeSpan = document.createElement('span');
        timeSpan.className = 'chat-time';
        const lastMsgDate = chat.lastMessageTime ? new Date(chat.lastMessageTime) : null;
        if (lastMsgDate && !isNaN(lastMsgDate.getTime())) {
            const today = new Date();
            timeSpan.textContent = lastMsgDate.toDateString() === today.toDateString()
                ? Utils.formatTimestamp(chat.lastMessageTime)
                : lastMsgDate.toLocaleDateString('ko-KR', { month: 'numeric', day: 'numeric' });
        }
        meta.appendChild(timeSpan);
        header.appendChild(meta);
        contentContainer.appendChild(header);

        const previewP = document.createElement('p');
        previewP.className = 'chat-preview';
        let previewText = '대화를 시작해보세요.';
        if (isRequest) {
            previewText = isRequester ? '승인 대기중' : `요청: ${Utils.escapeHTML(chat.requestReason || 'N/A')}`;
        } else if (chat.lastMessageSender?.lastMessage) {
            const senderName = chat.lastMessageSender.name ? `${chat.lastMessageSender.name}: ` : '';
            previewText = `${senderName}${Utils.escapeHTML(chat.lastMessageSender.lastMessage)}`;
        }
        previewP.textContent = previewText;
        contentContainer.appendChild(previewP);

        if (isRequest && isOwner && !isRequester) {
            const actionsDiv = document.createElement('div');
            actionsDiv.className = 'request-actions';
            actionsDiv.innerHTML = `
                <button class="action-button approve" data-action="${CONSTANTS.CHAT_STATUS.APPROVED}">승인</button>
                <button class="action-button reject" data-action="${CONSTANTS.CHAT_STATUS.REJECTED}">거부</button>
                <button class="action-button block" data-action="${CONSTANTS.CHAT_STATUS.BLOCKED}">차단</button>
            `;
            contentContainer.appendChild(actionsDiv);
        }

        item.appendChild(avatarContainer);
        item.appendChild(contentContainer);

        return item;
    },

    createAvatarHtml(imageUrl, name, isGroup = false) {
        const initials = name ? name.slice(0, 2).toUpperCase() : '?';
        const hasImage = !!imageUrl;
        const safeImageUrl = Utils.escapeHTML(isGroup && imageUrl ? `/upload/${imageUrl}` : imageUrl || "");

        // --- 로그 추가 ---
        console.log(`[DEBUG] createAvatarHtml called. isGroup=${isGroup}, imageUrl=${imageUrl}, safeImageUrl=${safeImageUrl}`); // 로그 추가
        // --- 로그 추가 끝 ---

        const imgTag = hasImage
            ? `<img class="avatar-image" src="${safeImageUrl}" loading="lazy" style="display: block;" onerror="this.style.display='none'; this.parentElement.classList.add('has-text'); this.parentElement.classList.remove('has-image'); this.nextElementSibling.style.display='block';" />`
            : `<img class="avatar-image" src="" alt="" style="display: none;" />`;
        const textTag = `<span class="avatar-text" style="display: ${hasImage ? 'none' : 'block'};">${Utils.escapeHTML(initials)}</span>`;
        return `
             <div class="avatar ${isGroup ? 'group' : ''} ${hasImage ? 'has-image' : 'has-text'}">
                 ${imgTag}
                 ${textTag}
             </div>`;
    },


    updateUnreadCounts(groupUnread, personalUnread) {
        const groupEl = DOMElements.groupUnreadCount;
        const personalEl = DOMElements.personalUnreadCount;
        if (groupEl) {
            groupEl.textContent = groupUnread > 0 ? groupUnread : '';
            groupEl.style.display = groupUnread > 0 ? 'inline-block' : 'none';
        }
        if (personalEl) {
            personalEl.textContent = personalUnread > 0 ? personalUnread : '';
            personalEl.style.display = personalUnread > 0 ? 'inline-block' : 'none';
        }
    },

    // --- Personal Chat Window ---
    async openPersonalChat(chat, currentUser) {
        const chatWindow = DOMElements.personalChatContainer;
        if (!chat || !chat.id || !chatWindow) {
            console.error("[DEBUG] Invalid chat data or chat window element not found.");
            this.showError("채팅방을 여는 데 필요한 정보가 부족합니다.");
            StateManager.setState({ isChatOpening: false }); // Reset flag if early exit
            return;
        }

        StateManager.setLoading(true);
        let initialPage = 0;
        let totalMessages = 0;
        let chatOpenError = null;

        try {
            // 1. 메시지 개수 가져오기
            try {
                totalMessages = await WebSocketManager.getMessageCount(chat.id);
            } catch (countError) {
                console.error("[DEBUG] Error getting message count:", countError);
                chatOpenError = "메시지 개수 로딩 실패";
                throw countError;
            }

            // 2. 상태 설정 및 UI 초기화
            const totalPages = Math.ceil(totalMessages / CONSTANTS.PAGE_SIZE);
            initialPage = Math.max(0, totalPages - 1);
            StateManager.openChatRoom(chat.id, initialPage); // Set state including initial page

            this.lastRenderedDate = null;
            this.lastMessageSenderId = null;
            if (DOMElements.messagesContainer) {
                DOMElements.messagesContainer.innerHTML = '';
                // *** Sentinel 요소 동적 생성 및 추가 ***
                const sentinel = document.createElement('div');
                sentinel.id = 'message-load-sentinel';
                // 스타일 직접 설정 또는 CSS 클래스 부여
                sentinel.style.height = '1px';
                sentinel.style.pointerEvents = 'none';
                // 컨테이너의 가장 앞에 추가 (첫번째 자식 요소로)
                DOMElements.messagesContainer.prepend(sentinel);
                console.log("[DEBUG] Prepended message-load-sentinel.");
                // *** Sentinel 추가 완료 ***

                // Observer 설정 및 시작 (Sentinel 추가 후)
                this.setupIntersectionObserver(DOMElements.messagesContainer, sentinel);
            } else { console.error("[DEBUG] Messages container not found!"); }
            StateManager.clearRenderedMessages(chat.id);
            this.setupChatWindowHeader(chat, currentUser);
            this.setupChatWindowFeatures(chat, currentUser);
            this.renderOptionsMenu(chat, currentUser);

            // 3. 초기 메시지 가져오기
            let initialMessages = [];
            try {
                initialMessages = await WebSocketManager.fetchMessagesForChat(chat.id, initialPage);
                if (!Array.isArray(initialMessages)) {
                    console.error("[DEBUG] Received messages data is not an array:", initialMessages);
                    throw new Error("Received messages data is not an array.");
                }
            } catch (fetchError) {
                console.error("[DEBUG] Error fetching initial messages:", fetchError);
                chatOpenError = "메시지 목록 로딩 실패";
                throw fetchError;
            }

            // 4. 메시지 렌더링
            initialMessages.forEach(msg => this.renderMessage(msg, 'append'));

            // 5. 스크롤 및 읽음 처리
            this.scrollToBottom(true); // Force scroll after initial render
            WebSocketManager.markAllMessagesAsRead(chat.id);

        } catch (error) {
            // Outer catch handles errors from inner try blocks
            if (!chatOpenError) { // Set default error if not already specific
                chatOpenError = "채팅 내용을 불러오는 중 오류 발생";
            }
            this.showError(chatOpenError);
            StateManager.closeChatRoom(); // Revert to list view on error
        } finally {
            StateManager.setLoading(false); // Ensure loading indicator is hidden
        }

        // 6. 최종 UI 설정 (오류가 없었을 경우)
        if (!chatOpenError) {
            this.updateChatInputState(chat);
            this.updateNotificationToggle(chat.notificationEnabled !== false);
            DOMElements.messageInput?.focus();
        }
    },

    setupChatWindowHeader(chat, currentUser) {
        const otherUser = chat.requester?.uuid === currentUser ? chat.owner : chat.requester;
        const chatName = chat.type === CONSTANTS.CHAT_TYPE.GROUP
            ? (chat.name || 'Unnamed Group')
            : (otherUser?.name || 'Unknown User');
        const avatarImage = chat.type === CONSTANTS.CHAT_TYPE.GROUP ? chat.clubImage : otherUser?.profileImage;
        if (DOMElements.chatName) DOMElements.chatName.textContent = chatName;
        const headerAvatar = DOMElements.personalAvatar;
        if(headerAvatar) {
            headerAvatar.innerHTML = this.createAvatarHtml(avatarImage, chatName, chat.type === CONSTANTS.CHAT_TYPE.GROUP);
        }
    },

    setupChatWindowFeatures(chat, currentUser) {
        if (chat.type === CONSTANTS.CHAT_TYPE.PRIVATE) {
            if (DOMElements.chatStatus) DOMElements.chatStatus.style.display = 'block';
            this.updatePrivateChatStatus(false, null); // Initial state
            WebSocketManager.checkOnlineStatus(chat.id);
            this.removeParticipantsSidebar();
            if (DOMElements.noticeSection) DOMElements.noticeSection.style.display = 'none';
        } else { // Group Chat
            if (DOMElements.chatStatus) DOMElements.chatStatus.style.display = 'none';
            this.renderParticipantsList(chat, currentUser);
            WebSocketManager.checkOnlineStatus(chat.id);
            const notice = StateManager.getState().notices.get(Number(chat.id));
            if (!notice && DOMElements.noticeSection) {
                WebSocketManager.fetchNotice(chat.id);
            } else if (DOMElements.noticeSection) { // Render only if section exists
                this.renderNotice(chat, notice, currentUser);
            }
        }
    },

    resetChatWindow() {
        console.log("[DEBUG] Resetting chat window UI.");
        DOMElements.personalChatContainer?.classList.remove('visible');
        if (DOMElements.messagesContainer) DOMElements.messagesContainer.innerHTML = '';
        if (DOMElements.chatName) DOMElements.chatName.textContent = '채팅';
        if (DOMElements.personalAvatar) DOMElements.personalAvatar.innerHTML = this.createAvatarHtml(null, '?');
        if (DOMElements.chatStatus) { DOMElements.chatStatus.textContent = ''; DOMElements.chatStatus.style.display = 'none';}
        if (DOMElements.optionsMenu) DOMElements.optionsMenu.innerHTML = '';
        if (DOMElements.messageInput) { DOMElements.messageInput.value = ''; DOMElements.messageInput.placeholder = '메시지를 입력하세요.'; DOMElements.messageInput.disabled = true; }
        if (DOMElements.sendButton) DOMElements.sendButton.disabled = true;
        if (DOMElements.noticeSection) DOMElements.noticeSection.style.display = 'none';
        this.removeParticipantsSidebar();
        this.lastRenderedDate = null;
        this.lastMessageSenderId = null;
        // Intersection Observer 연결 해제
        if (this.intersectionObserver) {
            this.intersectionObserver.disconnect();
            this.intersectionObserver = null;
            console.log("[DEBUG] IntersectionObserver disconnected.");
        }
    },

    renderMessage(item, position = 'append') {
        console.log(`[DEBUG] renderMessage called for item (position: ${position}):`, JSON.stringify(item)); // <<< 로그 추가
        const container = DOMElements.messagesContainer;
        if (!container || !item || !item.id) {
            console.warn("Cannot render message, container or item invalid", item);
            return;
        }
        if (StateManager.hasRenderedMessage(item.chatRoomId, item.id)) {
            return; // 중복 렌더링 방지
        }

        const messageTimestamp = item.timestamp ? new Date(item.timestamp) : null;
        const messageDate = messageTimestamp ? Utils.formatDate(item.timestamp) : null;
        const timeStr = Utils.formatTimestamp(item.timestamp);

        // --- 날짜 구분선 추가 ---
        let dateElement = null;
        if (messageDate && messageDate !== this.lastRenderedDate) {
            dateElement = document.createElement('article');
            dateElement.className = 'date-notification';
            const timeElement = document.createElement('time');
            timeElement.className = 'date-text';
            timeElement.textContent = messageDate;
            dateElement.appendChild(timeElement);
            this.lastRenderedDate = messageDate;
            // this.lastMessageSenderId = null; // <<< 제거: 그룹화에 더 이상 사용 안 함
        }

        // --- 메시지 요소 생성 ---
        const element = document.createElement('article');
        element.id = `message-${item.id}`;
        element.dataset.messageId = item.id;

        const isSystemMessage = item.type === CONSTANTS.MESSAGE_TYPE.SYSTEM;
        const isOwnMessage = !isSystemMessage && item.sender?.uuid === StateManager.getState().currentUser;

        if (isSystemMessage) {
            element.className = 'system-notification';
            const p = document.createElement('p');
            p.className = 'system-text';
            p.textContent = item.content;
            element.appendChild(p);
            // this.lastMessageSenderId = null; // <<< 제거: 그룹화에 더 이상 사용 안 함
        } else {
            const senderName = item.sender?.name || 'Unknown';
            const senderId = item.sender?.uuid; // ID는 유지 (나중을 위해)
            const messageContent = item.content;

            // --- *** 그룹화 로직 제거 *** ---
            // const showHeader = !isOwnMessage && (senderId !== this.lastMessageSenderId); // 제거
            // this.lastMessageSenderId = senderId; // 제거
            const showHeader = true; // <<< 수정: 받은 메시지는 항상 헤더 표시
            // --- *** 그룹화 로직 제거 끝 *** ---

            element.className = isOwnMessage ? 'message-sent' : 'message-received'; // 'grouped' 클래스 제거
            // element.classList.toggle('grouped', !showHeader && !isOwnMessage); // 제거

            if (isOwnMessage) {
                // 보낸 메시지 렌더링 (이전과 동일)
                element.innerHTML = `
                     <p class="message-text">${Utils.escapeHTML(messageContent)}</p>
                     <time class="timestamp">${timeStr}</time>`;
            } else { // 받은 메시지
                // --- *** 항상 아바타와 이름 표시 *** ---
                const avatarHtml = this.createAvatarHtml(item.sender?.profileImage, senderName); // 항상 아바타 생성
                element.innerHTML = `
                       <div class="message-avatar">${avatarHtml}</div>
                      <div class="message-content-wrapper">
                           <header class="message-sender-name">${Utils.escapeHTML(senderName)}</header>
                           <div class="message-bubble">
                              <p class="message-text">${Utils.escapeHTML(messageContent)}</p>
                           </div>
                      </div>
                      <div class="message-meta">
                          <time class="timestamp">${timeStr}</time>
                      </div>`;
                // --- *** 항상 아바타와 이름 표시 끝 *** ---
            }
        }

        // --- DOM에 요소 추가 ---
        const insertReference = (position === 'prepend') ? container.querySelector('article:not(.date-notification)') : null;
        if (dateElement) {
            container.insertBefore(dateElement, position === 'prepend' ? container.firstChild : null);
        }
        container.insertBefore(element, position === 'prepend' ? (dateElement ? dateElement.nextSibling : container.firstChild) : null);

        StateManager.addRenderedMessage(item.chatRoomId, item.id);

        if (position === 'append') {
            element.style.opacity = '0';
            requestAnimationFrame(() => { element.style.transition = 'opacity 0.3s ease-in'; element.style.opacity = '1'; });
        }
        return element;
    },


    scrollToBottom(force = false) {
        const container = DOMElements.messagesContainer;
        if (!container) return;
        const scrollThreshold = 100;
        const isNearBottom = container.scrollHeight - container.scrollTop - container.clientHeight < scrollThreshold;
        if (force || isNearBottom || StateManager.getState().isScrollable) {
            container.scrollTop = container.scrollHeight;
        }
    },

    updatePrivateChatStatus(isOnline, lastOnlineTimestamp) {
        const statusElement = DOMElements.chatStatus;
        if (!statusElement) return;
        statusElement.style.display = 'block';
        if (isOnline) {
            statusElement.textContent = '온라인'; statusElement.style.color = '#00cc00';
        } else if (lastOnlineTimestamp) {
            const relativeTime = Utils.formatRelativeTime(lastOnlineTimestamp);
            statusElement.textContent = `마지막 접속: ${relativeTime}`; statusElement.style.color = '#666';
        } else {
            statusElement.textContent = '오프라인'; statusElement.style.color = '#666';
        }
    },

    updateGroupParticipantStatus(uuid, isOnline) {
        const indicator = document.querySelector(`.participants-sidebar .status-indicator[data-uuid="${uuid}"]`);
        if (indicator) { indicator.style.backgroundColor = isOnline ? '#4caf50' : '#666'; }
    },

    updateChatInputState(chat) {
        const input = DOMElements.messageInput;
        const button = DOMElements.sendButton;
        if (!input || !button) return;
        const isDisabled = !chat || chat.status === CONSTANTS.CHAT_STATUS.CLOSED || chat.status === CONSTANTS.CHAT_STATUS.BLOCKED;
        const isPendingRequester = chat?.status === CONSTANTS.CHAT_STATUS.PENDING && chat?.requester?.uuid === StateManager.getState().currentUser;
        const finalDisabled = isDisabled || isPendingRequester;
        input.disabled = finalDisabled;
        button.disabled = finalDisabled;
        if (finalDisabled) {
            input.placeholder = isPendingRequester ? "수락 대기 중..." : "채팅이 비활성화되었습니다.";
            input.value = '';
        } else {
            input.placeholder = "메시지를 입력하세요...";
        }
    },

    updateNotificationToggle(isEnabled) {
        const button = DOMElements.notificationToggle; const icon = DOMElements.notificationIcon; if (!button) return;
        button.setAttribute('aria-pressed', isEnabled.toString()); button.classList.toggle('enabled', isEnabled);
        button.title = isEnabled ? "알림 끄기" : "알림 켜기"; if (icon) { icon.style.fill = isEnabled ? '#333' : '#ccc'; }
    },

    renderOptionsMenu(chat, currentUser) {
        const menu = DOMElements.optionsMenu; if (!menu) return; menu.innerHTML = ''; const isOwner = chat.owner?.uuid === currentUser;
        if (chat.type === CONSTANTS.CHAT_TYPE.PRIVATE) { menu.appendChild(this.createOptionButton('나가기', 'leave-option')); menu.appendChild(this.createOptionButton('차단하기', 'block-option')); }
        else if (chat.type === CONSTANTS.CHAT_TYPE.GROUP) { menu.appendChild(this.createOptionButton('참여자 목록', 'user-list-toggle')); if (!isOwner) menu.appendChild(this.createOptionButton('나가기', 'leave-option')); }
    },

    createOptionButton(text, className) {
        const button = document.createElement('button'); button.textContent = text; button.className = className; return button;
    },
    // 참여자 데이터 하나를 받아 LI 요소를 생성하여 반환
    createParticipantListItem(participant, isOwner) {
        const item = document.createElement('li');
        item.className = 'participant-item';
        item.dataset.uuid = participant.uuid; // Store UUID

        const avatarHtml = this.createAvatarHtml(participant.profileImage, participant.name);

        item.innerHTML = `
             <div class="participant-avatar-container">
                ${avatarHtml}
                <div class="status-indicator" data-uuid="${participant.uuid}" style="background-color: #666;" title="오프라인"></div>
             </div>
             <div class="user-info">
                <span class="user-name">${Utils.escapeHTML(participant.name)}</span>
                ${isOwner ? '<span class="role-badge">모임장</span>' : ''}
             </div>
        `;
        // 이미지 에러 처리 핸들러 추가 (renderParticipantsList와 동일하게)
        const avatarImage = item.querySelector('.avatar-image');
        if (avatarImage) {
            avatarImage.onerror = () => {
                const avatar = avatarImage.closest('.avatar');
                avatar?.classList.remove('has-image');
                avatar?.classList.add('has-text');
                if(avatarImage) avatarImage.style.display = 'none';
                const avatarText = avatar?.querySelector('.avatar-text');
                if (avatarText) avatarText.style.display = 'block';
            };
        }
        return item;
    },
    // renderParticipantsList 함수는 이제 위 헬퍼 함수 사용
    renderParticipantsList(chat, currentUser) {
        console.log(`[DEBUG] renderParticipantsList called for chat ID: ${chat?.id}`);
        // console.log("[DEBUG] Chat data in renderParticipantsList:", JSON.stringify(chat));

        this.removeParticipantsSidebar();
        if (!chat || chat.type !== CONSTANTS.CHAT_TYPE.GROUP || !chat.participants || chat.participants.length === 0) {
            console.log("[DEBUG] Exiting renderParticipantsList: Not group or no participants.");
            // 참여자 없을 때 빈 사이드바 렌더링 (선택적)
            const emptySidebar = document.createElement('aside');
            emptySidebar.className = 'participants-sidebar';
            emptySidebar.id = 'participantsSidebar';
            emptySidebar.innerHTML = `<header class="sidebar-header"><h2 class="participant-count">참여자 (0)</h2><button class="participants-close-button" aria-label="참여자 목록 닫기">&times;</button></header><ul class="participant-list"><li class="empty-participant">참여자가 없습니다.</li></ul>`;
            DOMElements.personalChatContainer?.appendChild(emptySidebar);
            emptySidebar.querySelector('.participants-close-button')?.addEventListener('click', this.toggleParticipantsSidebar);
            return;
        }

        const sidebar = document.createElement('aside');
        sidebar.className = 'participants-sidebar';
        sidebar.id = 'participantsSidebar';
        sidebar.innerHTML = `
            <header class="sidebar-header">
                 <h2 class="participant-count">참여자 (${chat.participants.length})</h2>
                 <button class="participants-close-button" aria-label="참여자 목록 닫기">&times;</button>
            </header>
            <ul class="participant-list"></ul>
        `;
        const listElement = sidebar.querySelector('.participant-list');
        // console.log("[DEBUG] Found listElement (.participant-list):", listElement);

        if (listElement) {
            const fragment = document.createDocumentFragment();
            chat.participants.forEach(participant => {
                const isOwner = participant.uuid === chat.owner?.uuid;
                // *** 헬퍼 함수 사용 ***
                const item = this.createParticipantListItem(participant, isOwner);
                fragment.appendChild(item);
            });
            listElement.appendChild(fragment);
            // console.log(`[DEBUG] Appended fragment with ${chat.participants.length} items to listElement.`);
        } else {
            console.error("[DEBUG] Could not find .participant-list inside sidebar template!");
        }

        const container = DOMElements.personalChatContainer;
        if (container) {
            container.appendChild(sidebar);
            // console.log("[DEBUG] Appended sidebar to personalChatContainer.");
        } else { console.error("[DEBUG] Cannot append sidebar, personalChatContainer not found!"); }

        sidebar.querySelector('.participants-close-button')?.addEventListener('click', () => {
            console.log("[DEBUG] Sidebar close button clicked.");
            this.toggleParticipantsSidebar();
        });
        WebSocketManager.checkOnlineStatus(chat.id); // 온라인 상태 확인 요청
    },

    toggleParticipantsSidebar() {
        console.log("[DEBUG] toggleParticipantsSidebar called."); // <<< 로그 추가
        const sidebar = document.getElementById('participantsSidebar');
        console.log("[DEBUG] Found sidebar element by ID:", sidebar); // <<< 중요: null 인지 확인!

        if (!sidebar) {
            console.error("Participants sidebar element with ID 'participantsSidebar' not found in DOM!");
            return; // 사이드바 없으면 중단
        }
        sidebar.classList.toggle('visible'); // visible 클래스 토글
        // 옵션 메뉴의 버튼 상태 업데이트
        DOMElements.optionsMenu?.querySelector('.user-list-toggle')
            ?.setAttribute('aria-expanded', sidebar.classList.contains('visible'));
        console.log(`[DEBUG] Toggled sidebar visibility. Now visible: ${sidebar.classList.contains('visible')}`); // <<< 로그 추가
    },

    removeParticipantsSidebar() {
        const sidebar = document.getElementById('participantsSidebar'); sidebar?.remove();
        DOMElements.optionsMenu?.querySelector('.user-list-toggle')?.setAttribute('aria-expanded', 'false');
    },

    renderNotice(chat, noticeData, currentUser) {
        const section = DOMElements.noticeSection;
        const view = DOMElements.noticeView;
        const empty = DOMElements.noticeEmpty;
        const preview = DOMElements.noticePreview;
        const content = DOMElements.noticeContent;
        const editBtn = DOMElements.noticeEditButton;
        const deleteBtn = DOMElements.noticeDeleteButton;
        const addBtn = DOMElements.noticeAddButton;
        const toggleBtn = DOMElements.noticeToggleButton;

        // 요소 존재 여부 우선 확인
        if (!section || !view || !empty || !preview || !content || !editBtn || !deleteBtn || !addBtn || !toggleBtn) {
            console.warn("[Notice DEBUG] Required notice elements not found. Aborting render.");
            if(section) section.style.display = 'none';
            return;
        }

        // 그룹 채팅이 아니면 숨김
        if (!chat || chat.type !== CONSTANTS.CHAT_TYPE.GROUP) {
            section.style.display = 'none';
            return;
        }

        // --- 디버깅 로그 추가 ---
        const isOwner = chat.owner?.uuid === currentUser;
        const hasNotice = !!(noticeData && noticeData.content && noticeData.content.trim());
        const shouldShowSection = hasNotice || isOwner;
        if (shouldShowSection) {
            section.style.display = 'block';

            if (hasNotice) { // 공지 있음
                console.log("[Notice DEBUG] Notice content found. Showing view, hiding empty/add.");
                view.style.display = 'block';
                empty.style.display = 'none';
                addBtn.style.display = 'none'; // '등록' 버튼 숨김

                // ... (기존 공지 내용 표시 로직) ...
                const noticeText = Utils.escapeHTML(noticeData.content);
                preview.textContent = noticeText.split('\n')[0];
                content.innerHTML = noticeText.replace(/\n/g, '<br>');
                editBtn.style.display = isOwner ? 'inline-block' : 'none';
                deleteBtn.style.display = isOwner ? 'inline-block' : 'none';
                toggleBtn.style.display = 'inline-block';
                const isExpanded = noticeData?.expanded ?? false;
                toggleBtn.setAttribute('aria-expanded', isExpanded.toString());
                content.classList.toggle('expanded', isExpanded);
                preview.classList.toggle('hidden', isExpanded);
                toggleBtn.querySelector('svg')?.classList.toggle('rotated', isExpanded);

            } else { // 공지 없음
                console.log("[Notice DEBUG] No notice content. Hiding view.");
                view.style.display = 'none';
                editBtn.style.display = 'none';
                deleteBtn.style.display = 'none';
                toggleBtn.style.display = 'none';

                // 모임장일 때만 '없음' 영역과 '등록' 버튼 표시
                if (isOwner) {
                    console.log("[Notice DEBUG] User is owner. Showing empty message and add button.");
                    empty.style.display = 'block';
                    addBtn.style.display = 'inline-block'; // <<< '등록' 버튼 표시
                } else {
                    console.log("[Notice DEBUG] User is not owner. Hiding empty message and add button.");
                    empty.style.display = 'none';
                    addBtn.style.display = 'none';
                }
            }
        }else{
            section.style.display = 'none'; // 섹션 전체를 숨김
        }
        // --- 로그 추가 끝 ---
    },

    // 공지사항 모달 설정 및 이벤트 처리
    setupNoticeModal() {
        const modal = document.getElementById('noticeModal'); // Use getElementById for consistency
        const title = document.getElementById('modalTitle');
        const form = document.getElementById('noticeForm');
        const input = document.getElementById('noticeText');
        const cancelBtn = document.getElementById('cancelNotice');
        const submitBtn = document.getElementById('submitNotice');

        if (!modal || !title || !form || !input || !cancelBtn || !submitBtn) {
            console.warn("Notice modal elements missing...");
            this.noticeModalControls = { openModal: () => {}, closeModal: () => {} }; // Set empty controls
            return;
        }
        console.log("[DEBUG] setupNoticeModal executed.");

        const openModal = (mode, data = {}) => {
            modal.style.display = 'flex';
            form.dataset.mode = mode;
            submitBtn.disabled = false; // Ensure button is enabled

            if (mode === 'add' || mode === 'edit') {
                title.textContent = mode === 'add' ? '공지 등록' : '공지 수정';
                input.placeholder = "공지사항 내용을 입력하세요";
                input.value = data.text || '';
                submitBtn.textContent = mode === 'add' ? '등록' : '수정';
                form.removeAttribute('data-target-uuid'); // Clear target UUID if set previously
            }
            input.focus();
        };

        const closeModal = () => {
            modal.style.display = 'none';
            input.value = '';
            delete form.dataset.mode;
            submitBtn.disabled = false; // Ensure button is enabled on close
        };

        // Form submit handler (now includes request logic)
        form.onsubmit = async (e) => { // Add async
            e.preventDefault();
            if (submitBtn.disabled) return;

            const content = input.value.trim(); // Notice content OR request reason
            const mode = form.dataset.mode;

            // Common checks
            if (!WebSocketManager.isConnected()) { alert('서버에 연결되지 않았습니다.'); return; }

            submitBtn.disabled = true;
            submitBtn.textContent = '처리중...';

            try {
                if (mode === 'add' || mode === 'edit') { // Notice logic
                    const currentChatId = StateManager.getState().currentChatRoomId;
                    if (!content) { throw new Error('공지 내용을 입력해주세요.'); }
                    if (!currentChatId) { throw new Error('공지 처리 불가 (채팅방 정보 없음)'); }

                    if (mode === 'add') await WebSocketManager.createNotice(currentChatId, content);
                    else await WebSocketManager.updateNotice(currentChatId, content);
                    closeModal();

                }
            } catch (error) {
                console.error("Error submitting form:", error);
                UIManager.showError(error.message || "처리 중 오류 발생");
                // Restore button state on error
                submitBtn.disabled = false;
                submitBtn.textContent = mode === 'add' ? '등록' : (mode === 'edit' ? '수정' : '요청 보내기');
            }
        };

        this.noticeModalControls = { openModal, closeModal };
    },
    // 공지사항 모달 설정 및 이벤트 처리
    setupChatModal() {
        const modal = document.getElementById('ChatModal'); // Use getElementById for consistency
        const title = document.getElementById('ChatmodalTitle');
        const form = document.getElementById('ChatForm');
        const input = document.getElementById('ChatText');
        const cancelBtn = document.getElementById('cancelChat');
        const submitBtn = document.getElementById('submitChat');

        if (!modal || !title || !form || !input || !cancelBtn || !submitBtn) {
            console.warn("Notice modal elements missing...");
            this.chatModalControls = { openModal: () => {}, closeModal: () => {} }; // Set empty controls
            return;
        }
        console.log("[DEBUG] setupNoticeModal executed.");

        const openModal = (data = {}) => {
            modal.style.display = 'flex';
            submitBtn.disabled = false; // Ensure button is enabled
            title.textContent = `${data.name || '사용자'}님께 채팅 요청`;
            input.placeholder = "채팅 요청 사유";
            input.value = ''; // Clear reason field
            submitBtn.textContent = '요청 보내기';
            form.dataset.targetUuid = data.uuid || ''; // Store target UUID
            input.focus();
        };

        const closeModal = () => {
            modal.style.display = 'none';
            input.value = '';
            delete form.dataset.targetUuid; // Clear target UUID on close
            submitBtn.disabled = false; // Ensure button is enabled on close
        };

        // Form submit handler (now includes request logic)
        form.onsubmit = async (e) => { // Add async
            e.preventDefault();
            if (submitBtn.disabled) return;

            const content = input.value.trim(); // Notice content OR request reason

            if(content == null || content === '') {
                return;
            }
            // Common checks
            if (!WebSocketManager.isConnected()) { alert('서버에 연결되지 않았습니다.'); return; }

            submitBtn.disabled = true;
            submitBtn.textContent = '처리중...';

            const targetUuid = form.dataset.targetUuid;
            if (!targetUuid) { throw new Error("요청 대상 UUID가 없습니다."); }
            // Reason (content) is optional here

            console.log(`[DEBUG] Sending chat request via Fetch to UUID: ${targetUuid}, reason: ${content}`);
            const csrfToken = document.querySelector('meta[name="_csrf"]').content;
            // Use Fetch API to POST to the existing controller endpoint
            const response = await fetch('/addpublicchat/requestchat', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    // Add CSRF token header if needed by Spring Security
                    'X-CSRF-TOKEN': csrfToken
                },
                body: new URLSearchParams({
                    'uuid': targetUuid,
                    'reason': content // Send reason (can be empty)
                })
            });

            if (response.ok) {
                const successMsg = await response.text();
                alert(successMsg || "채팅 요청을 보냈습니다.");
                closeModal();
                if(DOMElements.userSearchInput) DOMElements.userSearchInput.value = '';
                if(DOMElements.userSearchResults) DOMElements.userSearchResults.innerHTML = '';
                WebSocketManager.refreshChatRooms(); // Refresh list to show pending request
            } else {
                const errorMsg = await response.text();
                alert(errorMsg || "채팅 요청 실패.");
                submitBtn.disabled = false;
                submitBtn.textContent = '요청 보내기';
                throw new Error(errorMsg || `채팅 요청 실패 (${response.status})`);
            }
        };

        this.chatModalControls = { openModal, closeModal };
    },
    renderSearchResults(users) {
        if (!DOMElements.userSearchResults) {
            console.error("User search results container not found.");
            return;
        }
        DOMElements.userSearchResults.innerHTML = ''; // 이전 결과 삭제

        if (!users || users.length === 0) {
            DOMElements.userSearchResults.innerHTML = '<p class="text-muted small p-2 mb-0">검색 결과가 없습니다.</p>';
            return;
        }

        const fragment = document.createDocumentFragment();
        users.forEach(user => {
            const item = document.createElement('div');
            // Bootstrap 클래스 등 활용하여 디자인
            item.className = 'search-result-item d-flex align-items-center p-2 border-bottom';
            item.style.cursor = 'pointer';
            item.dataset.uuid = user.uuid;
            item.dataset.name = user.name;
            item.dataset.profile = user.profileImage || '';

            const avatarHtml = this.createAvatarHtml(user.profileImage, user.name, false);
            const nameSpan = document.createElement('span');
            nameSpan.className = 'search-result-name';
            nameSpan.textContent = user.name; // escapeHTML 필요 없음 (textContent 사용)

            const avatarDiv = document.createElement('div');
            avatarDiv.className = 'chat-avatar small-avatar me-2'; // 작은 아바타 스타일 적용
            avatarDiv.innerHTML = avatarHtml;

            item.appendChild(avatarDiv);
            item.appendChild(nameSpan);
            fragment.appendChild(item);
        });
        DOMElements.userSearchResults.appendChild(fragment);
    },
    showPushNotification(notification) {
        const container = DOMElements.notificationContainer; if (!container) return;
        const state = StateManager.getState(); if (notification.sender?.uuid === state.currentUser || (state.isChatRoomOpen && notification.chatRoomId === state.currentChatRoomId)) return;
        const chat = StateManager.findChatRoom(notification.chatRoomId); if (chat && chat.notificationEnabled === false) return;
        DOMElements.notificationName.textContent = notification.senderName || "알림"; DOMElements.notificationMessage.textContent = notification.content || ""; DOMElements.notificationTimestamp.textContent = Utils.formatTimestamp(notification.timestamp);
        if (DOMElements.notificationAvatarContainer) {
            DOMElements.notificationAvatarContainer.innerHTML = this.createAvatarHtml(
                notification.sender?.profileImage,
                notification.senderName || '?'
            );
        } else {
            console.warn("Notification avatar container (#avatarContainer) not found. Skipping avatar.");
        }
        container.onclick = () => { ChatApp.openChatById(notification.chatRoomId); this.hidePushNotification(); };
        container.style.display = 'block'; container.style.visibility = 'visible'; container.style.opacity = '0'; container.style.transform = 'translateX(100%)';
        requestAnimationFrame(() => { container.style.transition = 'transform 0.3s ease-out, opacity 0.3s ease-out'; container.style.opacity = '1'; container.style.transform = 'translateX(0)'; });
        setTimeout(() => this.hidePushNotification(), 5000);
    },
    hidePushNotification() {
        const container = DOMElements.notificationContainer; if (!container || container.style.opacity === '0') return;
        container.style.transition = 'transform 0.3s ease-in, opacity 0.3s ease-in'; container.style.opacity = '0'; container.style.transform = 'translateX(100%)';
        setTimeout(() => { container.style.display = 'none'; container.style.visibility = 'hidden'; container.onclick = null; }, 300);
    },
    showError(message) {
        console.error("UI Error:", message); const errorDiv = document.createElement('div'); errorDiv.className = 'error-message'; errorDiv.textContent = message; errorDiv.setAttribute('role', 'alert');
        errorDiv.style.cssText = 'position:fixed; bottom:20px; left:50%; transform:translateX(-50%); background-color:rgba(211,47,47,0.9); color:white; padding:10px 20px; border-radius:4px; z-index:10000; opacity:0; transition:opacity 0.5s ease-in-out;';
        (DOMElements.errorContainer || document.body).appendChild(errorDiv);
        requestAnimationFrame(() => { errorDiv.style.opacity = '1'; });
        setTimeout(() => { errorDiv.style.opacity = '0'; setTimeout(() => errorDiv.remove(), 500); }, 5000);
    },

    setupEventListeners() {
        console.log("[DEBUG] Setting up event listeners...");
        // Main controls
        if (DOMElements.openChatButton) {
            DOMElements.openChatButton.addEventListener('click', () => {
                console.log("[DEBUG] openChatButton clicked!"); // <<< 로그 추가
                StateManager.openChatList();
            });
            console.log("[DEBUG] openChatButton listener attached.");
        } else {
            console.error("[DEBUG] openChatButton not found, cannot attach listener."); // <<< 로그 추가
        }
        if (DOMElements.closeChatButton) {
            DOMElements.closeChatButton.addEventListener('click', () => {
                console.log("[DEBUG] closeChatButton clicked!"); // <<< 로그 추가
                StateManager.closeChatApp();
            });
            console.log("[DEBUG] closeChatButton listener attached.");
        } else {
            console.error("[DEBUG] closeChatButton not found, cannot attach listener."); // <<< 로그 추가
        }
        // Tabs
        DOMElements.groupTab?.addEventListener('click', () => StateManager.switchTab(CONSTANTS.CHAT_TYPE.GROUP));
        DOMElements.personalTab?.addEventListener('click', () => StateManager.switchTab(CONSTANTS.CHAT_TYPE.PRIVATE));

        // Chat List Interaction (Delegation)
        if (DOMElements.chatList) {
            DOMElements.chatList.addEventListener('click', (event) => {
                console.log("[DEBUG] Click detected on #chatList. Target:", event.target);
                const chatItem = event.target.closest('.chat-item');
                if (!chatItem) {
                    console.log("[DEBUG] Click was not on a .chat-item.");
                    return;
                }
                const chatId = chatItem.dataset.chatId;
                if (!chatId) {
                    console.log("[DEBUG] Clicked item has no data-chat-id.");
                    return;
                }

                const actionButton = event.target.closest('.action-button');
                if (actionButton) { // 액션 버튼 클릭 시
                    const action = actionButton.dataset.action;
                    console.log(`[DEBUG] Action button clicked. Action: ${action}, ChatID: ${chatId}`); // <<< 로그 추가

                    if (action) {
                        // confirm 전에 로그 추가
                        console.log(`[DEBUG] Showing confirm dialog for action: ${action}`); // <<< 로그 추가
                        if (confirm(`정말로 이 요청을 '${actionButton.textContent}'하시겠습니까?`)) {
                            console.log(`[DEBUG] Confirmed action: ${action}. Calling WebSocketManager.handleChatRequest...`); // <<< 로그 추가
                            const sent = WebSocketManager.handleChatRequest(chatId, action);
                            console.log(`[DEBUG] WebSocketManager.handleChatRequest called. Sent: ${sent}`); // <<< 로그 추가

                            if (action === CONSTANTS.CHAT_STATUS.REJECTED || action === CONSTANTS.CHAT_STATUS.BLOCKED) {
                                console.log(`[DEBUG] Optimistically removing chat item for action: ${action}`); // <<< 로그 추가
                                StateManager.removeChatRoom(chatId);
                            } else if (action === CONSTANTS.CHAT_STATUS.APPROVED) {
                                console.log(`[DEBUG] Approve action sent. Waiting for server update...`); // <<< 로그 추가
                                // 필요하다면 여기서 버튼 비활성화 및 '처리중...' 표시 추가
                                // actionButton.disabled = true;
                                // actionButton.textContent = '처리중...';
                            }
                        } else {
                            console.log(`[DEBUG] Cancelled action: ${action}`); // <<< 로그 추가
                        }
                    } else {
                        console.log(`[DEBUG] Action button clicked but no action found in dataset.`); // <<< 로그 추가
                    }
                } else if (!chatItem.classList.contains('request-item')) {
                    console.log(`[DEBUG] Calling ChatApp.openChatById for ID: ${chatId} (Status might be ACTIVE, CLOSED, or BLOCKED)`);
                    ChatApp.openChatById(chatId);
                } else {
                    console.log("[DEBUG] Clicked item is a request item, not opening chat.");
                }
            });
            console.log("[DEBUG] #chatList click listener attached.");
        } else { console.error("[DEBUG] #chatList element not found during listener setup!"); }
        // Personal Chat Window controls
        if (DOMElements.backButton) {
            DOMElements.backButton.addEventListener('click', () => StateManager.closeChatRoom());
            console.log("[DEBUG] Back button click listener attached.");
        } else { console.error("[DEBUG] Back button element not found during listener setup!"); }

        // Message Input & Send
        if (DOMElements.sendButton) { DOMElements.sendButton.addEventListener('click', ChatApp.sendMessage); console.log("[DEBUG] Send button click listener attached."); }
        else { console.error("[DEBUG] Send button element not found!"); }
        if (DOMElements.messageInput) { DOMElements.messageInput.addEventListener('keypress', (e)=>{ if(e.key==='Enter'&&!e.shiftKey){ e.preventDefault(); ChatApp.sendMessage(); } }); console.log("[DEBUG] Message input keypress listener attached."); }
        else { console.error("[DEBUG] Message input element not found!"); }

        // Message container scroll listeners
        if (DOMElements.messagesContainer) {
            //DOMElements.messagesContainer.addEventListener('scroll', Utils.throttle(this.handleMessageScroll, 100));
         /*   DOMElements.messagesContainer.addEventListener('scroll', () => {
                const el = DOMElements.messagesContainer; if (!el) return;
                const isAtBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 10;
                if (StateManager.getState().isScrollable !== isAtBottom) StateManager.setState({ isScrollable: isAtBottom });
            });*/
            console.log("[DEBUG] Messages container scroll listeners attached.");
        } else { console.error("[DEBUG] Messages container element not found!"); }
        // Options Menu & Actions
        DOMElements.optionsButton?.addEventListener('click', () => { if (DOMElements.optionsMenu) DOMElements.optionsMenu.style.display = DOMElements.optionsMenu.style.display==='block'?'none':'block'; });
        document.addEventListener('click', (event) => { if (!DOMElements.optionsButton?.contains(event.target) && !DOMElements.optionsMenu?.contains(event.target)) { if(DOMElements.optionsMenu) DOMElements.optionsMenu.style.display = 'none'; } });
        if (DOMElements.optionsMenu) {
            DOMElements.optionsMenu.addEventListener('click', (event) => {
                const button = event.target.closest('button');
                if (!button) return;
                const chatId = StateManager.getState().currentChatRoomId;
                if (!chatId) return;
                if (button.classList.contains('leave-option')) {
                    console.log("[DEBUG] Leave option clicked");
                    if (confirm("정말로 이 채팅방을 나가시겠습니까?")) {
                        // 서버 요청 보내기 (서버에서 모임장 여부 등 최종 확인)
                        const sent = WebSocketManager.leaveChatRoom(chatId);
                        if (sent) {
                            // 낙관적 업데이트: 즉시 채팅방 닫고 목록에서 제거
                            StateManager.closeChatRoom(); // 목록 보기로 이동
                            StateManager.removeChatRoom(chatId); // 캐시 및 목록에서 제거
                        }
                    }
                }
                else if (button.classList.contains('block-option')) {
                    console.log("[DEBUG] Block option clicked");
                    if (confirm("정말로 이 사용자를 차단하시겠습니까?\n차단 후에는 대화할 수 없으며, 상대방에게는 채팅방 나가기로 표시될 수 있습니다.")) {
                        // 서버 요청 보내기
                        const sent = WebSocketManager.blockUser(chatId);
                        if (sent) {
                            // 낙관적 업데이트: 즉시 채팅방 닫고 목록에서 제거
                            StateManager.closeChatRoom(); // 목록 보기로 이동
                            StateManager.removeChatRoom(chatId); // 캐시 및 목록에서 제거
                        }
                    }
                }
                else if (button.classList.contains('user-list-toggle')) {
                    // <<< 로그 추가 >>>
                    console.log("[DEBUG] 'user-list-toggle' button clicked. Calling toggleParticipantsSidebar...");
                    this.toggleParticipantsSidebar(); // 토글 함수 호출
                }
                // 옵션 메뉴 닫기
                if (DOMElements.optionsMenu) DOMElements.optionsMenu.style.display = 'none';
            });
        } else {
            console.error("[DEBUG] Options menu element not found during listener setup!");
        }



        // Notification Toggle in Header
        DOMElements.notificationToggle?.addEventListener('click', () => { const c=StateManager.findChatRoom(StateManager.getState().currentChatRoomId); if(c) WebSocketManager.toggleNotification(c.id, !(c.notificationEnabled!==false)); StateManager.updateNotificationSetting(c.id, !(c.notificationEnabled!==false)); });



        // --- ADD Search Button/Input Listeners ---
        if (DOMElements.userSearchButton) {
            DOMElements.userSearchButton.addEventListener('click', ChatApp.searchUser);
            console.log("[DEBUG] User search button listener attached.");
        } else { console.warn("[DEBUG] User search button not found."); }

        if (DOMElements.userSearchInput) {
            DOMElements.userSearchInput.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') { e.preventDefault(); ChatApp.searchUser(); }
            });
            console.log("[DEBUG] User search input listener attached.");
        } else { console.warn("[DEBUG] User search input not found."); }
        // --- END Search Listeners ---


        // --- ADD Search Results Click Listener ---
        if (DOMElements.userSearchResults) {
            DOMElements.userSearchResults.addEventListener('click', (event) => {
                const targetItem = event.target.closest('.search-result-item');
                if (!targetItem) return;
                const targetUuid = targetItem.dataset.uuid;
                const targetName = targetItem.dataset.name;
                if (!targetUuid) { console.error("Clicked item missing data-uuid"); return; }

                console.log(`[DEBUG] Clicked search result for UUID: ${targetUuid}`);
                // Use notice modal for request reason
                if (this.chatModalControls) {
                    this.chatModalControls.openModal({ uuid: targetUuid, name: targetName });
                } else {
                    console.error("Chat modal controls not available!");
                    alert("오류: 요청 창을 열 수 없습니다.");
                }
            });
            console.log("[DEBUG] User search results click listener attached.");
        } else { console.warn("[DEBUG] User search results container not found."); }
        // --- END Search Results Listener ---
        this.setupChatModal();
        // Notice Modal setup (Event delegation for buttons inside modal)
        this.setupNoticeModal(); // 최초 1회 호출하여 controls 설정
        document.body.addEventListener('click', (event) => {
            const target = event.target;
            const currentChatId = StateManager.getState().currentChatRoomId;
            const noticeControls = this.noticeModalControls;
            const chatControls = this.chatModalControls;
            if (!noticeControls || !chatControls) return;

            // 공지사항 추가 버튼 클릭
            if (target === DOMElements.noticeAddButton) {
                noticeControls.openModal('add');
            }
            // 공지사항 수정 버튼 클릭
            else if (target === DOMElements.noticeEditButton) { const n = currentChatId ? StateManager.getState().notices.get(Number(currentChatId)) : null; if (n) noticeControls.openModal('edit', n.content); }
            // 공지사항 삭제 버튼 클릭
            else if (target === DOMElements.noticeDeleteButton) {
                if (currentChatId && confirm('공지사항을 삭제하시겠습니까?')) {
                    WebSocketManager.deleteNotice(currentChatId);
                    // Optimistic UI Update: 서버 응답 전 미리 반영
                    StateManager.updateNotice(currentChatId, null);
                }
            }
            else if (target === DOMElements.cancelChatButton || (target === DOMElements.ChatModal && event.target === DOMElements.ChatModal)) { chatControls.closeModal(); }
            // 모달 취소 버튼 또는 모달 배경 클릭
            else if (target === DOMElements.cancelNoticeButton || (target === DOMElements.noticeModal && event.target === DOMElements.noticeModal)) { noticeControls.closeModal(); }
            // 공지사항 토글 버튼 클릭
            else if (target === DOMElements.noticeToggleButton || target.closest('.notice-toggle')) {
                const notice = currentChatId ? StateManager.getState().notices.get(Number(currentChatId)) : null;
                if (currentChatId && notice) {
                    const newState = !(notice.expanded ?? false);
                    WebSocketManager.toggleNoticeState(currentChatId, newState);
                    // Optimistic UI Update
                    StateManager.updateNoticeExpansion(currentChatId, newState);
                }
            }
            // 공지사항 미리보기 클릭 (축소 상태일 때만)
            else if (target === DOMElements.noticePreview) {
                const notice = currentChatId ? StateManager.getState().notices.get(Number(currentChatId)) : null;
                if (notice && !notice.expanded) {
                    DOMElements.noticeToggleButton?.click(); // 토글 버튼 클릭 효과
                }
            }
        });


        console.log("[DEBUG] Event listeners setup complete.");
    },
    // Intersection Observer 설정 및 시작 함수
    setupIntersectionObserver() {
        const sentinel = document.getElementById('message-load-sentinel');
        const container = DOMElements.messagesContainer;

        if (!sentinel || !container) {
            console.error("Cannot setup IntersectionObserver: sentinel or container not found.");
            return;
        }

        // 기존 Observer가 있다면 해제
        if (this.intersectionObserver) {
            this.intersectionObserver.disconnect();
        }

        const options = {
            root: container, // 감시할 루트 요소 (메시지 컨테이너)
            rootMargin: '0px', // 루트 요소와의 여백 없음
            threshold: 0.1 // 10% 이상 보이면 콜백 실행 (0 또는 1도 가능)
        };

        // Intersection Observer 콜백 함수
        const observerCallback = (entries, observer) => {
            entries.forEach(entry => {
                // isIntersecting: 감시 대상(sentinel)이 루트(container)와 교차(보임)하는지 여부
                if (entry.isIntersecting) {
                    console.log("Message load sentinel is visible, attempting to load older messages...");
                    // 로딩 함수 호출 (debounce된 버전 사용)
                    ChatApp.loadPreviousMessages();
                }
            });
        };

        // Observer 생성 및 관찰 시작
        this.intersectionObserver = new IntersectionObserver(observerCallback, options);
        this.intersectionObserver.observe(sentinel);
        console.log("[DEBUG] IntersectionObserver setup and observing sentinel.");
    },
    handleMessageScroll() {
        const container = DOMElements.messagesContainer;
        if (!container || StateManager.getState().isLoading || StateManager.isFetchingPrevious(StateManager.getState().currentChatRoomId)) return;
        if (container.scrollTop < 100) { // Top threshold
            console.log("Near top, attempting to load older messages...");
            ChatApp.loadPreviousMessages();
        }
    }
};


// --- Main Application Controller ---
const ChatApp = {
    lastSendTime: 0,
    markedMessageIds: new Map(), // Map<chatRoomId, Set<messageId>>

    init() {
        console.log("Initializing Chat App...");
        DOMElements.initialize();
        StateManager.loadChatState();
        // console.log("[DEBUG] State after loadChatState:", JSON.stringify(StateManager.getState()));

        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', () => {
                UIManager.setupEventListeners();
                this.postInitSetup(); // Call after setup
            });
        } else {
            UIManager.setupEventListeners();
            this.postInitSetup(); // Call immediately
        }
    },

    // Post-initialization steps (after listeners are set)
    postInitSetup() {
        WebSocketManager.connect();

        console.log("[DEBUG] Calling initial UI updates.");
        UIManager.updateChatUI(StateManager.getState());
        UIManager.updateTabUI(StateManager.getState().activeTab);

        // Reopen chat room if state indicates
        const initialChatId = StateManager.getState().currentChatRoomId;
        if (StateManager.getState().isChatRoomOpen && initialChatId) {
            console.log(`[DEBUG] Attempting to reopen chat room ${initialChatId} from loaded state.`);
            let checkCount = 0;
            const maxCheckCount = 20;
            const checkInterval = setInterval(() => {
                checkCount++;
                if (StateManager.getState().isChatRoomsLoaded) {
                    clearInterval(checkInterval);
                    console.log(`[DEBUG] Chat list loaded. Reopening chat ${initialChatId}...`);
                    this.openChatById(initialChatId);
                } else if (checkCount >= maxCheckCount) {
                    clearInterval(checkInterval);
                    console.warn("[DEBUG] Waited too long for chat list load. Resetting state.");
                    StateManager.closeChatRoom(); // Reset state if reopen fails
                } else { /* Wait */ }
            }, 500);
        }

        // Add visibility listener
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'visible' && StateManager.getState().isChatRoomOpen) {
                this.markCurrentChatAsRead();
            }
        });
        console.log("Chat App Initialization sequence complete.");
    },

    async openChatById(chatId) {
        const numChatId = Number(chatId);
        console.log(`[DEBUG] Start openChatById for ID: ${numChatId}`);
        if (StateManager.getState().isChatOpening) { console.warn("Chat opening already in progress."); return; }
        StateManager.setState({ isChatOpening: true });
        let chat = StateManager.findChatRoom(numChatId);
        if (!chat) {
            if (!StateManager.getState().isChatRoomsLoaded) {
                console.warn(`Chat list not loaded. Refreshing.`); UIManager.showError("목록 로딩 중..."); WebSocketManager.refreshChatRooms();
            } else {
                console.warn(`Chat ${numChatId} not found in cache.`); UIManager.showError("채팅방 정보 없음.");
            }
            StateManager.setState({ isChatOpening: false }); return;
        }
        console.log(`[DEBUG] Opening chat: ${chat.name || chat.id}`);
        try {
            await UIManager.openPersonalChat(chat, StateManager.getState().currentUser);
            console.log(`[DEBUG] openPersonalChat completed for ${numChatId}`);
        } catch (error) {
            console.error(`[DEBUG] Error during openPersonalChat for ${numChatId}`, error);
        } finally {
            StateManager.setState({ isChatOpening: false });
            console.log(`[DEBUG] Finished openChatById attempt for ${numChatId}`);
        }
    },

    handleIncomingMessage(message) {
        console.log('[DEBUG] Handling incoming message:', JSON.stringify(message));
        if (!message?.id || !message.chatRoomId) return;
        StateManager.updateChatRoomWithMessage(message); // Update last message info
        const state = StateManager.getState();
        if (state.isChatRoomOpen && Number(message.chatRoomId) === state.currentChatRoomId) {
            if (!StateManager.hasRenderedMessage(message.chatRoomId, message.id)) {
                UIManager.renderMessage(message, 'append');
                UIManager.scrollToBottom();
                setTimeout(() => {
                    if (document.visibilityState === 'visible') {
                        this.markMessageAsRead(message.chatRoomId, message.id);
                    }
                }, 500);
            }
        } else { /* Update unread count via server push ('readUpdate') */ }
    },

    handleIncomingNotification(notification) {
        UIManager.showPushNotification(notification);
    },

    sendMessage() {
        const now = Date.now();
        if (now - this.lastSendTime < CONSTANTS.SEND_RATE_LIMIT) {
            UIManager.showError("메시지를 너무 빨리 보낼 수 없습니다.");
            return;
        }

        const content = DOMElements.messageInput?.value?.trim() ?? '';
        const chatId = StateManager.getState().currentChatRoomId;

        if (!chatId || !content) {
            console.warn("Cannot send empty message or no chat selected.");
            return;
        }

        // --- Optimistic UI를 위한 데이터 준비 ---
        const currentUserUuid = StateManager.getState().currentUser;
        let senderName = "나"; // 기본값
        let senderProfileImage = null; // 기본값

        // 현재 채팅방 정보에서 내 정보 찾아보기 (이름, 프로필 이미지)
        const currentChat = StateManager.findChatRoom(chatId);
        if (currentChat && currentChat.participants) {
            const currentUserInfo = currentChat.participants.find(p => p.uuid === currentUserUuid);
            if (currentUserInfo) { senderName = currentUserInfo.name; senderProfileImage = currentUserInfo.profileImage; }
        }
        // 참고: 더 좋은 방법은 StateManager에 현재 로그인 사용자 프로필 정보를 별도로 저장해두는 것입니다.

        // 화면에 즉시 표시할 임시 메시지 객체 생성
        // ID는 임시 값을 사용하고, timestamp는 현재 시간 사용 (서버 시간과 약간 다를 수 있음)
        const optimisticMessage = {
            id: `temp_${Date.now()}`,
            chatRoomId: chatId,
            sender: { uuid: currentUserUuid, name: senderName, profileImage: senderProfileImage },
            content: content,
            timestamp: new Date().toISOString(),
            type: CONSTANTS.MESSAGE_TYPE.NORMAL
        };
        // --- 데이터 준비 끝 ---


        // 서버로 메시지 전송 시도
        if (WebSocketManager.sendMessage(chatId, content)) {
            this.lastSendTime = now;

            // --- ★ Optimistic UI Update ★ ---
            // 메시지 전송 성공(시도) 시 즉시 화면에 렌더링
            console.log("[DEBUG] Rendering optimistic message:", optimisticMessage);
            UIManager.renderMessage(optimisticMessage, 'append');
            UIManager.scrollToBottom(true); // 메시지 추가 후 스크롤 맨 아래로
            // --- ★ 업데이트 끝 ★ ---
            // --- *** 2. 채팅 목록 캐시 즉시 업데이트 추가 *** ---
            if (currentChat) { // 위에서 찾은 chat 객체 사용
                // 마지막 메시지 정보 업데이트
                currentChat.lastMessageSender = {
                    uuid: optimisticMessage.sender.uuid,
                    name: optimisticMessage.sender.name,
                    lastMessage: optimisticMessage.content,
                    // ISO 문자열 -> epoch milliseconds 또는 Date 객체로 변환 저장 필요할 수 있음
                    lastMessageTime: new Date(optimisticMessage.timestamp).getTime()
                };
                // 정렬 기준 시간도 업데이트
                currentChat.lastMessageTime = new Date(optimisticMessage.timestamp).getTime();
                console.log(`[DEBUG] Optimistically updated chatRoomsCache for chat ${chatId} with new last message.`);

                // 캐시 업데이트 후 즉시 목록 갱신 (선택적, 성능 영향 고려)
                // if (StateManager.getState().isChatOpen && !StateManager.getState().isChatRoomOpen) {
                //     UIManager.renderChatList(StateManager.getFilteredChatRooms(), StateManager.getState().currentUser, false);
                // }
            }
            // --- *** 캐시 업데이트 끝 *** ---
            // 입력창 비우고 포커스
            if (DOMElements.messageInput) {
                DOMElements.messageInput.value = '';
                DOMElements.messageInput.focus();
            }

        } else {
            UIManager.showError("메시지 전송 실패. 연결을 확인하세요.");
            // 참고: 전송 실패 시 낙관적으로 추가한 메시지를 제거하는 로직 추가 가능 (더 복잡)
        }

        // 전송 시도 후 입력 상태 재확인 (이전 안전장치 코드, 필요 없을 수 있음)
        // const currentChatForState = StateManager.findChatRoom(chatId);
        // if (currentChatForState) {
        //     UIManager.updateChatInputState(currentChatForState);
        // }
    },

    markMessageAsRead(chatRoomId, messageId) {
        const numChatId = Number(chatRoomId);
        if (!numChatId || !messageId) return;
        const markedSet = this.markedMessageIds.get(numChatId);
        if (markedSet?.has(messageId)) return;
        if (!StateManager.getState().isChatRoomOpen || StateManager.getState().currentChatRoomId !== numChatId) return;
        if (WebSocketManager.markMessageAsRead(numChatId, messageId)) {
            if (!markedSet) this.markedMessageIds.set(numChatId, new Set([messageId]));
            else markedSet.add(messageId);
        }
    },

    clearMarkedMessages(chatRoomId) {
        const numChatRoomId = Number(chatRoomId);
        this.markedMessageIds.delete(numChatRoomId);
        console.log(`[DEBUG] Cleared marked message IDs for chat room: ${numChatRoomId}`);
    },

    markCurrentChatAsRead() {
        const chatId = StateManager.getState().currentChatRoomId;
        if (chatId && StateManager.getState().isChatRoomOpen) {
            WebSocketManager.markAllMessagesAsRead(chatId);
            this.clearMarkedMessages(chatId);
        }
    },

    loadPreviousMessages: Utils.debounce(async () => {
        const state = StateManager.getState();
        const chatId = state.currentChatRoomId;
        if (!chatId || state.isLoading || StateManager.isFetchingPrevious(chatId)) return;
        const oldestPage = StateManager.getOldestPageLoaded(chatId);
        // console.log(`Current oldest page for chat ${chatId}: ${oldestPage}`);
        if (oldestPage === undefined || oldestPage === null || oldestPage <= 0) { console.log("Already at oldest page."); return; }
        const pageToFetch = oldestPage - 1;
        StateManager.setFetchingPrevious(chatId, true);
        StateManager.setLoading(true);
        try {
            console.log(`Workspaceing previous messages for chat ${chatId}, page ${pageToFetch}`);
            const oldMessages = await WebSocketManager.fetchMessagesForChat(chatId, pageToFetch);
            if (oldMessages && oldMessages.length > 0) {
                const container = DOMElements.messagesContainer; if (!container) throw new Error("Msg container not found");
                const prevHeight = container.scrollHeight, prevScroll = container.scrollTop;
                oldMessages.reverse().forEach(msg => { UIManager.renderMessage(msg, 'prepend'); });

                const sentinel = document.getElementById('message-load-sentinel');
                if (sentinel && container.firstChild !== sentinel) {
                    console.log("[DEBUG] Re-prepending sentinel after loading messages.");
                    // sentinel 요소를 찾아서 다시 맨 앞에 추가 (DOM에서 자동으로 위치 이동)
                    container.prepend(sentinel);
                }

                container.scrollTop = prevScroll + (container.scrollHeight - prevHeight); // Maintain scroll position
                StateManager.setOldestPageLoaded(chatId, pageToFetch);
            } else {
                console.log(`No more messages on page ${pageToFetch}.`); StateManager.setOldestPageLoaded(chatId, 0);
            }
        } catch (error) { console.error("Failed to load previous:", error); UIManager.showError("이전 메시지 로딩 실패"); }
        finally { StateManager.setFetchingPrevious(chatId, false); StateManager.setLoading(false); }
    }, 500), // Debounce load requests
    async searchUser() {
        if (!DOMElements.userSearchInput) return;
        const query = DOMElements.userSearchInput.value.trim();
        if (!query) {
            UIManager.renderSearchResults([]); // 입력 없으면 결과 비우기
            return;
        }

        console.log(`[DEBUG] Searching for user with UUID: ${query}`);
        StateManager.setLoading(true); // 검색 중 로딩 표시 (선택적)

        try {
            // 서버 API 호출 (fetch 사용)
            const response = await fetch(`/api/users/search?uuid=${encodeURIComponent(query)}`);
            if (!response.ok) {
                // 4xx, 5xx 에러 처리
                throw new Error(`사용자 검색 실패 (${response.status})`);
            }
            const users = await response.json();
            console.log("[DEBUG] Search results received:", users);
            UIManager.renderSearchResults(users); // 결과 렌더링

        } catch (error) {
            console.error("Error searching user:", error);
            UIManager.showError(error.message || "사용자 검색 중 오류 발생");
            UIManager.renderSearchResults([]); // 오류 시 결과 비우기
        } finally {
            StateManager.setLoading(false); // 로딩 종료
        }
    },
};

// --- Initialization ---
ChatApp.init(); // Start the application

// Optional: Clean disconnect on page unload
// window.addEventListener('beforeunload', () => { WebSocketManager.disconnect(); });