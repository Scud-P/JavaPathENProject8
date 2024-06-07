package com.openclassrooms.tourguide.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclassrooms.tourguide.DTO.AttractionDTO;
import com.openclassrooms.tourguide.DTO.UserLocationDTO;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
    private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
    private final GpsUtil gpsUtil;
    private final RewardsService rewardsService;
    private final TripPricer tripPricer = new TripPricer();
    private final ExecutorService executorService;
    public final Tracker tracker;
    boolean testMode = true;

    public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
        this.gpsUtil = gpsUtil;
        this.rewardsService = rewardsService;

        // On utilise un CachedThreadPool, c'est casher ou pas?

        this.executorService = Executors.newCachedThreadPool();

        Locale.setDefault(Locale.US);

        if (testMode) {
            logger.info("TestMode enabled");
            logger.debug("Initializing users");
            initializeInternalUsers();
            logger.debug("Finished initializing users");
        }
        tracker = new Tracker(this);
        addShutDownHook();
    }

    public List<UserReward> getUserRewards(User user) {
        return user.getUserRewards();
    }

    public VisitedLocation getUserLocation(User user) {
        VisitedLocation visitedLocation = (!user.getVisitedLocations().isEmpty()) ? user.getLastVisitedLocation()
                : trackUserLocation(user);
        return visitedLocation;
    }

    public User getUser(String userName) {
        return internalUserMap.get(userName);
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(internalUserMap.values());
    }

    public void addUser(User user) {
        if (!internalUserMap.containsKey(user.getUserName())) {
            internalUserMap.put(user.getUserName(), user);
            userIdMap.put(user.getUserId(), user);
        }
    }

    public List<Provider> getTripDeals(User user) {
        int cumulativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
        List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
                user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
                user.getUserPreferences().getTripDuration(), cumulativeRewardPoints);
        user.setTripDeals(providers);
        System.out.println(providers);
        return providers;
    }

    public VisitedLocation trackUserLocation(User user) {
        VisitedLocation visitedLocation = this.gpsUtil.getUserLocation(user.getUserId());
        user.addToVisitedLocations(visitedLocation);
        this.rewardsService.calculateRewards(user);
        return visitedLocation;
    }

    public CompletableFuture<VisitedLocation> trackUserLocationAsync(User user) {
        return CompletableFuture.supplyAsync(() -> this.gpsUtil.getUserLocation(user.getUserId()), executorService)
                .thenApply(visitedLocation -> {
                    user.addToVisitedLocations(visitedLocation);
                    return visitedLocation;
                })
                .thenApply(visitedLocation -> {
                    this.rewardsService.calculateRewards(user);
                    return visitedLocation;
                });
    }


    public JSONObject getNearByAttractions(VisitedLocation visitedLocation) {

        UUID userId = visitedLocation.userId;
        User user = getUserById(userId);

        List<Attraction> nearestAttractions = rewardsService.getFiveNearestAttractions(visitedLocation.location);

        List<AttractionDTO> attractionDTOS = nearestAttractions.stream()
                .map(attraction -> new AttractionDTO(
                        attraction.attractionName,
                        attraction.latitude,
                        attraction.longitude,
                        rewardsService.getDistance(visitedLocation.location, attraction.latitude, attraction.longitude),
                        rewardsService.getRewardPoints(attraction, user)
                ))
                .sorted(Comparator.comparingDouble(AttractionDTO::getDistance))
                .toList();

        UserLocationDTO userLocationDTO = new UserLocationDTO(visitedLocation);

        //TODO second DTO pour visitedLocation + réponse

        JSONObject responseObject = new JSONObject();
        responseObject.put("userLocation", userLocationDTO);
        responseObject.put("nearestAttractions", attractionDTOS);

        return responseObject;
    }

    private void addShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                tracker.stopTracking();
            }
        });
    }



    /**********************************************************************************
     *
     * Methods Below: For Internal Testing
     *
     **********************************************************************************/
    private static final String tripPricerApiKey = "test-server-api-key";
    // Database connection will be used for external users, but for testing purposes
    // internal users are provided and stored in memory
    private final Map<String, User> internalUserMap = new HashMap<>();
    private final Map<UUID, User> userIdMap = new HashMap<>();


    private void initializeInternalUsers() {
        IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
            String userName = "internalUser" + i;
            String phone = "000";
            String email = userName + "@tourGuide.com";
            User user = new User(UUID.randomUUID(), userName, phone, email);
            generateUserLocationHistory(user);

            internalUserMap.put(userName, user);
            userIdMap.put(user.getUserId(), user);
        });
        logger.debug("Created {} internal test users.", InternalTestHelper.getInternalUserNumber());
    }

    private void generateUserLocationHistory(User user) {
        IntStream.range(0, 3).forEach(i -> {
            user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
                    new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
        });
    }

    private double generateRandomLongitude() {
        double leftLimit = -180;
        double rightLimit = 180;
        return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
    }

    private double generateRandomLatitude() {
        double leftLimit = -85.05112878;
        double rightLimit = 85.05112878;
        return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
    }

    private Date getRandomTime() {
        LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
        return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
    }

    public User getUserById(UUID userId) {
        return userIdMap.get(userId);
    }

}
