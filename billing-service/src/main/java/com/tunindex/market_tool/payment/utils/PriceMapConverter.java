package com.tunindex.market_tool.payment.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Converter
@Slf4j
public class PriceMapConverter implements AttributeConverter<Map<String, BigDecimal>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, BigDecimal> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Error converting price map to JSON", e);
            return null;
        }
    }

    @Override
    public Map<String, BigDecimal> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(dbData, new TypeReference<Map<String, BigDecimal>>() {});
        } catch (JsonProcessingException e) {
            log.error("Error converting JSON to price map", e);
            return new HashMap<>();
        }
    }

}