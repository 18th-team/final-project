import React from "react";
import { Nav } from "react-bootstrap";
import { Link } from "react-router-dom";
import "../style/CategoryMenu.scss";

import SportIcon from "../assets/exercise.svg";
import CultureIcon from "../assets/culture.svg";
import FoodIcon from "../assets/food.svg";
import TechIcon from "../assets/tech.svg";
import TravelIcon from "../assets/travel.svg";
import HobbyIcon from "../assets/hobby.svg";

const categories = [
  { id: 1, name: "액티비티", icon: SportIcon, path: "/moimList?id=1" },
  { id: 2, name: "문화예술", icon: CultureIcon, path: "/moimList?id=2" },
  { id: 3, name: "푸드/드링크", icon: FoodIcon, path: "/moimList?id=3" },
  { id: 4, name: "자기계발", icon: TechIcon, path: "/moimList?id=4" },
  { id: 5, name: "여행", icon: TravelIcon, path: "/moimList?id=5" },
  { id: 6, name: "취미", icon: HobbyIcon, path: "/moimList?id=6" },
];

const CategoryMenu = () => {
  return (
    <Nav variant="pills" className="category-menu">
      {categories.map((category) => (
        <Nav.Item key={category.id} className="category-item-box">
          <Nav.Link as={Link} to={category.path} className="category-item">
            <img
              src={category.icon}
              alt={`${category.name} 아이콘`}
              className="category-icon"
            />
            <span>{category.name}</span>
          </Nav.Link>
        </Nav.Item>
      ))}
    </Nav>
  );
};

export default CategoryMenu;
