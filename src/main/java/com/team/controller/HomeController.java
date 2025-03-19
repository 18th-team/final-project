package com.team.controller;

import com.team.authentication.AuthenticationDTO;
import com.team.authentication.AuthenticationService;
import com.team.user.UserCreateForm;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/mobti")
    public String mobtiTest() {
        return "mobti_test";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }


    @GetMapping("/signup")
    public String signUp(Model model, HttpSession session) {
        UserCreateForm userCreateForm = new UserCreateForm();
        model.addAttribute("userCreateForm", userCreateForm);
        String clientKey = UUID.randomUUID().toString();
        AuthenticationService authService = new AuthenticationService(session, clientKey);
        model.addAttribute("clientKey", clientKey); // clientKey 전달
        return "signup";
    }
}
