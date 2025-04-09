$(document).ready(function() {
    // 검색 버튼 클릭 이벤트 처리
    $('#searchBtn').click(function() {
        search(); // 검색 함수 호출
    });
});

$('#searchInput').keypress(function(event) {
    if (event.which === 13) { // Enter 키를 눌렀을 때
        $('#searchBtn').click(); // 검색 버튼 클릭 이벤트 호출
    }
});

// 검색 함수
function search() {
    let query = $('#searchInput').val(); // 검색어 가져오기
    $.ajax({
        url: '/api/server/naver/' + encodeURIComponent(query),
        method: 'GET',
        success: function(data) {
            displaySearchResults(data); // 검색 결과 표시 함수 호출
            displayMarkers(data); // 마커 표시 함수 호출
        },
        error: function(xhr, status, error) {
            console.error('Error:', error); // 에러 발생 시 콘솔에 로그 출력
        }
    });
}

// 클릭한 장소 정보를 저장하는 전역 변수
let clickedRestaurant;

// 검색 결과 출력 함수
function displaySearchResults(results) {
    let searchResultsElement = document.getElementById('searchResults');
    searchResultsElement.innerHTML = ''; // 이전 검색 결과 지우기

    // 검색 결과를 목록으로 변환하여 출력
    results.forEach(function(restaurant) {
        let listItem = document.createElement('div'); // 장소 목록 아이템 생성
        listItem.classList.add('restaurant-item'); // CSS 클래스 추가
        // 각 장소의 이름과 주소 표시
        listItem.innerHTML = '<strong>'
            + restaurant.title.replace(/<[^>]+>/g, '') // HTML 태그 제거
            + '</strong><br>' + restaurant.address;
        searchResultsElement.appendChild(listItem); // 장소 목록에 아이템 추가

        // 장소 목록 아이템에 클릭 이벤트 추가
        listItem.addEventListener('click', function() {
            // 클릭한 장소 정보 저장
            clickedRestaurant = restaurant;
            // 해당 위치로 지도 이동
            moveMapToRestaurantLocation();
        });
    });
}

// 지도 위치 이동 함수
function moveMapToRestaurantLocation() {
    if (clickedRestaurant) { // 클릭한 장소 정보가 존재하는 경우
        const mapContainer = document.getElementById('map'); // 지도를 표시할 영역의 DOM 요소
        const mapOption = { // 지도 옵션 설정
            center: new kakao.maps.LatLng(clickedRestaurant.latitude, clickedRestaurant.longitude), // 지도의 중심좌표 설정
            level: 3 // 지도 확대 레벨 설정
        };

        // 지도 객체 생성
        const map = new kakao.maps.Map(mapContainer, mapOption);

        // 클릭한 장소 위치에 마커 생성
        const marker = new kakao.maps.Marker({
            map: map, // 마커를 표시할 지도 객체 설정
            position: new kakao.maps.LatLng(clickedRestaurant.latitude, clickedRestaurant.longitude), // 마커의 위치 설정
            title: clickedRestaurant.title.replace(/<[^>]+>/g, '') // 마커에 표시될 타이틀 설정 (HTML 태그 제거)
        });

        // 마커를 클릭했을 때 모달을 표시하고 내용을 설정하는 이벤트 리스너 등록
        kakao.maps.event.addListener(marker, 'click', function() {
            const modal = $('#modal'); // 모달 요소 선택
            const modalContent = $('#modalContent'); // 모달 내용 요소 선택
            modal.css("display", "block"); // 모달 표시
            modalContent.html(this.getTitle()); // 모달 내용 설정 (마커의 타이틀로 설정)

            const clickedMarkerInfo = $('#clickedMarkerInfo'); // 클릭한 마커 정보 요소 선택
            clickedMarkerInfo.html(this.getTitle()); // 클릭한 마커의 타이틀 표시
        });
    }
}

const imageSrc = "https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/markerStar.png";

// 마커 표시 함수
function displayMarkers(results) {
    // 지도 생성 및 초기화
    const map = new kakao.maps.Map(document.getElementById('map'), {
        center: new kakao.maps.LatLng(results[0].latitude, results[0].longitude), // 첫 번째 장소의 위치를 중심으로 설정
        level: 3 // 지도 확대 레벨 설정
    });

    // 각 장소에 대한 마커 표시
    results.forEach(function(restaurant) {
        const latitude = restaurant.latitude; // 장소의 위도
        const longitude = restaurant.longitude; // 장소의 경도
        const imageSize = new kakao.maps.Size(24, 35); // 마커 이미지 크기 설정
        const markerImage = new kakao.maps.MarkerImage(imageSrc, imageSize); // 마커 이미지 생성

        // 장소의 위치에 마커 생성
        const marker = new kakao.maps.Marker({
            map: map, // 마커를 표시할 지도 객체 설정
            position: new kakao.maps.LatLng(latitude, longitude), // 마커의 위치 설정
            title: restaurant.title.replace(/<[^>]+>/g, ''), // 마커에 표시될 타이틀 설정 (HTML 태그 제거)
            image: markerImage // 마커에 사용될 이미지 설정
        });

        // 마커를 클릭했을 때 모달을 표시하고 내용을 설정하는 이벤트 리스너 등록
        kakao.maps.event.addListener(marker, 'click', function() {
            const modal = $('#modal'); // 모달 요소 선택
            const modalContent = $('#modalContent'); // 모달 내용 요소 선택
            modal.css("display", "block"); // 모달 표시
            modalContent.html(this.getTitle()); // 모달 내용 설정 (마커의 타이틀로 설정)

            const clickedMarkerInfo = $('#clickedMarkerInfo'); // 클릭한 마커 정보 요소 선택
            clickedMarkerInfo.html(this.getTitle()); // 클릭한 마커의 타이틀 표시
        });
    });
}

// 모달 닫기 버튼 클릭 이벤트
$('.close').click(function() {
    const modal = $('#modal');
    modal.css("display", "none"); // 모달 숨기기
});