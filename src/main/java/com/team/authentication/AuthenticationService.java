package com.team.authentication;

import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
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
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.SocketException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AuthenticationService {

    private static final String BASEURL = "https://www.jeju.go.kr/";
    private static final String BASEURL2 = "https://pcc.siren24.com/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0";
    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(45);

    private final HttpSession session;
    private final String clientKey;
    private final WebClient webClientWithCookie; // 쿠키 저장용
    private final WebClient webClientNoCookie;   // 쿠키 저장 불필요용

    public AuthenticationService(HttpSession session, String clientKey) {
        /*System.setProperty("javax.net.debug", "ssl:handshake:verbose");  상세 로그 활성화*/
        this.session = session;
        this.clientKey = clientKey;
        this.webClientWithCookie = createWebClient(true);  // 쿠키 필터 적용
        this.webClientNoCookie = createWebClient(false);
        System.out.println("Initialized AuthenticationService with clientKey: " + this.clientKey);
    }

    private WebClient createWebClient(boolean withCookieFilter) {
        HttpClient httpClient = HttpClient.create()
                .secure(sslContextSpec -> {
                    try {
                        SslContext sslContext = SslContextBuilder.forClient()
                                .protocols("TLSv1.2") // TLS 1.2를 명시적으로 사용
                                .ciphers(Arrays.asList(
                                        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                                        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
                                ))
                                .build();
                        sslContextSpec.sslContext(sslContext);
                    } catch (SSLException e) {
                        throw new RuntimeException("SSL 컨텍스트 설정에 실패했습니다.", e);
                    }
                })
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .responseTimeout(RESPONSE_TIMEOUT)
                // 추가 설정: 읽기/쓰기 타임아웃 및 연결 풀 관리
                .doOnConnected(conn -> conn
                    .addHandlerLast(new ReadTimeoutHandler((int) RESPONSE_TIMEOUT.getSeconds()))
                    .addHandlerLast(new WriteTimeoutHandler((int) RESPONSE_TIMEOUT.getSeconds())))
                .tcpConfiguration(tcpClient -> tcpClient
                        .option(ChannelOption.TCP_NODELAY, true) // Nagle 알고리즘 비활성화로 패킷 전송 속도 개선
                        .option(ChannelOption.SO_SNDBUF, 1024 * 1024) // 송신 버퍼 크기 증가
                        .option(ChannelOption.SO_RCVBUF, 1024 * 1024)); // 수신 버퍼 크기 증가
            /*    .wiretap("reactor.netty.http.client.HttpClient", LogLevel.DEBUG); 네트워크 트래픽 로깅*/

        WebClient.Builder builder = WebClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR,ko;q=0.8,en-US;q=0.5,en;q=0.3")
                .clientConnector(new ReactorClientHttpConnector(httpClient));

        if (withCookieFilter) {
            builder.filter(cookieFilter());
        }
        return builder.build();
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

    public Mono<String> getCaptchaImage(Integer cnt) {
        String uri = cnt == null ? BASEURL2 + "pcc_V3/Captcha/simpleCaptchaImg.jsp"
                : BASEURL2 + "pcc_V3/Captcha/simpleCaptchaImg.jsp?cnt=" + cnt;
        return webClientWithCookie.get()
                .uri(uri)
                .header(HttpHeaders.HOST, "pcc.siren24.com")
                .header(HttpHeaders.REFERER, BASEURL2 + "pcc_V3/passWebV2/pcc_V3_j30_certHpTi01.jsp")
                .accept(MediaType.IMAGE_JPEG)
                .retrieve()
                .bodyToMono(byte[].class)
                .map(bytes -> "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes))
                .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY)
                        .filter(throwable -> throwable instanceof SocketException)
                        .doBeforeRetry(signal -> System.out.println("Retrying getCaptchaImage: " + signal.totalRetries())))
                .doOnError(error -> System.out.println("Failed to get captcha image: " + error.getMessage() + ", Time: " + LocalDateTime.now()))
                .onErrorResume(error -> Mono.just("data:image/jpeg;base64,")); // 기본 이미지로 대체
    }

    public Mono<MultiValueMap<String, String>> checkOtp(MultiValueMap<String, String> formData, String otp) {
        formData.set("otp", otp);
        return webClientWithCookie.post()
                .uri(BASEURL2 + "pcc_V3/passWebV2/pcc_V3_j30_certHpTi04.jsp")
                .header(HttpHeaders.HOST, "pcc.siren24.com")
                .header(HttpHeaders.REFERER, BASEURL2 + "pcc_V3/passWebV2/pcc_V3_j30_certHpTi03.jsp")
                .header(HttpHeaders.COOKIE, getSelectedCookies("JSESSIONID"))
                .body(BodyInserters.fromFormData(formData))
                .exchangeToMono(response -> {
                    System.out.println("Requesting: " + BASEURL2 + "pcc_V3/passWebV2/pcc_V3_j30_certHpTi04.jsp");
                    if (!response.statusCode().equals(HttpStatus.OK)) {
                        return Mono.error(new RuntimeException("Status code: " + response.statusCode()));
                    }
                    return response.bodyToMono(String.class).flatMap(body -> {
                        Document doc = Jsoup.parse(body);
                        MultiValueMap<String, String> result = new LinkedMultiValueMap<>();
                        Elements scripts = doc.select("script");
                        for (Element script : scripts) {
                            String scriptContent = script.data();
                            if (scriptContent.contains("pop_alert")) {
                                Pattern pattern = Pattern.compile("pop_alert\\s*\\(\\s*['\"]([^'\"]*)['\"]\\s*(?:,\\s*['\"]?[^'\"]*['\"]?)?\\s*\\)");
                                Matcher matcher = pattern.matcher(scriptContent);
                                if (matcher.find()) {
                                    String alertText = matcher.group(1).replace("\\n", "\n").replace("<br>", "");
                                    result.add("alertText", alertText);
                                    return Mono.just(result);
                                }
                            }
                        }
                        return Mono.just(result);
                    });
                })
                .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY)
                        .filter(throwable -> throwable instanceof SocketException))
                .doOnError(error -> System.out.println("Failed to check OTP: " + error.getMessage()));
    }

    public Mono<MultiValueMap<String, String>> CertificationRequest(AuthenticationDTO dto, MultiValueMap<String, String> formData, String captchaInput) {
        formData.set("cellCorp", dto.getCellcorp());
        if (!List.of("SKT", "KTF", "LGT").contains(dto.getCellcorp())) {
            return Mono.error(new IllegalArgumentException("Unsupported cellCorp: " + dto.getCellcorp()));
        }

        return webClientWithCookie.post()
                .uri(BASEURL2 + "pcc_V3/passWebV2/pcc_V3_j30_certHpTi01.jsp")
                .header(HttpHeaders.HOST, "pcc.siren24.com")
                .header(HttpHeaders.REFERER, BASEURL2 + "pcc_V3/passWebV2/pcc_V3_j10.jsp")
                .header(HttpHeaders.COOKIE, getSelectedCookies("JSESSIONID"))
                .body(BodyInserters.fromFormData(formData))
                .exchangeToMono(response -> processCertificationResponse(response, dto, captchaInput))
                .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY)
                        .filter(throwable -> throwable instanceof SocketException))
                .doOnError(error -> System.out.println("CertificationRequest failed: " + error.getMessage()));
    }

    private Mono<MultiValueMap<String, String>> processCertificationResponse(ClientResponse response, AuthenticationDTO dto, String captchaInput) {
        if (!response.statusCode().equals(HttpStatus.OK)) {
            return Mono.error(new RuntimeException("Status code: " + response.statusCode()));
        }
        return response.bodyToMono(String.class).flatMap(body -> {
            Document doc = Jsoup.parse(body);
            MultiValueMap<String, String> formData = parseFormInputs(doc, "goPassForm");
            if (hasError(formData)) {
                return Mono.error(new RuntimeException(formData.getFirst("errMsg")));
            }
            updateFormData(formData, dto, captchaInput);
            return webClientWithCookie.post()
                    .uri(BASEURL2 + "pcc_V3/passWebV2/pcc_V3_j30_certHpTi02.jsp")
                    .header(HttpHeaders.HOST, "pcc.siren24.com")
                    .header(HttpHeaders.REFERER, BASEURL2 + "pcc_V3/passWebV2/pcc_V3_j30_certHpTi01.jsp")
                    .header(HttpHeaders.COOKIE, getSelectedCookies("JSESSIONID"))
                    .body(BodyInserters.fromFormData(formData))
                    .exchangeToMono(response2 -> processSecondCertificationResponse(response2));
        });
    }

    private Mono<MultiValueMap<String, String>> processSecondCertificationResponse(ClientResponse response) {
        if (!response.statusCode().equals(HttpStatus.OK)) {
            return Mono.error(new RuntimeException("Status code: " + response.statusCode()));
        }
        return response.bodyToMono(String.class).flatMap(body -> {
            Document doc = Jsoup.parse(body);
            MultiValueMap<String, String> formData = parseFormInputs(doc, "goForm");
            if (hasError(formData)) {
                return Mono.error(new RuntimeException(formData.getFirst("errMsg")));
            }
            if (hasAlert(doc)) {
                return Mono.error(new RuntimeException(extractAlert(doc)));
            }
            return webClientWithCookie.post()
                    .uri(BASEURL2 + "pcc_V3/passWebV2/pcc_V3_j30_certHpTi03.jsp")
                    .header(HttpHeaders.HOST, "pcc.siren24.com")
                    .header(HttpHeaders.REFERER, BASEURL2 + "pcc_V3/passWebV2/pcc_V3_j30_certHpTi02.jsp")
                    .header(HttpHeaders.COOKIE, getSelectedCookies("JSESSIONID"))
                    .body(BodyInserters.fromFormData(formData))
                    .exchangeToMono(response3 -> {
                        if (!response3.statusCode().equals(HttpStatus.OK)) {
                            return Mono.error(new RuntimeException("Status code: " + response3.statusCode()));
                        }
                        return response3.bodyToMono(String.class).map(body3 -> {
                            Document doc3 = Jsoup.parse(body3);
                            MultiValueMap<String, String> result = parseFormInputs(doc3, "goPass");
                            session.setAttribute("resultFormData_" + clientKey, result);
                            return result;
                        });
                    });
        });
    }

    public Mono<Void> updatedUrl() {
        return webClientNoCookie.get()
                .uri(BASEURL + "mypage/login.htm?act=nlogin")
                .header(HttpHeaders.HOST, "www.jeju.go.kr") // 명시적 Host 헤더 추가
                .exchangeToMono(response -> {
                    System.out.println("Requesting: " + BASEURL + "mypage/login.htm?act=nlogin, Time: " + LocalDateTime.now());
                    if (!response.statusCode().equals(HttpStatus.OK)) {
                        return Mono.error(new RuntimeException("Status code: " + response.statusCode() + ", Headers: " + response.headers().asHttpHeaders()));
                    }
                    String cookieHeader = response.headers().header(HttpHeaders.SET_COOKIE)
                            .stream()
                            .map(cookie -> cookie.split(";", 2)[0])
                            .collect(Collectors.joining("; "));
                    return response.bodyToMono(String.class)
                            .doOnNext(body -> System.out.println("Response body length: " + body.length() + ", First 100 chars: " + body.substring(0, Math.min(100, body.length()))))
                            .flatMap(body -> webClientNoCookie.get()
                                    .uri(BASEURL + "tool/pcc/check.jsp?for=nlogin")
                                    .header(HttpHeaders.HOST, "www.jeju.go.kr")
                                    .header(HttpHeaders.REFERER, BASEURL + "mypage/login.htm?act=nlogin")
                                    .header(HttpHeaders.COOKIE, cookieHeader)
                                    .exchangeToMono(this::processCheckResponse));
                })
                .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY)
                        .filter(throwable -> {
                            if (throwable instanceof WebClientRequestException) {
                                Throwable cause = throwable.getCause();
                                return cause instanceof SocketException || cause instanceof IOException;
                            }
                            return throwable instanceof SocketException || throwable instanceof IOException;
                        })
                        .doBeforeRetry(signal -> System.out.println("Retry attempt: " + signal.totalRetries() + ", Error: " + signal.failure().getMessage() + ", Time: " + LocalDateTime.now())))
                .doOnError(error -> System.err.println("updatedUrl failed: " + error.getMessage() + ", Stacktrace: " + Arrays.toString(error.getStackTrace()) + ", Time: " + LocalDateTime.now()));
    }

    private Mono<Void> processCheckResponse(ClientResponse response) {
        if (!response.statusCode().equals(HttpStatus.OK)) {
            return Mono.error(new RuntimeException("Status code: " + response.statusCode()));
        }
        return response.bodyToMono(String.class).flatMap(body -> {
            Document doc = Jsoup.parse(body);
            MultiValueMap<String, String> formData = parseFormInputs(doc, "reqPCCForm", "input[type=text]");
            if (hasError(formData)) {
                return Mono.error(new RuntimeException(formData.getFirst("errMsg")));
            }
            String actionUrl = doc.select("form[name=reqPCCForm]").attr("action");
            if (actionUrl.isEmpty()) {
                return Mono.error(new RuntimeException("Action URL not found"));
            }
            String updatedUrl = UriComponentsBuilder.fromUriString(actionUrl)
                    .queryParams(formData).build().toUriString();
            session.setAttribute("updatedUrl_" + clientKey, updatedUrl);
            System.out.println("Updated URL set for clientKey: " + clientKey + " - " + updatedUrl);
            return Mono.empty();
        });
    }

    public Mono<MultiValueMap<String, String>> extractReqInfoAndRetUrl(String clientKey) {
        String updatedUrl = (String) session.getAttribute("updatedUrl_" + clientKey);
        if (updatedUrl == null || updatedUrl.isEmpty()) {
            return Mono.error(new RuntimeException("Updated URL not found in session for clientKey: " + clientKey));
        }

        return webClientWithCookie.get()
                .uri(updatedUrl)
                .header(HttpHeaders.HOST, "pcc.siren24.com")
                .header(HttpHeaders.REFERER, BASEURL)
                .header(HttpHeaders.COOKIE, getSelectedCookies("JSESSIONID"))
                .exchangeToMono(response -> {
                    if (!response.statusCode().equals(HttpStatus.OK)) {
                        return Mono.error(new RuntimeException("Status code: " + response.statusCode()));
                    }
                    return response.bodyToMono(String.class).flatMap(body -> {
                        Document doc = Jsoup.parse(body);
                        MultiValueMap<String, String> formData = parseFormInputs(doc, null, "input[type=hidden]");
                        if (hasError(formData)) {
                            return Mono.error(new RuntimeException(formData.getFirst("errMsg")));
                        }
                        String actionUrl = doc.select("form[name=Pcc_V3Form]").attr("action");
                        return webClientWithCookie.post()
                                .uri(actionUrl)
                                .header(HttpHeaders.HOST, "pcc.siren24.com")
                                .header(HttpHeaders.REFERER, updatedUrl)
                                .header(HttpHeaders.COOKIE, getSelectedCookies("JSESSIONID"))
                                .body(BodyInserters.fromFormData(formData))
                                .exchangeToMono(finalResponse -> {
                                    if (!finalResponse.statusCode().equals(HttpStatus.OK)) {
                                        return Mono.error(new RuntimeException("Status code: " + finalResponse.statusCode()));
                                    }
                                    return finalResponse.bodyToMono(String.class).map(finalBody -> {
                                        Document finalDoc = Jsoup.parse(finalBody);
                                        MultiValueMap<String, String> result = parseFormInputs(finalDoc, "cplogn");
                                        if (hasError(result)) {
                                            result.add("alertText", result.getFirst("errMsg"));
                                        }
                                        return result;
                                    });
                                });
                    });
                })
                .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY)
                        .filter(throwable -> throwable instanceof SocketException));
    }

    // 헬퍼 메서드
    private MultiValueMap<String, String> parseFormInputs(Document doc, String formName) {
        return parseFormInputs(doc, formName, "input");
    }

    private MultiValueMap<String, String> parseFormInputs(Document doc, String formName, String selector) {
        Elements inputs = formName != null ? doc.select("form[name=" + formName + "] " + selector) : doc.select(selector);
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        inputs.forEach(input -> {
            String name = input.attr("name");
            String value = input.attr("value");
            if (!name.isEmpty()) {
                formData.add(name, value);
            }
        });
        return formData;
    }

    private boolean hasError(MultiValueMap<String, String> formData) {
        List<String> errors = formData.get("errMsg");
        return errors != null && !errors.isEmpty();
    }

    private boolean hasAlert(Document doc) {
        return doc.select("script").stream().anyMatch(script -> script.data().contains("pop_alert"));
    }

    private String extractAlert(Document doc) {
        for (Element script : doc.select("script")) {
            String content = script.data();
            if (content.contains("pop_alert")) {
                Matcher matcher = Pattern.compile("pop_alert\\s*\\(\\s*\"([^\"]*)\"\\s*\\)").matcher(content);
                if (matcher.find()) {
                    return matcher.group(1).replace("\\n", "\n");
                }
            }
        }
        return "Unknown alert";
    }

    private void updateFormData(MultiValueMap<String, String> formData, AuthenticationDTO dto, String captchaInput) {
        formData.set("userName", dto.getName());
        formData.set("birthDay1", dto.getBirthDay1());
        formData.set("birthDay2", dto.getBirthDay2());
        formData.set("No", dto.getPhone());
        formData.set("captchaInput", captchaInput);
        formData.set("passGbn", "N");
        formData.remove("phoneNum");
        formData.remove("sci_name");
        formData.remove("sci_agency");
    }
}