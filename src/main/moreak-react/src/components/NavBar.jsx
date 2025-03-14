import React, { useState } from "react";
import { FaSearch, FaBars, FaTimes } from "react-icons/fa";
import { Link } from "react-router-dom"; // Link 추가
import "../style/NavBar.css";
import FinalLogo from "../img/final_logo.svg";

const NavBar = () => {
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState([]);
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  const handleSearch = (e) => {
    e.preventDefault();
    const dummyResults = [
      {
        id: 1,
        title: "[카페 모임] 분위기 예쁜 카페 같이가요☕️",
        keywords: "푸드,드링크,카페,모임,예쁜,분위기",
      },
      {
        id: 2,
        title: "[🍀서울 2030 블로거 클럽🍀] 같이 '알짜 블로그' 만들어요-!",
        keywords: "재테크,블로그,맛집,뷰티",
      },
    ].filter(
      (group) =>
        group.keywords.includes(searchQuery) ||
        group.title.includes(searchQuery)
    );
    setSearchResults(dummyResults);
    setSearchQuery("");
  };

  const toggleMenu = () => {
    setIsMenuOpen(!isMenuOpen);
  };

  return (
    <div>
      <nav className="navigation">
        <div className="nav-header">
          <div className="logo">
            <img src={FinalLogo} alt="모락모락 로고" className="logo-img" />
          </div>
          <button className="menu-toggle" onClick={toggleMenu}>
            {isMenuOpen ? <FaTimes /> : <FaBars />}
          </button>
        </div>
        <div className={`items ${isMenuOpen ? "open" : ""}`}>
          <Link to="/moimList" className="list_item">
            소모임
          </Link>
          <Link to="/chat" className="list_item">
            채팅
          </Link>
          <Link to="/community" className="list_item">
            커뮤니티
          </Link>
          <Link to="/login" className="list_item">
            로그인
          </Link>
          <Link to="/signup" className="list_item">
            회원가입
          </Link>
        </div>
        <form className="search-bar" onSubmit={handleSearch}>
          <div className="state-layer">
            <div className="content">
              <input
                type="text"
                className="supporting-text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="소모임 검색하기"
              />
            </div>
            <div className="trailing-elements">
              <button type="submit" className="_1st-trailing-icon">
                <div className="container">
                  <div className="state-layer2">
                    <FaSearch className="icon" style={{ color: "white" }} />
                  </div>
                </div>
              </button>
            </div>
          </div>
        </form>
        {searchResults.length > 0 && (
          <ul className="search-results">
            {searchResults.map((result) => (
              <li key={result.id}>{result.title}</li>
            ))}
          </ul>
        )}
      </nav>
    </div>
  );
};

export default NavBar;
