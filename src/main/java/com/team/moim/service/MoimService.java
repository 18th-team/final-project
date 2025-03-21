package com.team.moim.service;

import com.team.moim.dto.NewMoimDto;
import com.team.moim.entity.NewMoim;
import com.team.moim.repository.MoimRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class MoimService {
    @Autowired
    MoimRepository moimRepository;

    public Page<NewMoimDto> getMoimList(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<NewMoim> moimPage = moimRepository.findAll(pageable);
        return moimPage.map(NewMoimDto::new);
    }
}
