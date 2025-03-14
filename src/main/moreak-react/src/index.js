import React from "react";
import "bootstrap/dist/css/bootstrap.min.css"; // 부트스트랩 CSS 임포트

import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./components/App";

const root = ReactDOM.createRoot(document.getElementById("root"));
root.render(
  <BrowserRouter>
    <App />
  </BrowserRouter>
);
