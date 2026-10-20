// UserEmailDto.java
package com.tunindex.market_tool.mailing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEmailDto {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
}