package com.pickem.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamDTO(String school, String conference, List<String> logos) {
}