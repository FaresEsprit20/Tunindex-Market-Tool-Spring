package com.tunindex.market_tool.api.dto.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEmailDto {
    private Integer id;
    private String email;
    private String firstName;
    private String lastName;
}