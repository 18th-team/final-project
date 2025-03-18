package com.team.authentication;

import jakarta.servlet.http.HttpSession;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Scope("prototype")
public class AuthenticationService {

    private static final String BASEURL = "https://www.jeju.go.kr/";
    private static final String BASEURL2 = "https://pcc.siren24.com/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0";

    private String updatedUrl = "";
    private final HttpSession session;
    private final String clientKey; // 클라이언트별 고유 식별자

    public AuthenticationService(HttpSession session) {
        this.session = session;
        // 세션에서 기존 clientKey 재사용, 없으면 새로 생성
        String existingKey = (String) session.getAttribute("clientKey");
        this.clientKey = existingKey != null ? existingKey : UUID.randomUUID().toString();
        session.setAttribute("clientKey", this.clientKey);
    }

    public String getClientKey() {
        return this.clientKey;
    }

    private String cleanCookieValue(String value) {
        int dotIndex = value.indexOf(".");
        return dotIndex != -1 ? value.substring(0, dotIndex) : value;
    }

    private ExchangeFilterFunction cookieFilter() {
        return (request, next) -> next.exchange(request)
                .doOnNext(response -> {
                    List<String> setCookies = response.headers().header(HttpHeaders.SET_COOKIE);
                    Map<String, String> cookieStore = getCookieStoreFromSession();
                    setCookies.forEach(cookie -> {
                        String[] parts = cookie.split("=", 2);
                        if (parts.length == 2) {
                            cookieStore.put(parts[0], cleanCookieValue(parts[1].split(";")[0]));
                        }
                    });
                    session.setAttribute("cookieStore_" + clientKey, cookieStore);
                    System.out.println("Cookies stored for clientKey: " + clientKey + " - " + cookieStore);
                });
    }

    private Map<String, String> getCookieStoreFromSession() {
        Map<String, String> cookieStore = (Map<String, String>) session.getAttribute("cookieStore_" + clientKey);
        if (cookieStore == null) {
            cookieStore = new HashMap<>();
            session.setAttribute("cookieStore_" + clientKey, cookieStore);
        }
        return cookieStore;
    }

    private String getSelectedCookies(String... cookieNames) {
        Map<String, String> cookieStore = getCookieStoreFromSession();
        return Arrays.stream(cookieNames)
                .filter(cookieStore::containsKey)
                .map(name -> name + "=" + cookieStore.get(name))
                .collect(Collectors.joining("; "));
    }

    private String getAllCookies() {
        Map<String, String> cookieStore = getCookieStoreFromSession();
        return cookieStore.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
    }

    public Mono<String> getCaptchaImage() {
        WebClient client = WebClient.builder()
                .filter(cookieFilter())
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(false)))
                .build();
        String cookies = getSelectedCookies("JSESSIONID");
        System.out.println("Sending Cookie in getCaptchaImage: " + cookies);
        if (cookies.isEmpty()) {
            System.out.println("Warning: JSESSIONID is missing in getCaptchaImage");
        }
        return client.get()
                .uri(BASEURL2 + "pcc_V3/Captcha/simpleCaptchaImg.jsp")
                .header(HttpHeaders.HOST, "pcc.siren24.com")
                .header(HttpHeaders.REFERER, BASEURL2 + "pcc_V3/passWebV2/pcc_V3_j30_certHpTi01.jsp")
                .header(HttpHeaders.COOKIE, getSelectedCookies("JSESSIONID"))
                .accept(MediaType.IMAGE_JPEG)
                .retrieve()
                .bodyToMono(byte[].class)
                .map(bytes -> "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes))
                .doOnError(error -> System.out.println("캡차 이미지 요청 실패: " + error.getMessage()));
    }

    public Mono<MultiValueMap<String, String>> CertificationRequest(AuthenticationDTO authenticationDTO, MultiValueMap<String, String> formData) {
        formData.set("cellCorp", authenticationDTO.getCellcorp());
        System.out.println(formData);
        System.out.println("CertificationRequest Cookie : " + getSelectedCookies("JSESSIONID"));
        WebClient client = WebClient.builder()
                .filter(cookieFilter())
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(false)))
                .build();

        if (authenticationDTO.getCellcorp().equals("SKT") || authenticationDTO.getCellcorp().equals("KTF") || authenticationDTO.getCellcorp().equals("LGT")) {
            return client.post()
                    .uri(BASEURL2 + "pcc_V3/passWebV2/pcc_V3_j30_certHpTi01.jsp")
                    .header(HttpHeaders.HOST, "pcc.siren24.com")
                    .header(HttpHeaders.REFERER, BASEURL2 + "pcc_V3/passWebV2/pcc_V3_j10.jsp")
                    .header(HttpHeaders.COOKIE, getSelectedCookies("JSESSIONID"))
                    .body(BodyInserters.fromFormData(formData))
                    .exchangeToMono(passResponse -> {
                        System.out.println(BASEURL2 + "pcc_V3/passWebV2/pcc_V3_j30_certHpTi01.jsp");
                        if (!passResponse.statusCode().equals(HttpStatus.OK)) {
                            return Mono.error(new RuntimeException("상태 코드 " + passResponse.statusCode()));
                        }
                        return passResponse.bodyToMono(String.class)
                                .flatMap(passBody -> {
                                    Document passDoc = Jsoup.parse(passBody);
                                    Elements passFormInputs = passDoc.select("form[name=goPassForm] input");
                                    MultiValueMap<String, String> passFormData = new LinkedMultiValueMap<>();
                                    passFormInputs.forEach(input -> {
                                        String name = input.attr("name");
                                        String value = input.attr("value");
                                        if (!name.isEmpty()) {
                                            passFormData.add(name, value);
                                        }
                                    });
                                    passFormData.set("userName", authenticationDTO.getName());
                                    passFormData.set("birthDay1", authenticationDTO.getBirthDay1());
                                    passFormData.set("birthDay2", authenticationDTO.getBirthDay2());
                                    passFormData.set("No", authenticationDTO.getPhone());
                                    passFormData.set("captchaInput", authenticationDTO.getCaptchaInput());
                                    passFormData.set("passGbn", "N");

                                    passFormData.remove("phoneNum");
                                    passFormData.remove("sci_name");
                                    passFormData.remove("sci_agency");
                                    System.out.println(passFormData);
                                    return client.post()
                                            .uri(BASEURL2 + "pcc_V3/passWebV2/pcc_V3_j30_certHpTi02.jsp")
                                            .header(HttpHeaders.HOST, "pcc.siren24.com")
                                            .header(HttpHeaders.REFERER, BASEURL2 + "pcc_V3/passWebV2/pcc_V3_j30_certHpTi01.jsp")
                                            .header(HttpHeaders.COOKIE, getSelectedCookies("JSESSIONID"))
                                            .body(BodyInserters.fromFormData(passFormData))
                                            .exchangeToMono(passResponse2 -> {
                                                System.out.println(BASEURL2 + "pcc_V3/passWebV2/pcc_V3_j30_certHpTi02.jsp");
                                                if (!passResponse2.statusCode().equals(HttpStatus.OK)) {
                                                    return Mono.error(new RuntimeException("상태 코드 " + passResponse2.statusCode()));
                                                }
                                                return passResponse2.bodyToMono(String.class).flatMap(passBody2 -> {
                                                    System.out.println(passBody2);
                                                    Document passDoc2 = Jsoup.parse(passBody2);
                                                    MultiValueMap<String, String> passFormData2 = new LinkedMultiValueMap<>();
                                                    Elements scripts = passDoc2.select("script");
                                                    for (Element script : scripts) {
                                                        String scriptContent = script.data();
                                                        System.out.println("Script Content: " + scriptContent);
                                                        if (scriptContent.isEmpty()) {
                                                            continue;
                                                        }
                                                        if (scriptContent.contains("pop_alert")) {
                                                            Pattern pattern = Pattern.compile("pop_alert\\s*\\(\\s*\"([^\"]*)\"\\s*\\)");
                                                            Matcher matcher = pattern.matcher(scriptContent);
                                                            if (matcher.find()) {
                                                                String alertText = matcher.group(1).replace("\\n", "\n");
                                                                passFormData2.add("alertText", alertText);
                                                                return Mono.just(passFormData2);
                                                            }
                                                        }
                                                    }
                                                    Elements passFormInputs2 = passDoc2.select("form[name=goForm] input");
                                                    passFormInputs2.forEach(input -> {
                                                        String name = input.attr("name");
                                                        String value = input.attr("value");
                                                        if (!name.isEmpty()) {
                                                            passFormData2.add(name, value);
                                                        }
                                                    });
                                                    return Mono.just(passFormData2);
                                                });
                                            });
                                });
                    });
        } else {
            return Mono.error(new IllegalArgumentException("지원하지 않는 통신사 코드: " + authenticationDTO.getCellcorp()));
        }
    }

    public Mono<Void> cookieSetup() {
        WebClient client = WebClient.builder()
                .filter(cookieFilter())
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(false)))
                .build();
        System.out.println("cookieSetup Cookie : " + getSelectedCookies("JSESSIONID"));
        return client.get()
                .uri(BASEURL + "mypage/login.htm?act=nlogin")
                .exchangeToMono(response -> {
                    System.out.println("Requesting: " + BASEURL + "mypage/login.htm?act=nlogin");
                    if (!response.statusCode().equals(HttpStatus.OK)) {
                        return Mono.error(new RuntimeException("상태 코드 " + response.statusCode()));
                    }
                    return response.bodyToMono(String.class).flatMap(body -> client.get()
                            .uri(BASEURL + "tool/pcc/check.jsp?for=nlogin")
                            .header(HttpHeaders.HOST, "www.jeju.go.kr")
                            .header(HttpHeaders.REFERER, BASEURL + "mypage/login.htm?act=nlogin")
                            .header(HttpHeaders.COOKIE, getAllCookies())
                            .exchangeToMono(checkResponse -> {
                                System.out.println("Requesting: " + BASEURL + "tool/pcc/check.jsp?for=nlogin");
                                if (!checkResponse.statusCode().equals(HttpStatus.OK)) {
                                    return Mono.error(new RuntimeException("요청 실패: 상태 코드 " + checkResponse.statusCode()));
                                }
                                return checkResponse.bodyToMono(String.class)
                                        .flatMap(checkBody -> {
                                            Document checkDoc = Jsoup.parse(checkBody);
                                            Elements textInputs = checkDoc.select("input[type=text]");
                                            MultiValueMap<String, String> checkFormData = new LinkedMultiValueMap<>();

                                            for (Element input : textInputs) {
                                                String name = input.attr("name");
                                                String value = input.attr("value");
                                                if (!name.isEmpty()) {
                                                    checkFormData.add(name, value);
                                                }
                                            }

                                            List<String> errorMessages = checkFormData.get("errMsg");
                                            if (errorMessages != null && !errorMessages.isEmpty()) {
                                                return Mono.error(new RuntimeException(errorMessages.get(0)));
                                            }

                                            String actionUrl = checkDoc.select("form[name=reqPCCForm]").attr("action");
                                            if (actionUrl.isEmpty()) {
                                                return Mono.error(new RuntimeException("actionUrl을 찾을 수 없습니다."));
                                            }
                                            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(actionUrl);
                                            checkFormData.forEach((name, values) -> {
                                                if (!values.isEmpty()) {
                                                    builder.queryParam(name, values.get(0));
                                                }
                                            });
                                            this.updatedUrl = builder.build().toUriString();
                                            session.setAttribute("updatedUrl_" + clientKey, this.updatedUrl);
                                            System.out.println("Updated URL set for clientKey: " + clientKey + " - " + this.updatedUrl);
                                            System.out.println("Cookie after setup: " + getSelectedCookies("JSESSIONID"));
                                            return Mono.empty();
                                        });
                            }));
                });
    }

    public Mono<MultiValueMap<String, String>> extractReqInfoAndRetUrl(String clientKey) {
        System.out.println("extractReqInfoAndRetUrl: " + getSelectedCookies("JSESSIONID"));
        this.updatedUrl = (String) session.getAttribute("updatedUrl_" + clientKey);
        if (this.updatedUrl == null || this.updatedUrl.isEmpty()) {
            return Mono.error(new RuntimeException("세션에서 updatedUrl을 찾을 수 없습니다. clientKey: " + clientKey));
        }

        System.out.println("Requesting URL for clientKey: " + clientKey + " - " + this.updatedUrl);
        WebClient client = WebClient.builder()
                .filter(cookieFilter())
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(false)))
                .build();

        return client.get()
                .uri(this.updatedUrl)
                .header(HttpHeaders.HOST, "pcc.siren24.com")
                .header(HttpHeaders.REFERER, BASEURL)
                .header(HttpHeaders.COOKIE, getSelectedCookies("JSESSIONID"))
                .exchangeToMono(certResponse -> {
                    System.out.println(this.updatedUrl);
                    if (!certResponse.statusCode().equals(HttpStatus.OK)) {
                        return Mono.error(new RuntimeException("세 번째 요청 실패: 상태 코드 " + certResponse.statusCode()));
                    }
                    return certResponse.bodyToMono(String.class)
                            .flatMap(certBody -> {
                                Document certDoc = Jsoup.parse(certBody);
                                Elements hiddenInputs = certDoc.select("input[type=hidden]");
                                MultiValueMap<String, String> certFormData = new LinkedMultiValueMap<>();
                                hiddenInputs.forEach(input -> {
                                    String name = input.attr("name");
                                    String value = input.attr("value");
                                    if (!name.isEmpty()) {
                                        certFormData.add(name, value);
                                    }
                                });
                                List<String> certErrorMessages = certFormData.get("errMsg");
                                if (certErrorMessages != null && !certErrorMessages.isEmpty()) {
                                    certFormData.add("alertText", certErrorMessages.toString());
                                    return Mono.just(certFormData);
                                }
                                String certActionUrl = certDoc.select("form[name=Pcc_V3Form]").attr("action");
                                System.out.println("certActionUrl: " + certActionUrl);
                                return client.post()
                                        .uri(certActionUrl)
                                        .header(HttpHeaders.HOST, "pcc.siren24.com")
                                        .header(HttpHeaders.REFERER, updatedUrl)
                                        .header(HttpHeaders.COOKIE, getSelectedCookies("JSESSIONID"))
                                        .body(BodyInserters.fromFormData(certFormData))
                                        .exchangeToMono(finalResponse -> {
                                            System.out.println(certActionUrl);
                                            if (!finalResponse.statusCode().equals(HttpStatus.OK)) {
                                                return Mono.error(new RuntimeException("네 번째 요청 실패: 상태 코드 " + finalResponse.statusCode()));
                                            }
                                            return finalResponse.bodyToMono(String.class)
                                                    .flatMap(finalBody -> {
                                                        Document finalDoc = Jsoup.parse(finalBody);
                                                        Elements loginInputs = finalDoc.select("form[name=cplogn] input");
                                                        MultiValueMap<String, String> finalFormData = new LinkedMultiValueMap<>();
                                                        loginInputs.forEach(input -> {
                                                            String name = input.attr("name");
                                                            String value = input.attr("value");
                                                            if (!name.isEmpty()) {
                                                                finalFormData.add(name, value);
                                                            }
                                                        });
                                                        List<String> finalErrorMessages = finalFormData.get("errMsg");
                                                        if (finalErrorMessages != null && !finalErrorMessages.isEmpty()) {
                                                            finalFormData.add("alertText", finalErrorMessages.toString());
                                                            return Mono.just(finalFormData);
                                                        }
                                                        return Mono.just(finalFormData);
                                                    });
                                        });
                            });
                });
    }

    public String getCellcorpaa(String co) {
        switch (co) {
            case "KT": return "KTF";
            case "SKT": return "SKT";
            case "LGU": return "LGT";
            case "SKM": return "SKM";
            case "KTM": return "KTM";
            case "LGM": return "LGM";
            default: return "Unknown";
        }
    }
}