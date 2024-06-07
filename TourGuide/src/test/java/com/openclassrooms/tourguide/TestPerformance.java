package com.openclassrooms.tourguide;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.openclassrooms.tourguide.user.UserReward;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

import static org.junit.jupiter.api.Assertions.*;

public class TestPerformance {

    /*
     * A note on performance improvements:
     *
     * The number of users generated for the high volume tests can be easily
     * adjusted via this method:
     *
     * InternalTestHelper.setInternalUserNumber(100000);
     *
     *
     * These tests can be modified to suit new solutions, just as long as the
     * performance metrics at the end of the tests remains consistent.
     *
     * These are performance metrics that we are trying to hit:
     *
     * highVolumeTrackLocation: 100,000 users within 15 minutes:
     * assertTrue(TimeUnit.MINUTES.toSeconds(15) >=
     * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
     *
     * highVolumeGetRewards: 100,000 users within 20 minutes:
     * assertTrue(TimeUnit.MINUTES.toSeconds(20) >=
     * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
     */

    private Logger logger = LoggerFactory.getLogger(TestPerformance.class);


    @Test
    public void highVolumeTrackLocation() {
        GpsUtil gpsUtil = new GpsUtil();
        RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
        // Users should be incremented up to 100,000, and test finishes within 15
        // minutes
        InternalTestHelper.setInternalUserNumber(100);
        TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

        List<User> allUsers = new ArrayList<>();
        allUsers = tourGuideService.getAllUsers();

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        for (User user : allUsers) {
            tourGuideService.trackUserLocation(user);
        }
        stopWatch.stop();
        tourGuideService.tracker.stopTracking();

        System.out.println("highVolumeTrackLocation: Time Elapsed: "
                + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
        assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
    }

    @Test
    public void highVolumeTrackLocationAsync() throws InterruptedException, ExecutionException {
        GpsUtil gpsUtil = new GpsUtil();
        RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

        InternalTestHelper.setInternalUserNumber(100000);
        TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

        List<User> allUsers = tourGuideService.getAllUsers();

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        List<CompletableFuture<VisitedLocation>> locationFutures = allUsers.stream()
                .map(tourGuideService::trackUserLocationAsync)
                .toList();

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(locationFutures.toArray(new CompletableFuture[0]));
        allFutures.get();

        stopWatch.stop();
        tourGuideService.tracker.stopTracking();

        System.out.println(allUsers.size());
        System.out.println("highVolumeTrackLocation: Time Elapsed: "
                + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
        assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
    }

    @Test
    public void testTrackUserLocationConsistency() throws ExecutionException, InterruptedException {
        GpsUtil gpsUtil = new GpsUtil();
        RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
        // Users should be incremented up to 100,000, and test finishes within 15
        // minutes
        InternalTestHelper.setInternalUserNumber(100);
        TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

        // Create two lists to test the output of both Sync and Async methods
        List<User> allUsers = tourGuideService.getAllUsers();
        List<User> allUsersAsync = new ArrayList<>(allUsers);


        // Track users synchronously
        StopWatch stopWatchSync = new StopWatch();
        stopWatchSync.start();
        for (User user : allUsers) {
            tourGuideService.trackUserLocation(user);
        }
        stopWatchSync.stop();
        tourGuideService.tracker.stopTracking();

        // Track users asynchronously
        StopWatch stopWatchAsync = new StopWatch();
        stopWatchAsync.start();
        List<CompletableFuture<VisitedLocation>> locationFutures = allUsersAsync.stream()
                .map(tourGuideService::trackUserLocationAsync)
                .toList();

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(locationFutures.toArray(new CompletableFuture[0]));
        allFutures.get();
        stopWatchAsync.stop();
        tourGuideService.tracker.stopTracking();

        //Assert that the asynchronous method is faster
        assertTrue((stopWatchSync.getTime()) > (stopWatchAsync.getTime()));
        System.out.println("Sync time: " + stopWatchSync.getTime() + " ms");
        System.out.println("Async time: " + stopWatchAsync.getTime() + " ms");

        //Assert that the synchronous and asynchronous methods yield the same result

        for (int i = 0; i < allUsers.size(); i++) {
            User userSync = allUsers.get(i);
            User userAsync = allUsersAsync.get(i);

            // Assert that the number of visited locations are the same
            List<VisitedLocation> visitedLocationsSync = userSync.getVisitedLocations();
            List<VisitedLocation> visitedLocationsAsync = userAsync.getVisitedLocations();
            assertEquals(visitedLocationsSync.size(), visitedLocationsAsync.size());

            // Assert that the visited locations are the same
            for (int j = 0; j < visitedLocationsSync.size(); j++) {
                assertEquals(visitedLocationsSync.get(j).location.latitude, visitedLocationsAsync.get(j).location.latitude);
                assertEquals(visitedLocationsSync.get(j).location.longitude, visitedLocationsAsync.get(j).location.longitude);
            }

            // Assert that the rewards are the same
            List<UserReward> userRewardsSync = userSync.getUserRewards();
            List<UserReward> userRewardsAsync = userAsync.getUserRewards();
            assertEquals(userRewardsSync.size(), userRewardsAsync.size());
            for (int k = 0; k < userRewardsSync.size(); k++) {
                assertEquals(userRewardsSync.get(k).getRewardPoints(), userRewardsAsync.get(k).getRewardPoints());
            }
        }
    }

    @Test
    public void highVolumeGetRewards() {
        GpsUtil gpsUtil = new GpsUtil();
        RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

        // Users should be incremented up to 100,000, and test finishes within 20
        // minutes
        InternalTestHelper.setInternalUserNumber(100);
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

        Attraction attraction = gpsUtil.getAttractions().get(0);
        List<User> allUsers = tourGuideService.getAllUsers();

        allUsers.forEach(u -> u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date())));

        allUsers.forEach(rewardsService::calculateRewards);

        for (User user : allUsers) {
            assertFalse(user.getUserRewards().isEmpty());
        }
        stopWatch.stop();
        tourGuideService.tracker.stopTracking();

        System.out.println("highVolumeGetRewards: Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime())
                + " seconds.");
        assertTrue(TimeUnit.MINUTES.toSeconds(20) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
    }

    @Test
	public void highVolumeGetBatchRewards() {
		// Initialize dependencies
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		// Users should be incremented up to 100,000, and test finishes within 20 minutes
		InternalTestHelper.setInternalUserNumber(100000);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		// Start stopwatch for the entire test
		StopWatch totalStopWatch = new StopWatch();
		totalStopWatch.start();

		// Get the list of all users
		List<User> allUsers = tourGuideService.getAllUsers();

		// Add visited locations for all users at a specific attraction
		Attraction attraction = gpsUtil.getAttractions().get(0);
		allUsers.forEach(u -> u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date())));

        AtomicInteger calculatedRewards = new AtomicInteger();

        // Calculate rewards for all users in one call
		StopWatch loopStopWatch = new StopWatch(); // Create a new StopWatch for measuring loop time
		loopStopWatch.start(); // Start the stopwatch for the loop
		rewardsService.calculateBatchRewards(allUsers, calculatedRewards);
		loopStopWatch.stop(); // Stop the stopwatch for the loop

		// Calculate total time elapsed
		totalStopWatch.stop();
		long totalTime = totalStopWatch.getTime();

		// Output loop time and total time
		System.out.println("Total time (ms) for calculating rewards for all users: " + loopStopWatch.getTime());
		System.out.println("Total time (ms) elapsed for the test: " + totalTime);

		// Print total tracked users
		System.out.println("Total tracked users: " + allUsers.size());

		// Assertions or further processing if needed
		for (User user : allUsers) {
            assertFalse(user.getUserRewards().isEmpty());
		}

		// Ensure test finishes within 20 minutes
		assertTrue(TimeUnit.MINUTES.toSeconds(20) >= TimeUnit.MILLISECONDS.toSeconds(totalTime));
	}

    @Test
    public void testCalculateRewardsConsistency() {

        GpsUtil gpsUtil = new GpsUtil();
        RewardCentral rewardCentral = new RewardCentral();
        RewardsService rewardsServiceAsync = new RewardsService(gpsUtil, rewardCentral);
        RewardsService rewardsServiceSync = new RewardsService(gpsUtil, rewardCentral);

        // Users should be incremented up to 100,000, and test finishes within 20 minutes
        InternalTestHelper.setInternalUserNumber(100);
        TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsServiceAsync);

        // Get the list of all users
        List<User> allUsers = tourGuideService.getAllUsers();

        // Add visited locations for all users at a specific attraction
        Attraction attraction = gpsUtil.getAttractions().get(0);
        allUsers.forEach(u -> u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date())));

        // Create second list to process asynchronously and be able to compare both lists
        List<User> allUsersAsync = new ArrayList<>(allUsers);

        // Calculate rewards for all users synchronously
        StopWatch stopWatchSync = new StopWatch();
        stopWatchSync.start();
        allUsers.forEach(rewardsServiceSync::calculateRewards);
        stopWatchSync.stop();

        // Calculate rewards for all users asynchronously
        StopWatch stopWatchAsync = new StopWatch();
        stopWatchAsync.start();
        AtomicInteger calculatedRewardsAsync = new AtomicInteger();
        rewardsServiceAsync.calculateBatchRewards(allUsersAsync, calculatedRewardsAsync);
        stopWatchAsync.stop();

        //Assert that the asynchronous method is faster
        assertTrue((stopWatchSync.getTime()) > (stopWatchAsync.getTime()));
        System.out.println("Sync time: " + stopWatchSync.getTime() + " ms");
        System.out.println("Async time: " + stopWatchAsync.getTime() + " ms");

        // Compare that sync and async method provide the same result
        for (int i = 0; i < allUsers.size(); i++) {
            List<UserReward> userRewardsSync = allUsers.get(i).getUserRewards();
            List<UserReward> userRewardsAsync = allUsersAsync.get(i).getUserRewards();

            assertEquals(userRewardsSync, userRewardsAsync);
            assertEquals(userRewardsSync.size(), userRewardsAsync.size());
            assertTrue(userRewardsSync.containsAll(userRewardsAsync));
        }
    }
}
