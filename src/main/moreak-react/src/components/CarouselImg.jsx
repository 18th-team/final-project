import React from "react";
import { Carousel } from "react-bootstrap";
import "../style/CarouselImg.css"; // 추가 스타일링용 (선택)
import Img1 from "../img/home-carousel01.jpg";
import Img2 from "../img/home-carousel02.jpg";
import Img3 from "../img/home-carousel03.jpg";
import Img4 from "../img/home-carousel04.jpg";
const images = [Img1, Img2, Img3, Img4];

const CarouselImg = () => {
  return (
    <Carousel interval={3000} className="carousel-custom">
      {images.map((image, index) => (
        <Carousel.Item key={index}>
          <img
            className="d-block w-100"
            src={image}
            alt={`캐러셀 이미지 ${index + 1}`}
          />
        </Carousel.Item>
      ))}
    </Carousel>
  );
};

export default CarouselImg;
