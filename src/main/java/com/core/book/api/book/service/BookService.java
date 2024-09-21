package com.core.book.api.book.service;

import com.core.book.api.book.dto.BookDTO;
import com.core.book.api.book.dto.ResultDTO;
import com.core.book.api.book.entity.Book;
import com.core.book.common.exception.NotFoundException;
import com.core.book.common.response.ErrorStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BookService {

    @Value("${naver-client-id}")
    private String clientId;

    @Value("${naver-client-secret}")
    private String clientSecret;

    public Map<String, Object> book(String text, int page, int size){

        int start = (page - 1) * size + 1; //검색 시작 위치 변수

        //String apiURL
        URI uri = UriComponentsBuilder
                .fromUriString("https://openapi.naver.com")
                .path("/v1/search/book.json")
                .queryParam("query", text) //검색어
                .queryParam("display", size) //한 번에 표시할 검색 결과 개수
                .queryParam("start", start) //검색 시작 위치
                .queryParam("sort", "sim") //검색 결과 정렬 방법
                .encode()
                .build()
                .toUri();

        //Spring 요청 제공 클래스
        RequestEntity<Void> req = RequestEntity.get(uri)
                .header("X-Naver-Client-Id", clientId)
                .header("X-Naver-Client-Secret", clientSecret)
                .build();

        //Spring 제공 RestTemplate
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> resp = restTemplate.exchange(req, String.class); //응답 본문 String 타입으로 변환

        //JSON 파싱 (JSON 문자열을 객체로 만듦, 문서화)
        ObjectMapper om = new ObjectMapper();
        ResultDTO resultDTO = null;

        try {
            resultDTO = om.readValue(resp.getBody(), ResultDTO.class);
        } catch (JsonMappingException e) {
            log.error("JsonMappingException occurred while reading value", e);
        } catch (JsonProcessingException e) {
            log.error("JsonProcessingException occurred while reading value", e);
        }

        if(resultDTO == null){
            throw new NotFoundException(ErrorStatus.FAIL_REQUEST_BOOK_INFO.getMessage());
        }

        //예외 처리 - "더 이상 검색 결과가 없습니다."
        if(resultDTO.getTotal() != 0 && resultDTO.getTotal() < start){
            throw new NotFoundException(ErrorStatus.BOOK_NO_MORE_FOUND_EXCEPTION.getMessage());
        }

        //책 정보 데이터 담긴 List 변수 : bookDTOs
        List<BookDTO> bookDtos = Optional.of(resultDTO)
                .map(ResultDTO::getItems)
                .orElse(Collections.emptyList());

        //DTO 에 담긴 데이터 Entity로 변경 : books
        List<Book> books = bookDtos.stream()
                .map(BookDTO::toEntity)
                .collect(Collectors.toList());

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("book", books);
        responseMap.put("page", page);
        responseMap.put("size", size);
        responseMap.put("totalSize", resultDTO.getTotal()); //총 검색 결과 개수

        return responseMap;
    }
}
