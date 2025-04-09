package com.team.API;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {

    //검색 요청할 페이지로 이동
    @GetMapping("/api/search")
    public String seacrh() {
        return "map/search";
    }
}
