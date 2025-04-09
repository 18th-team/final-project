var latitude = /*[[${clubDTO.latitude}]]*/ 37.5665;
var longitude = /*[[${clubDTO.longitude}]]*/ 126.9780;
console.log("**Latitude: " + latitude + ", Longitude: " + longitude); // 디버깅


if (latitude && longitude) {
    var mapOptions = {
        center: new naver.maps.LatLng(latitude, longitude),
        zoom: 15
    };
    var map = new naver.maps.Map('map', mapOptions);


    //마커생성
    const marker = new naver.maps.Marker({
        position: new naver.maps.LatLng(latitude, longitude),
        map: map,
        title: /*[[${clubDTO.locationTitle}]]*/ "장소",

    });

    // 정보 창 생성
    var infoWindow = new naver.maps.InfoWindow({
        content: `<div style="padding:10px;">[[${clubDTO.locationTitle}]]<br>[[${clubDTO.location}]]</div>`
    });


// 마커 클릭 시 정보 창 열기
    naver.maps.Event.addListener(marker, 'click', function () {
        if (infoWindow.getMap()) {
            infoWindow.close();
        } else {
            infoWindow.open(map, marker);
        }
    });
}else {
    console.error("Invalid coordinates");
}