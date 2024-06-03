package com.openclassrooms.tourguide.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class AttractionDTO {
    private String attractionName;
    private double attractionLatitude;
    private double attractionLongitude;
    private double distance;
    private int rewardPoints;
}
