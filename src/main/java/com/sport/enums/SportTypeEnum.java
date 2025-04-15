package com.sport.enums;

public enum SportTypeEnum {

    RUN("跑步", "Run"),
    RIDE("骑行", "Ride");

    private String name;

    private String value;

    SportTypeEnum(String name, String value) {
        this.name = name;
        this.value = value;
    }
}
