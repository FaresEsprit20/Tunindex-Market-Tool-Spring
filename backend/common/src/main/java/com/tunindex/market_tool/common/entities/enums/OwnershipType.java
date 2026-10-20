package com.tunindex.market_tool.common.entities.enums;


import lombok.Getter;

@Getter
public enum OwnershipType {
    PRIVATE("Private Sector"),
    GOVERNMENT("Government Owned");

    private final String description;

    OwnershipType(String description) {
        this.description = description;
    }

}