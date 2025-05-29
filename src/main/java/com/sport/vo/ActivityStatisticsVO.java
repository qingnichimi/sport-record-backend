package com.sport.vo;

import java.util.Map;

public record ActivityStatisticsVO(long totalActivities, double totalDistance, double currentYearDistance,
                                   double totalCity, Map distanceByYear, Map distanceByType,
                                   Map distanceByYearAndType) {
}
