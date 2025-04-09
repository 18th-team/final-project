/**
 * chat.js
 * WebSocket 및 STOMP를 사용한 실시간 채팅 기능 구현
 * 오류 수정 및 비동기 처리 개선 버전
 */
const chatApp = (function() {
    // 상태 변수 및 상수
    let stompClient = null;
    let activeTab = 'PRIVATE'; // 기본 탭
    let currentChatRoomId = null;
    let chatRoomsCache = []; // 채팅방 목록 캐시
    let isConnected = false; // 서버 연결 상태
    let isScrollable = true; // 메시지 목록 자동 스크롤 여부
    let retryCount = 0; // 연결 재시도 횟수
    let currentUser = null; // 현재 사용자 UUID (서버로부터 받음)
    const maxRetries = 5; // 최대 재시도 횟수
    const MAX_MESSAGE_LENGTH = 1000; // 메시지 최대 길이
    const CONNECTION_TIMEOUT = 10000; // 연결 시도 타임아웃 (10초)
    let lastSendTime = 0; // 마지막 메시지 전송 시간 (속도 제한용)
    const sendRateLimit = 1000; // 메시지 전송 최소 간격 (1초)
    let lastConnectTime = 0; // 마지막 연결 시도 시간 (속도 제한용)
    const connectRateLimit = 2000; // 연결 시도 최소 간격 (2초)
    let isChatOpening = false; // 채팅방 열기 작업 진행 중 플래그
    let lastMarkTime = 0; // 마지막 읽음 처리 시간 (쿨다운용)
    const markCooldown = 1000; // 읽음 처리 최소 간격 (1초)
    let renderedMessageIds = new Map(); // <chatRoomId, Set<messageId>> 렌더링된 메시지 추적
    let currentPage = 0; // 현재 로드된 메시지 페이지 (채팅방별 관리 필요 시 개선)
    const pageSize = 50; // 한 페이지당 메시지 수
    let state = { // UI 상태 관리
        isChatOpen: false,      // 전체 채팅 창 열림 여부
        isChatRoomOpen: false,  // 개별 채팅방 열림 여부
        isLoading: false        // 로딩 중 상태 (API 호출 등)
    };

    // 로컬 스토리지에 채팅 상태 저장
    function saveChatState() {
        try {
            const chatState = { isChatOpen: state.isChatOpen, isChatRoomOpen: state.isChatRoomOpen, currentChatRoomId, activeTab };
            localStorage.setItem('chatState', JSON.stringify(chatState));
        } catch (e) {
            console.error("Failed to save chat state to localStorage:", e);
        }
    }

    // 로컬 스토리지에서 채팅 상태 로드
    function loadChatState() {
        try {
            const savedState = localStorage.getItem('chatState');
            if (savedState) {
                const parsedState = JSON.parse(savedState);
                // 저장된 상태 복원, 유효성 검사 추가 가능
                state.isChatOpen = !!parsedState.isChatOpen;
                state.isChatRoomOpen = !!parsedState.isChatRoomOpen;
                currentChatRoomId = parsedState.currentChatRoomId || null;
                activeTab = parsedState.activeTab || 'PRIVATE';
                // 만약 채팅방이 열린 상태로 로드되면, 목록 상태는 닫힌 것으로 간주
                if (state.isChatRoomOpen) {
                    state.isChatOpen = true; // 전체 창은 열린 상태여야 함
                }
            }
        } catch (e) {
            console.error('Failed to load chat state from localStorage:', e);
            // 로드 실패 시 기본값 설정
            state.isChatOpen = false;
            state.isChatRoomOpen = false;
            currentChatRoomId = null;
            activeTab = 'PRIVATE';
            localStorage.removeItem('chatState'); // 잘못된 데이터 제거
        }
    }

    /**
     * 서버 연결 함수 (async/await 적용 및 Promise 반환)
     * WebSocket 및 STOMP 연결을 시도하고, 성공 시 resolve, 실패 시 reject하는 Promise를 반환합니다.
     */
    async function connect() {
        // 연결 시도 속도 제한 및 이미 연결된 경우 처리
        if (Date.now() - lastConnectTime < connectRateLimit) {
            console.log("연결 시도 간격이 너무 짧습니다.");
            return Promise.reject(new Error("연결 시도 간격 제한")); // Promise reject
        }
        if (stompClient && stompClient.connected) {
            console.log("이미 연결되어 있습니다.");
            if (!state.isChatRoomOpen) refreshChatRooms(); // 연결 상태면 목록 갱신 시도
            return Promise.resolve(); // 이미 연결된 경우 즉시 resolve
        }

        // 이전 클라이언트 정리 (재연결 시)
        if (stompClient) {
            try {
                await stompClient.deactivate(); // 비동기 비활성화
                console.log("Previous STOMP client deactivated.");
            } catch (deactivateError) { console.warn("이전 STOMP 클라이언트 비활성화 실패:", deactivateError); }
            stompClient = null;
        }

        state.isLoading = true;
        updateChatUI(); // 로딩 상태 UI 반영

        // Promise를 반환하여 비동기 연결 과정을 관리
        return new Promise((resolve, reject) => {
            let socket = null; // socket 참조 추가
            try {
                // SockJS 사용 (호환성을 위해 transports 옵션 제거 권장)
                socket = new SockJS('/chat');
                stompClient = Stomp.over(socket);

                // *** 중요: 서버 설정과 일치하도록 하트비트 수정 (5초로 가정) ***
                stompClient.heartbeat = { outgoing: 5000, incoming: 5000 };

                // 디버깅 로그 (개발 중 유용)
                // stompClient.debug = (str) => console.log('STOMP Debug:', str);

                // WebSocket 연결 종료 처리 (연결 시도 전에 핸들러 설정)
                stompClient.onWebSocketClose = (event) => {
                    console.warn('WebSocket closed:', { code: event?.code, reason: event?.reason, wasClean: event?.wasClean, timestamp: new Date().toISOString() });
                    isConnected = false;
                    showError("연결이 끊겼습니다. 1초 후 재연결을 시도합니다.");
                    // 재연결 시도 (단순 1초 지연, handleConnectionError 사용 고려)
                    setTimeout(connect, 1000);
                    // 현재 진행 중인 connect Promise는 reject하지 않음 (자동 재시도)
                };

                // STOMP 프로토콜 레벨 에러 처리
                stompClient.onStompError = (frame) => {
                    console.error('STOMP Error:', frame);
                    const errorMessage = frame.headers?.message || 'STOMP 프로토콜 오류 발생';
                    showError(errorMessage);
                    isConnected = false;
                    updateChatUI();
                    // 연결 실패로 간주하고 Promise reject
                    reject(new Error(errorMessage));
                };

                // 연결 타임아웃 타이머 설정
                const timeout = setTimeout(() => {
                    if (state.isLoading && !isConnected) { // 로딩 중이고 아직 연결 안됨
                        console.error("서버 연결 시간이 초과되었습니다.");
                        try {
                            // 연결 시도 중단
                            if (stompClient && typeof stompClient.deactivate === 'function') {
                                stompClient.deactivate();
                            } else if (socket && typeof socket.close === 'function') {
                                socket.close();
                            }
                        } catch (e) { console.warn("Timeout connection cleanup error:", e); }
                        state.isLoading = false;
                        updateChatUI();
                        reject(new Error("서버 연결 시간이 초과되었습니다.")); // Promise reject
                    }
                }, CONNECTION_TIMEOUT);

                // STOMP 연결 시도
                console.log("Attempting STOMP connection...");
                lastConnectTime = Date.now(); // 연결 시도 시간 기록

                stompClient.connect(
                    {}, // 헤더 (필요시 인증 정보 추가)
                    (frame) => { // 연결 성공 콜백
                        clearTimeout(timeout); // 타임아웃 타이머 제거
                        if (isConnected) { // 중복 콜백 방지
                            console.warn("중복 연결 성공 콜백 감지됨.");
                            resolve(frame); // 이미 성공 처리됨
                            return;
                        }
                        isConnected = true;
                        retryCount = 0; // 재시도 횟수 초기화
                        state.isLoading = false;
                        currentUser = frame.headers['user-name']; // 사용자 ID 설정
                        console.log('STOMP Connected. User:', currentUser);

                        if (!currentUser) { // 사용자 ID 없으면 에러 처리
                            console.error("사용자 인증 정보를 받지 못했습니다. 로그인 페이지로 이동합니다.");
                            window.location.href = "/login"; // 로그인 페이지 이동
                            reject(new Error("사용자 인증 실패"));
                            return;
                        }

                        // 연결 성공 후 작업
                        subscribeToTopics(); // STOMP 토픽 구독
                        refreshChatRooms(); // 채팅방 목록 요청
                        updateChatUI(); // UI 상태 업데이트

                        // *** 불필요한 setInterval 제거됨 ***

                        resolve(frame); // 연결 성공 Promise resolve
                    },
                    (error) => { // 연결 실패 콜백 (STOMP 레벨)
                        clearTimeout(timeout); // 타임아웃 타이머 제거
                        console.error('STOMP Connection Error Callback:', error);
                        isConnected = false;
                        state.isLoading = false;
                        updateChatUI();
                        reject(error); // 연결 실패 Promise reject
                    }
                );

            } catch (error) { // new SockJS() 또는 Stomp.over() 등 동기적 오류 처리
                console.error("Connection setup error:", error);
                isConnected = false;
                state.isLoading = false;
                updateChatUI();
                reject(error); // 설정 실패 Promise reject
            }
        }) // End of Promise returned by connect
            .catch(error => { // Promise .catch() 블록: 연결 실패 최종 처리
                // handleConnectionError 헬퍼 함수를 사용하여 재시도 로직 실행
                handleConnectionError(error);
                // 상위 호출자에게도 에러 전파 (필요시)
                throw error; // 또는 return Promise.reject(error);
            });
    }

    // 연결 에러 처리 및 재시도 헬퍼 함수
    function handleConnectionError(error) {
        isConnected = false;
        state.isLoading = false;
        // showError 함수가 null/undefined 에러 메시지를 처리하도록 개선 필요
        showError(`연결 실패: ${error?.message || '알 수 없는 오류'}`);
        updateChatUI();

        if (retryCount < maxRetries) {
            retryCount++;
            const delay = 1000 * retryCount; // 지수 백오프
            console.log(`${delay / 1000}초 후 재연결 시도... (${retryCount}/${maxRetries})`);
            setTimeout(connect, delay); // 지연 후 connect 재호출
        } else {
            showError("최대 재시도 횟수를 초과했습니다. 잠시 후 로그인 페이지로 이동합니다.", 10000);
            console.error("최대 재시도 횟수 초과. 로그인 페이지로 이동.");
            // Stomp 클라이언트 정리
            try {
                if (stompClient && typeof stompClient.deactivate === 'function') stompClient.deactivate();
            } catch(e) { console.warn("Final deactivate error:", e); }
            // 로그인 페이지로 이동
            setTimeout(() => { window.location.href = "/login"; }, 3000);
        }
    }

    // 온라인 상태 확인 요청
    function checkOnlineStatus(chatRoomId) {
        if (stompClient?.connected && chatRoomId && currentUser) {
            stompClient.send("/app/onlineStatus", {}, JSON.stringify({ chatRoomId: chatRoomId }));
        }
    }

    // 온라인 상태 UI 업데이트
    function updateOnlineStatus(uuid, lastOnlineTimestamp, isOnline) {
        // console.log(`Updating status - uuid: ${uuid}, isOnline: ${isOnline}, lastOnline: ${lastOnlineTimestamp}`);
        // 개인 채팅방 헤더 상태 업데이트
        if (state.isChatRoomOpen && currentChatRoomId) {
            const chat = chatRoomsCache.find(c => c.id === currentChatRoomId);
            const targetUuid = chat?.type === 'PRIVATE' ? (chat.requester?.uuid === currentUser ? chat.owner?.uuid : chat.requester?.uuid) : null;
            if (targetUuid === uuid) {
                const statusElement = document.querySelector('.personal-chat .chat-status'); // 개인 채팅 헤더
                if (statusElement) {
                    if (isOnline) {
                        statusElement.textContent = '온라인';
                        statusElement.style.color = '#00cc00';
                    } else {
                        const lastOnlineDate = lastOnlineTimestamp ? new Date(lastOnlineTimestamp) : null;
                        statusElement.textContent = lastOnlineDate ? `마지막 접속 ${formatTimeAgo(lastOnlineDate)}` : '오프라인';
                        statusElement.style.color = '#666';
                    }
                    // console.log(`Updated chat status in header: ${statusElement.textContent}`);
                }
                // 개인 채팅 헤더 아바타 옆 상태 표시기 업데이트
                const headerIndicator = document.querySelector('.personal-chat .status-indicator');
                if(headerIndicator) headerIndicator.style.backgroundColor = isOnline ? '#00cc00' : '#666';
            }
        }

        // 채팅 목록의 상태 표시기 업데이트
        const listIndicators = document.querySelectorAll(`#chatList .status-indicator[data-uuid="${uuid}"]`); // 목록 내 표시기
        listIndicators.forEach(indicator => {
            indicator.style.backgroundColor = isOnline ? '#00cc00' : '#666';
            // console.log(`Updated list indicator for UUID: ${uuid} to color: ${indicator.style.backgroundColor}`);
        });
    }

    // 시간 포맷 함수 (예: '5분 전')
    function formatTimeAgo(date) {
        const now = new Date();
        const diffMs = now - date;
        const diffSeconds = Math.round(diffMs / 1000);
        const diffMins = Math.round(diffSeconds / 60);
        const diffHours = Math.round(diffMins / 60);
        const diffDays = Math.round(diffHours / 24);

        if (diffSeconds < 60) return `방금 전`;
        if (diffMins < 60) return `${diffMins}분 전`;
        if (diffHours < 24) return `${diffHours}시간 전`;
        return `${diffDays}일 전`;
    }

    // 총 메시지 수 요청 (Promise 및 타임아웃 개선)
    function getMessageCount(chatId) {
        return new Promise((resolve, reject) => {
            if (!chatId || !stompClient?.connected || !currentUser) {
                return reject(new Error('Chat ID, connection, or user not available'));
            }

            const replyTo = `/user/${currentUser}/topic/messageCount/${chatId}_${Date.now()}`; // 더 고유한 응답 토픽
            let subscription = null;
            const timeoutDuration = 10000; // 10초 응답 타임아웃

            const timeout = setTimeout(() => {
                if (subscription) {
                    subscription.unsubscribe();
                    console.warn(`getMessageCount timed out for chat ${chatId}`);
                    reject(new Error('메시지 개수 요청 시간 초과'));
                }
            }, timeoutDuration);

            subscription = stompClient.subscribe(replyTo, message => {
                clearTimeout(timeout);
                if (subscription) subscription.unsubscribe();
                try {
                    const count = JSON.parse(message.body);
                    resolve(parseInt(count) || 0); // 숫자 변환 및 기본값 0
                } catch (e) { reject(new Error('Failed to parse message count response')); }
            }, error => { // 구독 실패 시
                clearTimeout(timeout);
                if (subscription) subscription.unsubscribe();
                console.error('Error subscribing to message count reply:', error);
                reject(error);
            });

            try {
                stompClient.send("/app/getMessageCount", { "reply-to": replyTo }, JSON.stringify({ id: chatId }));
            } catch (sendError) {
                clearTimeout(timeout);
                if (subscription) subscription.unsubscribe();
                reject(sendError);
            }
        });
    }

    // STOMP 토픽 구독
    function subscribeToTopics() {
        if (!stompClient || !currentUser || !stompClient.connected) {
            console.error("Cannot subscribe to topics: client not ready.");
            return;
        }
        // TODO: 기존 구독이 있다면 해제하는 로직 추가 (unsubscribeAll())

        // 채팅방 목록 구독
        stompClient.subscribe(`/user/${currentUser}/topic/chatrooms`, message => {
            try {
                const receivedRooms = JSON.parse(message.body);
                // 서버 응답이 항상 배열인지 확인
                chatRoomsCache = Array.isArray(receivedRooms) ? receivedRooms : [];
                renderChatList(chatRoomsCache);
                // 현재 열린 채팅방 정보 갱신 (목록에서 사라졌거나 정보 변경 시)
                if (state.isChatRoomOpen && currentChatRoomId) {
                    const currentChatInfo = chatRoomsCache.find(c => c.id === currentChatRoomId);
                    if (currentChatInfo) {
                        updateChatWindowHeader(currentChatInfo); // 헤더 정보 업데이트
                        // 채팅방 상태(CLOSED, BLOCKED 등) 변경 시 입력창 상태 업데이트
                        const messageInput = document.querySelector('.personal-chat .message-input');
                        const sendButton = document.querySelector('.personal-chat .send-button');
                        updateChatInput(currentChatInfo, messageInput, sendButton);
                    } else {
                        // 현재 열린 채팅방이 목록에 없음 (삭제됨)
                        showError("현재 채팅방 정보를 찾을 수 없습니다. 목록으로 돌아갑니다.");
                        resetChatWindow();
                    }
                }
                updateChatUI();
            } catch (e) { console.error("Failed to process chatrooms message:", e); }
        });

        // 새 메시지 수신 구독
        stompClient.subscribe(`/user/${currentUser}/topic/messages`, message => {
            try {
                const item = JSON.parse(message.body);
                handleMessage(item);
            } catch (e) { console.error("Failed to process incoming message:", e); }
        });

        // 서버 에러 메시지 구독
        stompClient.subscribe(`/user/${currentUser}/topic/errors`, message => {
            showError(message.body || "서버에서 오류가 발생했습니다.");
        });

        // 읽음 처리 상태 업데이트 구독
        stompClient.subscribe(`/user/${currentUser}/topic/readUpdate`, message => {
            try {
                const update = JSON.parse(message.body);
                // 캐시 업데이트
                const chat = chatRoomsCache.find(c => c.id === update.chatRoomId);
                if(chat) chat.unreadCount = update.unreadCount;
                // UI 업데이트
                updateUnreadCountUI(update.chatRoomId, update.unreadCount);
            } catch (e) { console.error("Failed to process read update:", e); }
        });

        // 푸시 알림용 메시지 구독
        stompClient.subscribe(`/user/${currentUser}/topic/notifications`, message => {
            try {
                const update = JSON.parse(message.body);
                handleNotification(update);
            } catch (e) { console.error("Failed to process notification:", e); }
        });

        // 채팅방 알림 설정 변경 구독
        stompClient.subscribe(`/user/${currentUser}/topic/notificationUpdate`, message => {
            try {
                const update = JSON.parse(message.body);
                const chat = chatRoomsCache.find(c => c.id === update.chatRoomId);
                if (chat) {
                    chat.notificationEnabled = update.notificationEnabled; // 캐시 업데이트
                    // 현재 열린 채팅방이면 토글 UI 업데이트
                    if (currentChatRoomId === update.chatRoomId && state.isChatRoomOpen) {
                        updateNotificationToggle();
                    }
                }
            } catch (e) { console.error("Failed to process notification update:", e); }
        });

        // 온라인 상태 변경 구독
        stompClient.subscribe(`/user/${currentUser}/topic/onlineStatus`, message => {
            try {
                const status = JSON.parse(message.body);
                updateOnlineStatus(status.uuid, status.lastOnline, status.isOnline);
            } catch (e) { console.error("Failed to process online status:", e); }
        });
        console.log("Subscribed to STOMP topics");
    }
    // TODO: 구독 해제 함수
    // function unsubscribeAll() { ... }


    // 채팅방 목록 새로고침 요청
    function refreshChatRooms() {
        if (stompClient?.connected && currentUser) {
            stompClient.send("/app/refreshChatRooms", {}, JSON.stringify({ uuid: currentUser }));
        }
    }

    // 특정 채팅방 메시지 가져오기 (Promise 및 타임아웃 개선)
    function refreshMessages(chatId = currentChatRoomId, page = 0) { // page 기본값 0
        return new Promise((resolve, reject) => {
            if (!chatId || !stompClient?.connected || !currentUser) {
                return reject(new Error('Chat ID, connection, or user not available'));
            }

            state.isLoading = true; // 로딩 시작 (UI 피드백 추가 필요)
            // 응답 받을 고유 토픽 생성 (UUID나 타임스탬프 사용 가능)
            const replyTo = `/user/${currentUser}/topic/messages/${chatId}/page/${page}_${Date.now()}`;
            let subscription = null;
            const timeoutDuration = 15000; // 15초 타임아웃

            const timeout = setTimeout(() => {
                if (subscription) {
                    subscription.unsubscribe();
                    console.warn(`refreshMessages timed out for chat ${chatId}, page ${page}`);
                    state.isLoading = false;
                    reject(new Error('메시지 로딩 시간 초과'));
                }
            }, timeoutDuration);

            subscription = stompClient.subscribe(replyTo, message => {
                clearTimeout(timeout);
                if (subscription) subscription.unsubscribe();
                state.isLoading = false;
                try {
                    const items = JSON.parse(message.body);
                    // 서버가 항상 배열 반환 가정, 아니면 빈 배열 처리
                    resolve(Array.isArray(items) ? items : []);
                } catch (e) { reject(new Error('Failed to parse messages response')); }
            }, error => { // 구독 실패
                clearTimeout(timeout);
                if (subscription) subscription.unsubscribe();
                state.isLoading = false;
                console.error(`Error subscribing for messages chat ${chatId}, page ${page}:`, error);
                reject(error);
            });

            // 메시지 요청 전송
            try {
                stompClient.send("/app/getMessages", { "reply-to": replyTo }, JSON.stringify({ id: chatId, page, size: pageSize }));
                // console.log(`Requesting messages for chat ${chatId}, page ${page}`);
            } catch (sendError) {
                clearTimeout(timeout);
                if (subscription) subscription.unsubscribe();
                state.isLoading = false;
                reject(sendError);
            }
        });
    }

    // 메시지 읽음 처리 요청
    function markMessagesAsRead() {
        if (Date.now() - lastMarkTime < markCooldown || !stompClient?.connected || !currentChatRoomId || !state.isChatRoomOpen || !currentUser) return;

        stompClient.send("/app/markMessagesAsRead", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
        lastMarkTime = Date.now();

        // UI 즉시 업데이트 (서버 응답 기다리지 않음)
        const chat = chatRoomsCache.find(c => c.id === currentChatRoomId);
        if (chat) {
            chat.unreadCount = 0; // 캐시 업데이트
            updateUnreadCountUI(currentChatRoomId, 0); // UI 업데이트
        }
    }

    // 수신 메시지 처리 (렌더링 및 상태 업데이트)
    function handleMessage(item) {
        if (!item || !item.chatRoomId || !item.id) { // 메시지 ID 확인 추가
            console.warn('Received invalid message item:', item);
            return;
        }
        // console.log('Handling message:', item.id);

        // 해당 채팅방의 renderedMessageIds Set 가져오기 (없으면 생성)
        if (!renderedMessageIds.has(item.chatRoomId)) {
            renderedMessageIds.set(item.chatRoomId, new Set());
        }
        const messageIds = renderedMessageIds.get(item.chatRoomId);

        // 이미 처리(렌더링)된 메시지인지 확인
        if (!messageIds.has(item.id)) {
            messageIds.add(item.id); // 처리하기 전에 ID 추가

            // 현재 열린 채팅방이면 렌더링
            if (item.chatRoomId === currentChatRoomId && state.isChatRoomOpen) {
                renderMessage(item, 'append'); // 항상 맨 아래 추가
                markMessagesAsRead(); // 새 메시지 수신 시 읽음 처리 시도
            }

            // 채팅방 캐시 업데이트 (마지막 메시지, 시간)
            const chat = chatRoomsCache.find(c => c.id === item.chatRoomId);
            if (chat && item.type !== 'SYSTEM') { // 시스템 메시지 제외
                chat.lastMessage = item.content;
                chat.lastMessageTime = item.timestamp;
                // 읽지 않음 카운트 증가 (내가 보낸 메시지 제외) - 서버 readUpdate로 처리하는게 더 정확할 수 있음
                if(item.sender?.uuid !== currentUser) {
                    chat.unreadCount = (chat.unreadCount || 0) + 1;
                    updateUnreadCountUI(chat.id, chat.unreadCount);
                }

                // 채팅 목록 실시간 업데이트 (Debounce/Throttle 고려)
                if (state.isChatOpen && !state.isChatRoomOpen) {
                    // 성능 위해 잠시 후 한 번만 실행하도록 개선 가능
                    renderChatList(chatRoomsCache);
                }
            }
        } else {
            // console.log(`Message ${item.id} already processed, skipping.`);
        }
    }

    // 읽지 않은 메시지 수 UI 업데이트 함수
    function updateUnreadCountUI(chatRoomId, unreadCount) {
        // 채팅 목록 아이템 찾기 (data-chatroom-id 속성 사용)
        const chatItem = document.querySelector(`#chatList .chat-item[data-chatroom-id="${chatRoomId}"]`);
        if (chatItem) {
            const unreadSpan = chatItem.querySelector('.unread-count');
            if (unreadSpan) {
                const count = parseInt(unreadCount) || 0;
                unreadSpan.textContent = count > 0 ? count : '';
                unreadSpan.style.display = count > 0 ? 'inline-block' : 'none';
            }
        }
        // 탭 옆의 총 개수도 업데이트
        updateTotalUnreadCounts();
    }

    // 탭 옆 총 읽지 않은 메시지 수 업데이트
    function updateTotalUnreadCounts() {
        let groupUnread = 0;
        let personalUnread = 0;
        // 캐시 기준 합산 (UI 동기화 위해)
        chatRoomsCache.forEach(chat => {
            if (chat.type === 'GROUP') groupUnread += (chat.unreadCount || 0);
            else personalUnread += (chat.unreadCount || 0);
        });

        const groupElement = document.getElementById('groupUnreadCount');
        const personalElement = document.getElementById('personalUnreadCount');
        // textContent는 문자열이므로, 숫자가 0일 때 빈 문자열로 설정
        if (groupElement) groupElement.textContent = groupUnread > 0 ? String(groupUnread) : '';
        if (personalElement) personalElement.textContent = personalUnread > 0 ? String(personalUnread) : '';
    }

    // 채팅 요청 처리 (승인/거부/차단)
    function handleRequest(chatId, action) {
        if (!chatId || !action || !stompClient?.connected || !currentUser) return;

        stompClient.send("/app/handleChatRequest", {}, JSON.stringify({ chatRoomId: chatId, action }));

        // 낙관적 UI 업데이트
        const chatIndex = chatRoomsCache.findIndex(c => c.id === chatId);
        if (chatIndex > -1) {
            const targetChat = chatRoomsCache[chatIndex];
            if (action === 'APPROVE') {
                targetChat.status = 'ACTIVE'; // 상태 변경
                renderChatList(chatRoomsCache); // 목록 업데이트
                openPersonalChat(targetChat); // 승인 후 바로 채팅방 열기
            } else { // REJECT or BLOCK
                // 목록에서 제거
                chatRoomsCache.splice(chatIndex, 1);
                renderChatList(chatRoomsCache); // 목록 업데이트
                // 현재 열려있던 채팅방이면 닫기
                if (chatId === currentChatRoomId) {
                    resetChatWindow();
                }
            }
        }
    }

    // 푸시 알림용 메시지 처리
    function handleNotification(item) {
        if (!item || !item.chatRoomId || !item.senderName || (item.sender && item.sender.uuid === currentUser)) {
            // console.log('Notification skipped:', item);
            return;
        }
        const chat = chatRoomsCache.find(c => c.id === item.chatRoomId);

        // 알림 설정 ON & (채팅목록 닫힘 OR 다른 채팅방 열림)
        if (chat?.notificationEnabled !== false && (!state.isChatRoomOpen || item.chatRoomId !== currentChatRoomId)) {
            showPushNotification({
                senderName: item.senderName,
                content: item.content,
                timestamp: item.timestamp,
                chatRoomId: item.chatRoomId
                // avatarUrl 등 추가 정보 포함 가능
            });
        }
    }

    // 푸시 알림 UI 표시
    function showPushNotification(notification) {
        const container = document.getElementById('notificationContainer');
        if (!container) return;
        // TODO: 알림 클릭 시 해당 채팅방 이동 기능 추가

        const nameText = container.querySelector('#notificationName');
        const messageText = container.querySelector('#notificationMessage');
        const timestampText = container.querySelector('.timestamp-text');
        const avatarContainer = container.querySelector('#avatarContainer'); // HTML 구조 확인

        nameText.textContent = notification.senderName;
        messageText.textContent = notification.content;
        timestampText.textContent = notification.timestamp ?
            new Date(notification.timestamp).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit', hour12: true }) : "";

        // 아바타 생성 (기존 로직 개선)
        avatarContainer.innerHTML = ''; // 기존 아바타 제거
        const avatarDiv = document.createElement('div');
        avatarDiv.className = 'avatar'; // CSS 클래스 확인
        // 이니셜 또는 이미지 설정
        avatarDiv.textContent = notification.senderName.slice(0, 2).toUpperCase();
        avatarContainer.appendChild(avatarDiv);

        // 알림 보이기 (애니메이션)
        container.style.display = 'block';
        requestAnimationFrame(() => { // 다음 프레임에서 애니메이션 시작
            container.style.opacity = '1';
            container.style.transform = 'translateX(0)';
        });


        // 5초 후 자동으로 사라짐
        // 기존 타이머가 있다면 제거
        if(container.timerId) clearTimeout(container.timerId);
        if(container.removeTimerId) clearTimeout(container.removeTimerId);

        container.timerId = setTimeout(() => {
            container.style.opacity = '0';
            container.style.transform = 'translateX(100%)'; // 오른쪽으로 사라짐
            container.removeTimerId = setTimeout(() => {
                container.style.display = 'none'; // 애니메이션 후 숨김
                container.timerId = null;
                container.removeTimerId = null;
            }, 300); // transition 시간과 일치
        }, 5000);

        // 닫기 버튼 이벤트 (한 번만 설정)
        const closeButton = container.querySelector('.close-button');
        if (closeButton && !closeButton.listenerAdded) {
            closeButton.addEventListener('click', () => {
                if(container.timerId) clearTimeout(container.timerId);
                if(container.removeTimerId) clearTimeout(container.removeTimerId);
                container.style.opacity = '0';
                container.style.transform = 'translateX(100%)';
                setTimeout(() => container.style.display = 'none', 300);
            });
            closeButton.listenerAdded = true; // 리스너 중복 추가 방지
        }
    }


    /**
     * 채팅 목록 렌더링 함수
     * chatRoomsCache 데이터를 기반으로 채팅 목록 UI를 생성합니다.
     * @param {Array} chatRooms - 채팅방 데이터 배열
     */
    function renderChatList(chatRooms) {
        const chatListElement = document.getElementById('chatList');
        if (!chatListElement) return;

        const fragment = document.createDocumentFragment(); // 성능을 위해 DocumentFragment 사용

        // 로딩 상태 또는 빈 목록 처리
        if (state.isLoading && !chatRooms?.length) {
            fragment.innerHTML = '<p class="loading-text">채팅 목록을 불러오는 중...</p>';
        } else if (!chatRooms?.length) {
            fragment.innerHTML = '<p class="empty-list-text">채팅방이 없습니다.</p>';
        } else {
            // 현재 활성화된 탭 기준으로 필터링 및 정렬 (최신 메시지 시간 순)
            const filteredAndSortedRooms = chatRooms
                .filter(chat => activeTab === chat.type) // 활성 탭 필터
                .sort((a, b) => (b.lastMessageTime || 0) - (a.lastMessageTime || 0)); // 시간 내림차순 정렬

            filteredAndSortedRooms.forEach(chat => {
                const item = createChatItemElement(chat); // 채팅 아이템 생성 함수 호출
                fragment.appendChild(item);
            });
        }

        chatListElement.innerHTML = ''; // 기존 목록 비우기
        chatListElement.appendChild(fragment); // 새 목록 추가

        updateTotalUnreadCounts(); // 총 읽지 않음 수 업데이트
    }

    /**
     * 개별 채팅 목록 아이템 HTML 요소를 생성하는 함수
     * @param {object} chat - 채팅방 데이터 객체
     * @returns {HTMLElement} - 생성된 article 요소
     */
    function createChatItemElement(chat) {
        const isRequest = chat.status === 'PENDING';
        const isRequester = chat.requester?.uuid === currentUser;
        const isOwner = chat.owner?.uuid === currentUser;
        const isClosedOrBlocked = chat.status === 'CLOSED' || chat.status === 'BLOCKED';

        const item = document.createElement('article');
        item.dataset.chatroomId = chat.id; // 식별자 추가
        item.className = `chat-item ${isRequest ? 'request-item' : ''} ${isClosedOrBlocked ? 'closed-item' : ''}`;

        // 클릭 이벤트 바인딩 (ACTIVE 상태일 때만)
        if (chat.status === 'ACTIVE') {
            item.addEventListener('click', () => openPersonalChat(chat));
            item.style.cursor = 'pointer';
        } else {
            item.style.cursor = 'default';
        }

        const chatName = chat.type === 'GROUP' ? (chat.name || '그룹 채팅') :
            (isRequester ? chat.owner?.name : chat.requester?.name) || '알 수 없음';
        const lastMsgTimeStr = chat.lastMessageTime ?
            new Date(chat.lastMessageTime).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) : '';
        const unreadCount = chat.unreadCount || 0;

        // 아바타 및 온라인 상태 표시기
        const avatarHTML = `
            <div class="chat-avatar">
                <div class="avatar ${chat.type === 'GROUP' ? 'avatar-group' : ''}">
                    ${chatName.slice(0, chat.type === 'GROUP' ? 1 : 2).toUpperCase()}
                </div>
                ${chat.type === 'PRIVATE' ?
            `<div class="status-indicator" data-uuid="${isRequester ? chat.owner?.uuid : chat.requester?.uuid}" style="background-color: #666;"></div>`
            : ''}
            </div>`;

        // 메시지 미리보기 및 요청 사유
        let previewContent = '';
        if (isRequest) {
            previewContent = isRequester ? '승인 대기중' : `요청: ${chat.requestReason || '없음'}`;
        } else {
            previewContent = chat.lastMessage || '대화를 시작해보세요.';
        }
        // 길이 제한 (예: 30자)
        previewContent = previewContent.length > 30 ? previewContent.slice(0, 30) + '...' : previewContent;


        // 요청 처리 버튼 (요청 상태 & 내가 Owner일 때)
        let requestActionsHTML = '';
        if (isRequest && isOwner && !isRequester) {
            requestActionsHTML = `
                <div class="request-actions">
                    <button class="action-button approve" data-action="APPROVE">승인</button>
                    <button class="action-button reject" data-action="REJECT">거부</button>
                    <button class="action-button block" data-action="BLOCK">차단</button>
                </div>`;
        }

        // 전체 아이템 HTML 구조
        item.innerHTML = `
            ${avatarHTML}
            <div class="chat-content">
                <div class="chat-header">
                    <div class="chat-title-group">
                        <h3 class="chat-name">${chatName}</h3>
                        ${chat.type === 'GROUP' && chat.participants ? `<span class="member-count">${chat.participants.length}</span>` : ''}
                    </div>
                    <div class="chat-meta">
                        <span class="chat-time">${lastMsgTimeStr}</span>
                        <span class="unread-count" style="display: ${unreadCount > 0 ? 'inline-block' : 'none'};">${unreadCount > 0 ? unreadCount : ''}</span>
                    </div>
                </div>
                <p class="chat-preview">${previewContent}</p>
                ${requestActionsHTML}
            </div>`;

        // 요청 처리 버튼 이벤트 리스너 추가 (이벤트 위임 활용)
        if (requestActionsHTML) {
            item.querySelector('.request-actions').addEventListener('click', (e) => {
                if (e.target.classList.contains('action-button')) {
                    e.stopPropagation(); // 아이템 클릭 이벤트 방지
                    const action = e.target.dataset.action;
                    handleRequest(chat.id, action);
                }
            });
        }

        // 목록 생성 시점에 온라인 상태 요청 (최적화 필요 - 한 번에 여러 명 요청 등)
        if (chat.type === 'PRIVATE') {
            const targetUuid = isRequester ? chat.owner?.uuid : chat.requester?.uuid;
            if (targetUuid) {
                // Debounce/Throttle 적용하여 요청 줄이기
                debouncedCheckOnlineStatus(chat.id);
            }
        }

        return item;
    }

    // 온라인 상태 확인 Debounce 함수 (예시)
    const debouncedCheckOnlineStatus = debounce(checkOnlineStatus, 500); // 500ms 간격

    // Debounce 유틸리티 함수
    function debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }


    /**
     * 개인 채팅방 열기 함수 (비동기 처리 및 오류 수정)
     * @param {object} chat - 열려는 채팅방 데이터 객체
     */
    async function openPersonalChat(chat) {
        // 유효성 검사 및 중복 실행 방지
        if (!chat || !chat.id || isChatOpening) {
            console.warn("채팅방 열기 중복 호출 또는 잘못된 정보:", chat);
            return;
        }
        // 참여 불가능한 상태면 열지 않음
        if (chat.status !== 'ACTIVE') {
            showError("현재 참여할 수 없는 채팅방입니다.");
            return;
        }

        isChatOpening = true;
        state.isLoading = true;
        currentChatRoomId = chat.id; // 현재 채팅방 ID 설정 먼저
        state.isChatRoomOpen = true;
        state.isChatOpen = true; // 채팅 창은 열린 상태
        updateChatUI(); // 로딩 및 UI 전환 시작

        const chatWindow = document.querySelector('.personal-chat');
        const messagesContainer = chatWindow?.querySelector('.messages-container');
        if (!messagesContainer) {
            console.error('Messages container not found!');
            isChatOpening = false; state.isLoading = false; updateChatUI();
            return;
        }

        // *** 중복 렌더링 방지: Set 초기화 로직 수정 ***
        // 해당 채팅방의 Set이 없으면 새로 생성
        if (!renderedMessageIds.has(chat.id)) {
            renderedMessageIds.set(chat.id, new Set());
        }
        const currentMessageIds = renderedMessageIds.get(chat.id); // Set 참조

        // 메시지 컨테이너 비우기
        messagesContainer.innerHTML = '';
        currentPage = 0; // 페이지 초기화

        try {
            // 총 메시지 수 요청 (await 사용)
            const totalMessages = await getMessageCount(chat.id);

            // 마지막 페이지부터 로드 (최신 메시지 먼저 보기)
            if (totalMessages > 0) {
                const lastPage = Math.max(0, Math.ceil(totalMessages / pageSize) - 1);
                currentPage = lastPage; // 현재 페이지 설정

                // 마지막 페이지 메시지 로드 (await 사용)
                const messages = await refreshMessages(chat.id, lastPage);

                // 시간순 정렬 (오래된 -> 최신) 후 렌더링
                messages.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
                messages.forEach(msg => {
                    // Set 확인 및 ID 추가 후 렌더링
                    if (!currentMessageIds.has(msg.id)) {
                        currentMessageIds.add(msg.id);
                        renderMessage(msg, 'append'); // 아래쪽에 추가
                    }
                });

                // 스크롤 맨 아래로 이동 (DOM 업데이트 후)
                setTimeout(() => {
                    messagesContainer.scrollTop = messagesContainer.scrollHeight;
                    isScrollable = true;
                }, 0); // 비동기적으로 잠시 후 실행
            } else {
                // 메시지 없음 표시
                messagesContainer.innerHTML = '<p class="empty-message-text">아직 대화 내용이 없습니다.</p>';
            }

            // 채팅방 헤더 정보 업데이트
            updateChatWindowHeader(chat);

            // 읽음 처리, 알림 토글, 온라인 상태 확인
            markMessagesAsRead();
            updateNotificationToggle();
            checkOnlineStatus(chat.id);

            // 입력 필드 활성화/비활성화
            const messageInput = chatWindow.querySelector('.message-input');
            const sendButton = chatWindow.querySelector('.send-button');
            updateChatInput(chat, messageInput, sendButton);

        } catch (error) { // getMessageCount 또는 refreshMessages 실패 처리
            console.error('Failed to open personal chat:', error);
            showError(`채팅방 로딩 중 오류 발생: ${error.message}`);
            resetChatWindow(); // 실패 시 목록으로 돌아감
        } finally { // 성공/실패 여부 관계없이 실행
            isChatOpening = false;
            state.isLoading = false;
            saveChatState(); // 상태 저장
            updateChatUI(); // 최종 UI 업데이트
        }
    }

    // 채팅방 헤더 UI 업데이트
    function updateChatWindowHeader(chat) {
        const chatWindow = document.querySelector('.personal-chat');
        if (!chatWindow || !chat) return;

        const chatName = chat.type === 'GROUP' ? (chat.name || '그룹 채팅') :
            (chat.requester?.uuid === currentUser ? chat.owner?.name : chat.requester?.name) || '알 수 없음';

        const nameElement = chatWindow.querySelector('.chat-name');
        const avatarElement = chatWindow.querySelector('.chat-profile .avatar span'); // HTML 구조에 맞게 수정
        const statusElement = chatWindow.querySelector('.chat-status');

        if (nameElement) nameElement.textContent = chatName;
        if (avatarElement) avatarElement.textContent = chatName.slice(0, 2).toUpperCase();
        if (statusElement) statusElement.textContent = '상태 정보 로딩 중...'; // 초기 텍스트
    }


    /**
     * 메시지 렌더링 함수 (DOM 조작 및 애니메이션)
     * @param {object} item - 메시지 데이터 객체
     * @param {string} position - 메시지 추가 위치 ('append' 또는 'prepend')
     */
    function renderMessage(item, position = 'append') {
        const messagesContainer = document.querySelector('.messages-container');
        if (!messagesContainer) return;

        // *** 더 강력한 중복 방지: DOM에 이미 있는지 확인 ***
        if (messagesContainer.querySelector(`[data-message-id="${item.id}"]`)) {
            // console.warn(`Message ${item.id} already in DOM, skipping.`);
            return;
        }

        let dateElement = null; // 날짜 구분선 요소
        const currentDate = item.timestamp ? new Date(item.timestamp).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' }) : '';

        // 날짜 구분선 필요 여부 확인
        const siblingMessage = position === 'append' ? messagesContainer.lastElementChild : messagesContainer.firstElementChild;
        let adjacentMessage = siblingMessage;
        // 날짜 구분선 건너뛰고 실제 메시지 찾기
        while(adjacentMessage && adjacentMessage.classList.contains('date-notification')) {
            adjacentMessage = position === 'append' ? adjacentMessage.previousElementSibling : adjacentMessage.nextElementSibling;
        }
        const lastDate = adjacentMessage?.dataset.date;

        if (!lastDate || lastDate !== currentDate) {
            dateElement = document.createElement('article');
            dateElement.className = 'date-notification';
            dateElement.dataset.date = currentDate;
            dateElement.innerHTML = `<time class="date-text">${currentDate}</time>`;
        }

        // 메시지 요소 생성
        const element = document.createElement('article');
        element.dataset.messageId = item.id; // ID 저장
        element.dataset.date = currentDate; // 날짜 저장
        element.style.opacity = '0'; // 초기 투명 (애니메이션용)

        if (item.type === 'SYSTEM') {
            element.className = 'system-notification';
            element.innerHTML = `<p class="system-text">${item.content || ''}</p>`;
        } else {
            const isOwnMessage = item.sender?.uuid === currentUser;
            element.className = isOwnMessage ? 'message-sent' : 'message-received';
            const timeStr = item.timestamp ? new Date(item.timestamp).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit', hour12: true }) : '';
            const senderName = item.sender?.name || '알 수 없음';
            const contentHTML = (item.content || '').replace(/\n/g, '<br>'); // 줄바꿈 처리

            if (isOwnMessage) {
                element.innerHTML = `
                    <div class="message-bubble">
                         <p class="message-text">${contentHTML}</p>
                    </div>
                    <div class="message-meta">
                        <time class="timestamp">${timeStr}</time>
                        </div>`;
            } else {
                element.innerHTML = `
                    <div class="avatar">${senderName.slice(0, 2).toUpperCase()}</div>
                    <div class="message-content-wrapper">
                        <header class="message-header">
                            <span class="user-name">${senderName}</span>
                        </header>
                        <div class="message-bubble">
                            <p class="message-text">${contentHTML}</p>
                        </div>
                         <div class="message-meta">
                             <time class="timestamp">${timeStr}</time>
                         </div>
                    </div>`;
            }
        }

        // DOM에 추가 (날짜 구분선 포함)
        const shouldScroll = isScrollable || item.sender?.uuid === currentUser; // 내가 보낸 메시지는 항상 스크롤

        if (position === 'prepend') {
            const oldScrollHeight = messagesContainer.scrollHeight;
            if (element) messagesContainer.insertBefore(element, messagesContainer.firstChild);
            if (dateElement) messagesContainer.insertBefore(dateElement, element); // 날짜를 메시지 앞에
            // 스크롤 위치 보정
            messagesContainer.scrollTop += (messagesContainer.scrollHeight - oldScrollHeight);
        } else { // append
            if (dateElement) messagesContainer.appendChild(dateElement);
            if (element) messagesContainer.appendChild(element);
            // 스크롤 맨 아래로 이동 (필요시)
            if (shouldScroll) {
                messagesContainer.scrollTop = messagesContainer.scrollHeight;
            }
        }

        // 페이드 인 애니메이션
        requestAnimationFrame(() => { // 다음 프레임에서 스타일 변경
            element.style.transition = 'opacity 0.3s ease-in';
            element.style.opacity = '1';
            if (dateElement) {
                dateElement.style.transition = 'opacity 0.3s ease-in';
                dateElement.style.opacity = '1';
            }
        });
    }


    // 알림 토글 버튼 UI 업데이트
    function updateNotificationToggle() {
        const toggleButton = document.querySelector('.personal-chat .notification-toggle');
        if (!toggleButton || !currentChatRoomId) return;
        const chat = chatRoomsCache.find(c => c.id === currentChatRoomId);
        const isEnabled = chat?.notificationEnabled !== false; // 기본 true

        toggleButton.setAttribute('aria-pressed', isEnabled.toString());
        const icon = toggleButton.querySelector('.notification-icon');
        if (icon) {
            // 활성/비활성 상태에 따라 아이콘 변경 또는 스타일 변경
            icon.style.fill = isEnabled ? '#333333' : '#CCCCCC'; // 예: 색상 변경
        }
    }

    // 채팅 입력 필드 상태 업데이트
    function updateChatInput(chat, messageInput, sendButton) {
        if (!messageInput || !sendButton || !chat) return;
        // PENDING, CLOSED, BLOCKED 상태에서는 비활성화
        const isDisabled = ['PENDING', 'CLOSED', 'BLOCKED'].includes(chat.status);
        messageInput.disabled = isDisabled;
        sendButton.disabled = isDisabled;
        // 상태별 placeholder 텍스트 설정
        if (chat.status === 'PENDING') {
            messageInput.placeholder = chat.requester?.uuid === currentUser ? "승인 대기 중..." : "채팅 요청 수락 대기 중...";
        } else if (isDisabled) {
            messageInput.placeholder = "메시지를 보낼 수 없습니다.";
        } else {
            messageInput.placeholder = "메시지를 입력하세요";
        }
    }

    // 에러 메시지 UI 표시 함수
    function showError(message, duration = 5000) {
        // (이전 답변의 개선된 showError 함수 내용 유지) [코드 라인 약 286-320]
        console.error("Error:", message); // 콘솔에도 에러 기록

        const errorDivId = 'chat-error-message';
        let errorDiv = document.getElementById(errorDivId);

        if (!errorDiv) {
            errorDiv = document.createElement('div');
            errorDiv.id = errorDivId;
            errorDiv.className = 'error-message'; // CSS 클래스 적용
            // 기본 스타일 (CSS에서 설정하는 것이 좋음)
            errorDiv.style.position = 'fixed';
            errorDiv.style.bottom = '20px';
            errorDiv.style.left = '50%';
            errorDiv.style.transform = 'translateX(-50%)';
            errorDiv.style.padding = '10px 20px';
            errorDiv.style.backgroundColor = 'rgba(200, 0, 0, 0.9)';
            errorDiv.style.color = 'white';
            errorDiv.style.borderRadius = '5px';
            errorDiv.style.zIndex = '10000';
            errorDiv.style.opacity = '0';
            errorDiv.style.transition = 'opacity 0.5s ease-out';
            document.body.appendChild(errorDiv);
        }

        errorDiv.textContent = message || '오류가 발생했습니다.'; // null/undefined 방지

        // 표시 애니메이션
        requestAnimationFrame(() => { errorDiv.style.opacity = '1'; });

        // 이전 타이머 제거
        if (errorDiv.timerId) clearTimeout(errorDiv.timerId);
        if (errorDiv.removeTimerId) clearTimeout(errorDiv.removeTimerId);

        // 숨김 타이머 설정
        errorDiv.timerId = setTimeout(() => {
            errorDiv.style.opacity = '0';
            errorDiv.removeTimerId = setTimeout(() => {
                if (errorDiv && errorDiv.parentNode) {
                    errorDiv.parentNode.removeChild(errorDiv);
                }
            }, 500); // transition 시간 후 제거
        }, duration);
    }

    // 이벤트 리스너 설정 (이벤트 위임 활용)
    function setupEventListeners() {
        const chatContainer = document.querySelector('.chat-system'); // 최상위 컨테이너
        if (!chatContainer) { console.error("Chat system container not found!"); return; }

        // 스크롤 이벤트 (메시지 컨테이너)
        const messagesContainer = chatContainer.querySelector('.messages-container');
        if (messagesContainer) {
            const debouncedScrollHandler = debounce(() => {
                const { scrollHeight, scrollTop, clientHeight } = messagesContainer;
                isScrollable = Math.abs(scrollHeight - scrollTop - clientHeight) < 10;
                if (scrollTop < 50 && !state.isLoading) { /* loadPreviousMessages(); */ }
            }, 100);
            messagesContainer.addEventListener('scroll', debouncedScrollHandler, { passive: true });
        }

        // 개인 채팅 창 이벤트 위임
        const personalChatWindow = chatContainer.querySelector('.personal-chat');
        if (personalChatWindow) {
            personalChatWindow.addEventListener('click', (e) => {
                // 옵션 버튼 토글
                const optionsButton = e.target.closest('.options-button');
                const optionsMenu = personalChatWindow.querySelector('.options-menu');
                if (optionsButton && optionsMenu) {
                    e.stopPropagation();
                    optionsMenu.style.display = optionsMenu.style.display === 'block' ? 'none' : 'block';
                    return; // 다른 이벤트 처리 방지
                }
                // 알림 토글 버튼
                const notificationToggle = e.target.closest('.notification-toggle');
                if (notificationToggle) {
                    e.stopPropagation();
                    if (!currentChatRoomId || !stompClient?.connected || !currentUser) return;
                    const chat = chatRoomsCache.find(c => c.id === currentChatRoomId);
                    const currentStatus = chat?.notificationEnabled !== false;
                    const action = currentStatus ? 'OFF' : 'ON';
                    stompClient.send("/app/toggleNotification", {}, JSON.stringify({ chatRoomId: currentChatRoomId, action }));
                    if(chat) chat.notificationEnabled = !currentStatus;
                    updateNotificationToggle();
                    if (optionsMenu) optionsMenu.style.display = 'none'; // 메뉴 닫기
                    return;
                }
                // 차단 버튼
                const blockOption = e.target.closest('.block-option');
                if (blockOption) {
                    if (confirm("정말로 이 사용자를 차단하시겠습니까?") && currentChatRoomId && stompClient?.connected) {
                        stompClient.send("/app/blockUser", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
                        resetChatWindow();
                    }
                    if (optionsMenu) optionsMenu.style.display = 'none';
                    return;
                }
                // 나가기 버튼
                const leaveOption = e.target.closest('.leave-option');
                if (leaveOption) {
                    if (confirm("정말로 이 채팅방을 나가시겠습니까?") && currentChatRoomId && stompClient?.connected) {
                        stompClient.send("/app/leaveChatRoom", {}, JSON.stringify({ chatRoomId: currentChatRoomId }));
                        resetChatWindow();
                    }
                    if (optionsMenu) optionsMenu.style.display = 'none';
                    return;
                }
                // 뒤로 가기 버튼
                const backButton = e.target.closest('.back-button');
                if (backButton) {
                    resetChatWindow();
                    return;
                }
                // 전송 버튼
                const sendButton = e.target.closest('.send-button');
                if (sendButton) {
                    sendMessage();
                    return;
                }
            });
            // 메뉴 외부 클릭 시 닫기
            document.addEventListener('click', (event) => {
                const optionsMenu = personalChatWindow.querySelector('.options-menu');
                const optionsButton = personalChatWindow.querySelector('.options-button');
                if (optionsMenu && optionsMenu.style.display === 'block' &&
                    !optionsButton?.contains(event.target) && !optionsMenu.contains(event.target)) {
                    optionsMenu.style.display = 'none';
                }
            });
            // 메시지 입력 Enter 키 처리
            const messageInput = personalChatWindow.querySelector('.message-input');
            if (messageInput) {
                messageInput.addEventListener('keypress', e => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                        e.preventDefault();
                        sendMessage();
                    }
                });
            }
        } // End of personalChatWindow events

        // 탭 전환 이벤트
        const messagesTabs = chatContainer.querySelector('.messages-tabs');
        if (messagesTabs) {
            messagesTabs.addEventListener('click', (e) => {
                const groupTab = e.target.closest('.tab-group');
                const personalTab = e.target.closest('.tab-personal');
                if (groupTab) switchTab('GROUP');
                else if (personalTab) switchTab('PRIVATE');
            });
        }

        // 채팅 열기/닫기 버튼 이벤트 (컨테이너 외부에 있을 수 있음)
        const openButton = document.getElementById('openChat');
        const closeButton = document.getElementById('closeChat');
        if (openButton) {
            openButton.addEventListener('click', () => {
                state.isChatOpen = true;
                state.isChatRoomOpen = false; // 목록 보기로 시작
                currentChatRoomId = null;
                updateChatUI();
                if (!isConnected) connect(); // 연결 안되어 있으면 연결 시도
                else refreshChatRooms(); // 연결되어 있으면 목록 새로고침
                saveChatState();
            });
        }
        if (closeButton) {
            closeButton.addEventListener('click', () => {
                state.isChatOpen = false;
                state.isChatRoomOpen = false;
                currentChatRoomId = null;
                updateChatUI();
                saveChatState();
                // 연결 해제 로직 (선택 사항)
                // if (stompClient?.connected) stompClient.deactivate();
            });
        }

        console.log("Event listeners set up.");
    }

    // 메시지 전송 함수
    function sendMessage() {
        // (이전 답변의 개선된 sendMessage 함수 내용 유지) [코드 라인 약 605-642]
        const messageInput = document.querySelector('.personal-chat .message-input');
        if (!messageInput || messageInput.disabled) return;

        const content = messageInput.value.trim();
        if (!content) return;

        if (content.length > MAX_MESSAGE_LENGTH) {
            showError(`최대 ${MAX_MESSAGE_LENGTH}자까지 입력 가능합니다.`);
            return;
        }
        if (Date.now() - lastSendTime < sendRateLimit) {
            showError("메시지를 너무 빨리 보낼 수 없습니다.");
            return;
        }

        if (currentChatRoomId && stompClient?.connected && currentUser) {
            const sanitizedContent = content.replace(/[<>&"']/g, match => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;', "'": '&#39;' }[match]));

            stompClient.send('/app/sendMessage', {}, JSON.stringify({
                chatRoomId: currentChatRoomId,
                content: sanitizedContent
            }));

            lastSendTime = Date.now();
            messageInput.value = '';
            messageInput.focus();

            // 낙관적 업데이트 (내가 보낸 메시지 바로 표시)
            const tempMessage = {
                id: `temp_${Date.now()}`,
                chatRoomId: currentChatRoomId,
                sender: { uuid: currentUser, name: '나' }, // 이름은 실제 사용자 정보 사용 권장
                content: sanitizedContent,
                timestamp: new Date().toISOString(),
                type: 'MESSAGE'
            };
            renderMessage(tempMessage, 'append'); // 즉시 렌더링
            // renderedMessageIds에는 추가하지 않음 (서버 메시지 수신 시 실제 ID로 처리됨)
            // 스크롤 맨 아래로 이동
            const messagesContainer = document.querySelector('.messages-container');
            if(messagesContainer) messagesContainer.scrollTop = messagesContainer.scrollHeight;


        } else {
            showError("메시지를 전송할 수 없습니다. 연결 상태를 확인해주세요.");
            if (!stompClient?.connected) connect(); // 연결 시도
        }
    }

    // 탭 전환 함수
    function switchTab(tab) {
        if (activeTab === tab) return;
        activeTab = tab;
        updateTabUI();
        renderChatList(chatRoomsCache); // 필터링된 목록 즉시 렌더링
        saveChatState();
    }

    // 채팅 창 초기화 (목록 보기로 돌아가기)
    function resetChatWindow() {
        const personalChatWindow = document.querySelector('.personal-chat');
        if (personalChatWindow) personalChatWindow.classList.remove('visible');

        const messagesList = document.getElementById('messagesList');
        if (messagesList) messagesList.classList.add('visible');

        currentChatRoomId = null; // 현재 채팅방 ID 해제
        state.isChatRoomOpen = false;
        // state.isChatOpen = true; // 목록 상태이므로 채팅 창은 열린 상태

        // 구독 해제는 불필요 (공통 구독 사용 가정)

        refreshChatRooms(); // 목록 새로고침
        saveChatState();
        updateChatUI();
    }

    // 전체 채팅 UI 상태 업데이트 함수
    function updateChatUI() {
        // (이전 답변의 개선된 updateChatUI 함수 내용 유지) [코드 라인 약 667-690]
        const chatContainer = document.querySelector('.chat-system'); // HTML 구조에 맞게 수정
        const messagesList = document.getElementById('messagesList');
        const openButton = document.getElementById('openChat');
        const closeButton = document.getElementById('closeChat');
        const personalChatWindow = document.querySelector('.personal-chat');

        if (!chatContainer || !messagesList || !openButton || !closeButton || !personalChatWindow) return;

        // 로딩 클래스 토글
        chatContainer.classList.toggle('loading', state.isLoading);

        if (state.isChatRoomOpen) {
            // 개인 채팅방 열림
            messagesList.classList.remove('visible');
            personalChatWindow.classList.add('visible');
            openButton.classList.add('hidden');
            closeButton.classList.remove('hidden');
            chatContainer.classList.add('active'); // 전체 활성
        } else if (state.isChatOpen) {
            // 채팅 목록 열림
            messagesList.classList.add('visible');
            personalChatWindow.classList.remove('visible');
            openButton.classList.add('hidden');
            closeButton.classList.remove('hidden');
            chatContainer.classList.add('active'); // 전체 활성
        } else {
            // 채팅 닫힘
            messagesList.classList.remove('visible');
            personalChatWindow.classList.remove('visible');
            openButton.classList.remove('hidden'); // 열기 버튼 표시
            closeButton.classList.add('hidden');
            chatContainer.classList.remove('active'); // 전체 비활성
        }
    }

    // 탭 UI 업데이트 함수
    function updateTabUI() {
        // (이전 답변의 updateTabUI 함수 내용 유지) [코드 라인 약 692-701]
        const groupTab = document.querySelector('.tab-group');
        const personalTab = document.querySelector('.tab-personal');
        if (!groupTab || !personalTab) return;

        groupTab.classList.toggle('active', activeTab === 'GROUP');
        personalTab.classList.toggle('active', activeTab === 'PRIVATE');
    }

    // 모듈의 Public API 정의
    return {
        // connect, // connect는 initialize 내부에서 호출
        setupEventListeners,
        loadChatState,
        updateChatUI,
        updateTabUI,
        // 초기화 함수 추가
        initialize: async () => {
            loadChatState(); // 1. 상태 로드
            updateTabUI();   // 2. 탭 UI 초기화
            try {
                await connect(); // 3. 서버 연결 시도 (await로 완료 대기)
                console.log("Initial connection successful.");
                // 4. 연결 성공 후 리스너 설정 및 최종 UI 업데이트
                setupEventListeners();
                updateChatUI();
                // 초기 상태가 채팅방 열림이면 해당 채팅방 로드
                if (state.isChatRoomOpen && currentChatRoomId) {
                    const chat = chatRoomsCache.find(c => c.id === currentChatRoomId);
                    if (chat && chat.status === 'ACTIVE') {
                        openPersonalChat(chat); // 저장된 채팅방 열기
                    } else {
                        resetChatWindow(); // 유효하지 않으면 목록으로
                    }
                } else if (state.isChatOpen) {
                    renderChatList(chatRoomsCache); // 목록 렌더링
                }

            } catch (error) { // connect()에서 reject된 경우
                console.error("Chat initialization failed:", error);
                // 초기 연결 실패 시 에러 처리 (예: 에러 메시지 표시)
                showError("채팅 서버에 연결할 수 없습니다. 새로고침하거나 나중에 다시 시도해주세요.");
            }
        }
    };
})(); // End of chatApp IIFE

// 페이지 로드 완료 시 채팅 앱 초기화 (async/await 적용)
document.addEventListener('DOMContentLoaded', async () => {
    // chatApp 모듈의 initialize 함수 호출하고 완료될 때까지 기다림
    await chatApp.initialize();
    console.log("Chat App Initialized");
});