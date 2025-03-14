import React from "react";
import NavBar from "./NavBar";
import CarouselImg from "./CarouselImg";
import CategoryMenu from "./CategoryMenu";

function Home() {
  return (
    <div>
      <NavBar></NavBar>
      <CarouselImg></CarouselImg>
      <CategoryMenu></CategoryMenu>
    </div>
  );
}

export default Home;
