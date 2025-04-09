// 네이버 지도 초기화
let map = new naver.maps.Map('map', {
    center: new naver.maps.LatLng(37.5665, 126.9780), // 기본: 서울
    zoom: 10
});
let markers = [];
let infoWindows = [];
let selectedMarker = null;

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
    selectedMarker = markers[index];
    selectedMarker.setIcon({
        url: 'https://maps.google.com/mapfiles/ms/icons/red-dot.png' // 선택된 마커 색상 변경
    });

    // 정보 창 열기
    infoWindows[index].open(map, markers[index]);

    // 선택된 값을 폼에 저장
    const cleanTitle = place.title.replace(/<[^>]+>/g, '');
    document.getElementById('location').value = place.address;
    document.getElementById('locationTitle').value = cleanTitle
    document.getElementById('latitude').value = place.mapy / 1e7;
    document.getElementById('longitude').value = place.mapx / 1e7;
}



// * 나이검사 및 사진 미리보기
$(document).ready(function () {
    // Age Restriction 실시간 검증
    $('#ageRestriction').on('input', function () {
        let age = parseInt($(this).val());
        if (age < 20) {
            $(this).val(20);
            $(this).addClass('is-invalid');
        } else {
            $(this).removeClass('is-invalid');
        }
    });


    // 파일 선택 시 미리보기
    $('#clubFile').on('change', function (event) {
        let preview = $('#preview');
        preview.empty(); // 기존 미리보기 초기화

        let files = event.target.files;
        if (files) {
            $.each(files, function (index, file) {
                if (file.type.match('image.*')) { // 이미지 파일만 처리
                    let reader = new FileReader();
                    reader.onload = function (e) {
                        let img = $('<img>').attr('src', e.target.result)
                            // .addClass('img-fluid')
                            .css({'max-width': '200px', 'max-height': '200px'});
                        preview.append(img);
                    };
                    reader.readAsDataURL(file);
                }
            });
        }
    });
    console.log(typeof jQuery); // "function"이면 로드 성공, "undefined"면 실패

});