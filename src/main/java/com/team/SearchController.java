package com.team;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SearchController {
    private final GroupService groupService;

    public SearchController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/search")
    public List<Group> searchGroups(String query) {
        return groupService.searchGroups(query); // 서비스에서 검색 로직
    }
}
