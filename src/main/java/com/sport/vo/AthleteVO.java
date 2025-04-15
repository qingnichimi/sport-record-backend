package com.sport.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AthleteVO {
    private long id;
    private String username;
    private int badgeTypeId;
    private String profileMedium;
    private String profile;
    private Integer ftp;
    private int weight;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getBadgeTypeId() {
        return badgeTypeId;
    }

    public void setBadgeTypeId(int badgeTypeId) {
        this.badgeTypeId = badgeTypeId;
    }

    public String getProfileMedium() {
        return profileMedium;
    }

    public void setProfileMedium(String profileMedium) {
        this.profileMedium = profileMedium;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public Integer getFtp() {
        return ftp;
    }

    public void setFtp(Integer ftp) {
        this.ftp = ftp;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }
}
