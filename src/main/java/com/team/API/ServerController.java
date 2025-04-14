package com.team.API;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import groovy.util.logging.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 네이버 검색 API를 통해 장소 정보를 검색하는 RestController입니다.
 * 네이버 API를 호출하여 지정된 이름 또는 동적으로 지정된 검색어를 기반으로 장소를 검색하고, 검색된 장소 목록을 반환합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/server")
public class ServerController {
    //인증키 정보 저장
    @Value("${naver.client-id}")
    private String NAVER_API_ID;

    @Value("${naver.secret}")
    private String NAVER_API_SECRET;

    /*
     * 네이버 검색API를 이용하여 동적으로 검색어를 지정하여, 장소를 검색하기
     * pqram query값으로 동적으로 지정된 검색어입력함
     * return 검색된 장소 목록들 보여줌
     * */
    @GetMapping("/naver/{name}")
    public List<Map<String, String>> naver(@PathVariable String name) {
        return searchResult(name);
    }

    @GetMapping("/naver")
    public List<Map<String, String>> naverSearchDynamic(@RequestParam String query) {
        return searchResult(query);
    }


    /*
     * 네이버 검색API를 이용하여 장소를 검색하는 메서드
     * 사용자가 입력한 검색어query값을 매개변수로 받아서
     * 검색된 장소 리스트들 목록으로 List타입으로 나열하기*/
    private List<Map<String, String>> searchResult(String query) {
        List<Map<String, String>> results = new ArrayList<>();
        try {
            ByteBuffer buffer = StandardCharsets.UTF_8.encode(query);
            String encode = StandardCharsets.UTF_8.decode(buffer).toString();

            URI uri = UriComponentsBuilder.fromUriString("https://openapi.naver.com")
                    .path("/v1/search/local")
                    .queryParam("query", encode).queryParam("display", 5)
                    .queryParam("start", 1).queryParam("sort", "random")
                    .encode().build().toUri();

            RestTemplate restTemplate = new RestTemplate();
            RequestEntity<Void> req = RequestEntity.get(uri)
                    .header("X-Naver-Client-Id", NAVER_API_ID)
                    .header("X-Naver-Client-Secret", NAVER_API_SECRET)
                    .build();

            ResponseEntity<String> response = restTemplate.exchange(req, String.class);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            JsonNode itemsNode = rootNode.path("items");

            for (JsonNode itemNode : itemsNode) {
                Map<String, String> result = new HashMap<>();
                result.put("title", itemNode.path("title").asText());
                result.put("address", itemNode.path("address").asText());
                result.put("mapy", itemNode.path("mapy").asText());
                result.put("mapx", itemNode.path("mapx").asText());
                // 변환 제거
                results.add(result);
                System.out.println("검색 결과 수: " + itemsNode.size());

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

}
