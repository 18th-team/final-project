import React from "react";
import { Routes, Route } from "react-router-dom";
import MoimList from "./Moim"; // 가정: 소모임 페이지
import Home from "./Home";
import "../style/App.css";
// import Chat from "./Chat"; // 가정: 채팅 페이지
// import Community from "./Community"; // 가정: 커뮤니티 페이지
// import Login from "./Login"; // 가정: 로그인 페이지
// import Signup from "./Signup"; // 가정: 회원가입 페이지

const App = () => {
  return (
    <div>
      <Routes>
        <Route path="/" element={<Home></Home>} /> {/* 기본 페이지 */}
        <Route path="/moimList" element={<MoimList />} />
        {/* <Route path="/chat" element={<Chat />} />
        <Route path="/community" element={<Community />} />
        <Route path="/login" element={<Login />} />
        <Route path="/signup" element={<Signup />} /> */}
      </Routes>
    </div>
  );
};

export default App;
