package com.team;


import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class GroupService {
    private final GroupRepository groupRepository;

    public GroupService(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    public List<Group> searchGroups(String query) {
        return groupRepository.findByKeywordsContaining(query);
    }
}