// 네이버 지도 초기화
let map = new naver.maps.Map('map', {
    center: new naver.maps.LatLng(37.5665, 126.9780), // 기본: 서울
    zoom: 10
});

let markers = [];
let infoWindows = [];
let selectedMarker = null;

document.addEventListener('DOMContentLoaded', function () {
    const searchInput = document.getElementById('search-query');

    searchInput.addEventListener('keydown', function (event) {
        if (event.key === 'Enter') {
            event.preventDefault(); // 기본 form 제출 막기
            searchAddress(); // 검색 함수 호출
        }
    });
});


function searchAddress() {
    const query = document.getElementById('search-query').value;
    fetch(`/api/server/naver?query=${encodeURIComponent(query)}`)
        .then(response => response.json())
        .then(data => {
            // 기존 마커와 정보 창 제거
            markers.forEach(marker => marker.setMap(null));
            infoWindows.forEach(info => info.close());
            markers = [];
            infoWindows = [];

            // 검색 결과 리스트 렌더링
            const resultList = document.getElementById('search-results');
            resultList.innerHTML = '';

            data.forEach((place, index) => {
                resultList.style.display='block';
                const item = document.createElement('div');
                item.className = 'result-item';
                item.innerHTML = `<strong>${place.title}</strong><br>${place.address}`;
                item.onclick = () => selectAddress(place, index, item);
                resultList.appendChild(item);

                // 마커 생성
                const lat = place.mapy / 1e7;  // 위도
                const lng = place.mapx / 1e7; // 경도
                const position = new naver.maps.LatLng(lat, lng);
                const marker = new naver.maps.Marker({
                    position: position,
                    map: map,
                    title: place.title
                });
                markers.push(marker);

                // 정보 창 생성
                const infoWindow = new naver.maps.InfoWindow({
                    content: `<div style="padding:10px;"><strong>${place.title}</strong><br>${place.address}</div>`
                });
                infoWindows.push(infoWindow);

                // 마커 클릭 시 정보 창 열기 및 선택
                naver.maps.Event.addListener(marker, 'click', () => {
                    selectAddress(place, index, item);
                });
            });

            // 지도 범위 조정
            if (data.length > 0) {
                if (data.length === 1) {
                    map.setCenter(new naver.maps.LatLng(data[0].mapy / 1e7, data[0].mapx / 1e7));
                    map.setZoom(14);
                } else {
                    const bounds = new naver.maps.LatLngBounds();
                    data.forEach(place => {
                        bounds.extend(new naver.maps.LatLng(place.mapy / 1e7, place.mapx / 1e7));
                    });
                    map.fitBounds(bounds);
                }
            }
        })
        .catch(error => console.error('Error:', error));
}




function selectAddress(place, index, item) {
    // 기존 정보 창 닫기
    infoWindows.forEach(info => info.close());

    // 선택된 마커 강조
    if (selectedMarker) selectedMarker.setIcon(null);
    selectedMarker = markers[index];  // 현재 클릭한 마커

    // 리스트 항목 배경색 설정 (인라인 스타일)
    document.querySelectorAll('.result-item').forEach(el => {
        el.style.backgroundColor = '';
        el.style.fontWeight = ''; // 추가 스타일 초기화
    });
    item.style.backgroundColor = '#2196f3';
    item.style.fontWeight = 'bold';
    selectedItem = item;

    // 정보 창 열기
    infoWindows[index].open(map, markers[index]);

    // 선택된 값을 폼에 저장
    const cleanTitle = place.title.replace(/<[^>]+>/g, ''); // HTML 태그 제거
    document.getElementById('location').value = place.address;
    document.getElementById('locationTitle').value = cleanTitle;
    document.getElementById('latitude').value = place.mapy / 1e7;  // 위도
    document.getElementById('longitude').value = place.mapx / 1e7;  // 경도
}


