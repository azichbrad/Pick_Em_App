package com.pickem.app.service;

import com.pickem.app.dto.TeamDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConferenceService {

    @Value("${cfbd.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    // Now maps the School Name to the entire TeamDTO object
    @Cacheable("teams")
    public Map<String, TeamDTO> getTeamDataMap() {
        String url = "https://api.collegefootballdata.com/teams/fbs";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<List<TeamDTO>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<TeamDTO>>() {}
        );

        Map<String, TeamDTO> teamMap = new HashMap<>();
        if (response.getBody() != null) {
            for (TeamDTO team : response.getBody()) {
                teamMap.put(team.school(), team);
            }
        }
        return teamMap;
    }
}