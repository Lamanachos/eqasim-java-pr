package org.eqasim.core.scenario.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.core.simulation.mode_choice.EqasimModeChoiceModule;
import org.eqasim.core.simulation.termination.EqasimTerminationConfigGroup;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contribs.discrete_mode_choice.modules.ConstraintModule;
import org.matsim.contribs.discrete_mode_choice.modules.DiscreteModeChoiceConfigurator;
import org.matsim.contribs.discrete_mode_choice.modules.EstimatorModule;
import org.matsim.contribs.discrete_mode_choice.modules.ModelModule.ModelType;
import org.matsim.contribs.discrete_mode_choice.modules.SelectorModule;
import org.matsim.contribs.discrete_mode_choice.modules.TourFinderModule;
import org.matsim.contribs.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.ControllerConfigGroup.RoutingAlgorithmType;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.config.groups.RoutingConfigGroup.AccessEgressType;
import org.matsim.core.config.groups.RoutingConfigGroup.TeleportedModeParams;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup.ActivityParams;
import org.matsim.core.config.groups.ScoringConfigGroup.ModeParams;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;

public class GenerateConfig {
	protected final List<String> ACTIVITY_TYPES = Arrays.asList("home", "work", "education", "shop", "leisure", "other",
			"freight_loading", "freight_unloading", "outside");

	protected final List<String> MODES = Arrays.asList("walk", "bike", "pt", "car", "car_passenger", "truck",
			"outside");

	private final List<String> NETWORK_MODES = Arrays.asList("car", "car_passenger", "truck");

	private final CommandLine cmd;
	private final String prefix;
	private final double sampleSize;
	private final int randomSeed;
	private final int threads;

	public GenerateConfig(CommandLine cmd, String prefix, double sampleSize, int randomSeed, int threads) {
		this.sampleSize = sampleSize;
		this.prefix = prefix;
		this.cmd = cmd;
		this.randomSeed = randomSeed;
		this.threads = threads;
	}

	/**
	 * This value is the last resort to stop the simulation, in case the termination
	 * criterion is never fulfilled. Otherwise, the simulation is stopped when the
	 * termination criterion kicks in.
	 */
	private final static int DEFAULT_ITERATIONS = 1000;

	protected void adaptConfiguration(Config config) {
		// General settings

		config.controller().setFirstIteration(0);
		config.controller().setLastIteration(DEFAULT_ITERATIONS);
		config.controller().setWriteEventsInterval(DEFAULT_ITERATIONS);
		config.controller().setWritePlansInterval(DEFAULT_ITERATIONS);
		config.controller().setOutputDirectory("simulation_output");
		config.controller().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);

		config.global().setRandomSeed(randomSeed);
		config.global().setNumberOfThreads(threads);

		config.controller().setRoutingAlgorithmType(RoutingAlgorithmType.SpeedyALT);

		config.transit().setUseTransit(true);

		// QSim settings
		config.qsim().setEndTime(30.0 * 3600.0);
		config.qsim().setNumberOfThreads(Math.min(12, threads));
		config.qsim().setFlowCapFactor(sampleSize);
		config.qsim().setStorageCapFactor(sampleSize);

		// Eqasim settings
		EqasimConfigGroup eqasimConfig = EqasimConfigGroup.get(config);
		eqasimConfig.setCrossingPenalty(3.0);
		eqasimConfig.setSampleSize(sampleSize);
		eqasimConfig.setAnalysisInterval(DEFAULT_ITERATIONS);
		
		// Termination settings
		EqasimTerminationConfigGroup terminationConfig = EqasimTerminationConfigGroup.getOrCreate(config);
		terminationConfig.setModes(MODES);

		// Scoring config
		ScoringConfigGroup scoringConfig = config.scoring();

		scoringConfig.setMarginalUtilityOfMoney(0.0);
		scoringConfig.setMarginalUtlOfWaitingPt_utils_hr(-1.0);

		for (String activityType : ACTIVITY_TYPES) {
			ActivityParams activityParams = scoringConfig.getActivityParams(activityType);

			if (activityParams == null) {
				activityParams = new ActivityParams(activityType);
				config.scoring().addActivityParams(activityParams);
			}

			activityParams.setScoringThisActivityAtAll(false);
		}

		// These parameters are only used by SwissRailRaptor. We configure the
		// parameters here in a way that SRR searches for the route with the shortest
		// travel time.
		for (String mode : MODES) {
			ModeParams modeParams = scoringConfig.getOrCreateModeParams(mode);

			modeParams.setConstant(0.0);
			modeParams.setMarginalUtilityOfDistance(0.0);
			modeParams.setMarginalUtilityOfTraveling(-1.0);
			modeParams.setMonetaryDistanceRate(0.0);
		}
		
		// Routing configuration
		RoutingConfigGroup routingConfig = config.routing();

		config.routing().setNetworkModes(NETWORK_MODES);

		config.routing().setAccessEgressType(AccessEgressType.accessEgressModeToLink);
		config.routing().setRoutingRandomness(0.0);

		TeleportedModeParams outsideParams = routingConfig.getOrCreateModeRoutingParams("outside");
		outsideParams.setBeelineDistanceFactor(1.0);
		outsideParams.setTeleportedModeSpeed(1000.0);

		TeleportedModeParams bikeParams = routingConfig.getOrCreateModeRoutingParams(TransportMode.bike);
		bikeParams.setBeelineDistanceFactor(1.4);
		bikeParams.setTeleportedModeSpeed(3.1); // 11.6 km/h

		TeleportedModeParams walkParams = routingConfig.getOrCreateModeRoutingParams(TransportMode.walk);
		walkParams.setBeelineDistanceFactor(1.3);
		walkParams.setTeleportedModeSpeed(1.2); // 4.32 km/h

		// Travel time calculator
		config.travelTimeCalculator().setAnalyzedModes(new HashSet<>(NETWORK_MODES));
		config.travelTimeCalculator().setFilterModes(true);
		config.travelTimeCalculator().setSeparateModes(false);

		// Discrete mode choice
		DiscreteModeChoiceConfigurator.configureAsModeChoiceInTheLoop(config, 0.05);

		DiscreteModeChoiceConfigGroup dmcConfig = (DiscreteModeChoiceConfigGroup) config.getModules()
				.get(DiscreteModeChoiceConfigGroup.GROUP_NAME);

		dmcConfig.setModelType(ModelType.Tour);
		dmcConfig.setPerformReroute(false);

		dmcConfig.setSelector(SelectorModule.MULTINOMIAL_LOGIT);

		dmcConfig.setTripEstimator(EqasimModeChoiceModule.UTILITY_ESTIMATOR_NAME);
		dmcConfig.setTourEstimator(EstimatorModule.CUMULATIVE);
		dmcConfig.setCachedModes(Arrays.asList("car", "bike", "pt", "walk", "car_passenger", "truck"));

		dmcConfig.setTourFinder(TourFinderModule.ACTIVITY_BASED);
		dmcConfig.getActivityTourFinderConfigGroup().setActivityTypes(Arrays.asList("home", "outside"));
		dmcConfig.setModeAvailability("unknown");

		dmcConfig.setTourConstraints(
				Arrays.asList(EqasimModeChoiceModule.VEHICLE_TOUR_CONSTRAINT, ConstraintModule.FROM_TRIP_BASED));
		dmcConfig.setTripConstraints(Arrays.asList(ConstraintModule.TRANSIT_WALK,
				EqasimModeChoiceModule.PASSENGER_CONSTRAINT_NAME, EqasimModeChoiceModule.OUTSIDE_CONSTRAINT_NAME));

		dmcConfig.setHomeFinder(EqasimModeChoiceModule.HOME_FINDER);
		dmcConfig.getVehicleTourConstraintConfig().setRestrictedModes(Arrays.asList("car", "bike"));

		dmcConfig.setTourFilters(Arrays.asList(EqasimModeChoiceModule.OUTSIDE_FILTER_NAME,
				EqasimModeChoiceModule.TOUR_LENGTH_FILTER_NAME));

		// Set up modes

		eqasimConfig.setEstimator(TransportMode.car, EqasimModeChoiceModule.CAR_ESTIMATOR_NAME);
		eqasimConfig.setEstimator(TransportMode.pt, EqasimModeChoiceModule.PT_ESTIMATOR_NAME);
		eqasimConfig.setEstimator(TransportMode.bike, EqasimModeChoiceModule.BIKE_ESTIMATOR_NAME);
		eqasimConfig.setEstimator(TransportMode.walk, EqasimModeChoiceModule.WALK_ESTIMATOR_NAME);

		for (String mode : Arrays.asList("outside", "car_passenger", "truck")) {
			eqasimConfig.setEstimator(mode, EqasimModeChoiceModule.ZERO_ESTIMATOR_NAME);
		}

		eqasimConfig.setCostModel(TransportMode.car, EqasimModeChoiceModule.ZERO_COST_MODEL_NAME);
		eqasimConfig.setCostModel(TransportMode.pt, EqasimModeChoiceModule.ZERO_COST_MODEL_NAME);

		// To make sure trips arriving later than the next activity end time are taken into account when routing the next trip during mode choice
		config.plans().setTripDurationHandling(PlansConfigGroup.TripDurationHandling.shiftActivityEndTimes);

		// Update paths
		config.network().setInputFile(prefix + "network.xml.gz");
		config.plans().setInputFile(prefix + "population.xml.gz");
		config.households().setInputFile(prefix + "households.xml.gz");
		config.facilities().setInputFile(prefix + "facilities.xml.gz");
		config.transit().setTransitScheduleFile(prefix + "transit_schedule.xml.gz");
		config.transit().setVehiclesFile(prefix + "transit_vehicles.xml.gz");
	}

	public void run(Config config) throws ConfigurationException {
		// Adapt config
		adaptConfiguration(config);

		// Apply command line
		cmd.applyConfiguration(config);
	}
}
