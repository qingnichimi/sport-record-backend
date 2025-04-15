package com.sport.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Map {
    private String id;
    private String polyline;
    private String summaryPolyline;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPolyline() {
        return polyline;
    }

    public void setPolyline(String polyline) {
        this.polyline = polyline;
    }

    public String getSummaryPolyline() {
        return summaryPolyline;
    }

    public void setSummaryPolyline(String summaryPolyline) {
        this.summaryPolyline = summaryPolyline;
    }
}
