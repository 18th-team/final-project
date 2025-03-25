package com.team.moim.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private String resourcePath = "/upload/**"; //view페이지에서 접근할 경로
    private String savePath = "file:///C:/springBoot_img/"; //실제 파일 저장 경로

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        //resourcePath 뷰에서 이 경로로 접근하면
        //savePath 에서 찾아준다.
        registry.addResourceHandler(resourcePath).addResourceLocations(savePath);
        WebMvcConfigurer.super.addResourceHandlers(registry);
    }
}
