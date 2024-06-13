package com.openclassrooms.tourguide.user;

import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import lombok.Data;
import lombok.Getter;

@Data
public class UserReward {

	public final VisitedLocation visitedLocation;
	@Getter
	public final Attraction attraction;
	@Getter
    private int rewardPoints;
	public UserReward(VisitedLocation visitedLocation, Attraction attraction, int rewardPoints) {
		this.visitedLocation = visitedLocation;
		this.attraction = attraction;
		this.rewardPoints = rewardPoints;
	}
	
	public UserReward(VisitedLocation visitedLocation, Attraction attraction) {
		this.visitedLocation = visitedLocation;
		this.attraction = attraction;
	}
}
