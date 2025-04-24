package com.team.moim.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private String resourcePath = "/upload/**"; //view페이지에서 접근할 경로
    private String savePath = "file:/home/ubuntu/upload/"; //실제 파일 저장 경로

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        //resourcePath 뷰에서 이 경로로 접근하면
        //savePath 에서 찾아준다.
        registry.addResourceHandler(resourcePath).addResourceLocations(savePath);
        WebMvcConfigurer.super.addResourceHandlers(registry);

        // 정적 이미지: 게시글
        registry.addResourceHandler("/img/upload/**")
                .addResourceLocations("classpath:/static/img/upload/");

        // ✅ 정적 이미지: 프로필 (중요!)
        registry.addResourceHandler("/img/user/**")
                .addResourceLocations("classpath:/static/img/user/");

        // ✅ 정적 자원 추가: js, css, icon, svg 등
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/");
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/");
        registry.addResourceHandler("/icon/**")
                .addResourceLocations("classpath:/static/icon/");
        registry.addResourceHandler("/img/svg/**")
                .addResourceLocations("classpath:/static/img/svg/");
        registry.addResourceHandler("/img/**")
                .addResourceLocations("classpath:/static/img/");

        // favicon (있다면)
        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/static/favicon.ico");
    }
}
