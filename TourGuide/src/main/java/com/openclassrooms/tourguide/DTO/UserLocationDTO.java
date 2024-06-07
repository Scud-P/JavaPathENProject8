package com.openclassrooms.tourguide.DTO;
import gpsUtil.location.VisitedLocation;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class UserLocationDTO {

    private double userLatitude;
    private double userLongitude;

    public UserLocationDTO(VisitedLocation visitedLocation) {
        this.userLatitude = visitedLocation.location.latitude;
        this.userLongitude = visitedLocation.location.longitude;
    }
}




