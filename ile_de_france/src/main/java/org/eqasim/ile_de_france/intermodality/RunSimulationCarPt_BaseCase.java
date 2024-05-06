package org.eqasim.ile_de_france.intermodality;

import org.eqasim.core.components.ParkRideManager;
import org.eqasim.core.components.car_pt.routing.EqasimCarPtModule;
import org.eqasim.core.components.car_pt.routing.EqasimPtCarModule;
import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.core.simulation.analysis.EqasimAnalysisModule;
import org.eqasim.core.simulation.mode_choice.EqasimModeChoiceModuleCarPt;
import org.eqasim.core.tools.TestCarPtPara;
import org.eqasim.ile_de_france.IDFConfigurator;
import org.eqasim.ile_de_france.mode_choice.IDFModeChoiceModuleCarPt;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contribs.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ScoringConfigGroup.ActivityParams;
import org.matsim.core.config.groups.ScoringConfigGroup.ModeParams;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class RunSimulationCarPt_BaseCase {
	static String outputPath = "simulation_output/1pc_pr_test/PTCar_BaseCase_rer_train_";
	static public void main(String[] args) throws ConfigurationException, IOException {
		args = new String[] {"--config-path", "ile_de_france/scenarios/idf_1pc/ile_de_france_config.xml"};
		String locationFile = "ile_de_france/scenarios/parcs-relais-idf_rer_train_outside_paris.csv";


		//double[] car_pt_constant = {1.50, 1.25, 1.00, 0.75, 0.50, 0.25};
		double[] car_pt_constant = {0.75};
		TestCarPtPara tp = new TestCarPtPara();

		for (int i = 0; i < car_pt_constant.length; i++) {
			tp.setPara(car_pt_constant[i]);
			tp.setCarPtSavePath(outputPath + car_pt_constant[i]);
			CommandLine cmd = new CommandLine.Builder(args) //
					.requireOptions("config-path") //
					.allowPrefixes("mode-choice-parameter", "cost-parameter") //
					.build();
			IDFConfigurator configurator = new IDFConfigurator();
			Config config = ConfigUtils.loadConfig(cmd.getOptionStrict("config-path"), configurator.getConfigGroups());

			//modify some parameters in config file
			config.controller().setLastIteration(60);
			config.controller().setOutputDirectory(outputPath + car_pt_constant[i]);
			config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);

			// multistage car trips
			config.routing().setAccessEgressType(RoutingConfigGroup.AccessEgressType.accessEgressModeToLink);
			config.qsim().setUsingTravelTimeCheckInTeleportation( true );



			for (ReplanningConfigGroup.StrategySettings ss : config.replanning().getStrategySettings()) {
				if (ss.getStrategyName().equals("KeepLastSelected")) {
					ss.setWeight(0.95);
				}
				if (ss.getStrategyName().equals("DiscreteModeChoice")) {
					ss.setWeight(0.05);
				}
			}


			// Eqasim config definition to add the mode car_pt estimation
			EqasimConfigGroup eqasimConfig = EqasimConfigGroup.get(config);
			eqasimConfig.setEstimator("car_pt", "CarPtUtilityEstimator");
			eqasimConfig.setEstimator("pt_car", "PtCarUtilityEstimator");

			// Scoring config definition to add the mode car_pt parameters
			ScoringConfigGroup scoringConfig = config.scoring();
			ModeParams carPtParams = new ModeParams("car_pt");
			ModeParams ptCarParams = new ModeParams("pt_car");
			scoringConfig.addModeParams(carPtParams);
			scoringConfig.addModeParams(ptCarParams);

			// "car_pt interaction" definition
			ActivityParams paramscarPtInterAct = new ActivityParams("carPt interaction");
			paramscarPtInterAct.setTypicalDuration(100.0);
			paramscarPtInterAct.setScoringThisActivityAtAll(false);

			// "pt_car interaction" definition
			ActivityParams paramsPtCarInterAct = new ActivityParams("ptCar interaction");
			paramsPtCarInterAct.setTypicalDuration(100.0);
			paramsPtCarInterAct.setScoringThisActivityAtAll(false);

			// Adding "car_pt interaction" to the scoring
			scoringConfig.addActivityParams(paramscarPtInterAct);
			scoringConfig.addActivityParams(paramsPtCarInterAct);

			// DMC config definition
			// Adding the mode "car_pt" and "pt_car" to CachedModes
			DiscreteModeChoiceConfigGroup dmcConfig = (DiscreteModeChoiceConfigGroup) config.getModules()
					.get(DiscreteModeChoiceConfigGroup.GROUP_NAME);
			Collection<String> cachedModes = new HashSet<>(dmcConfig.getCachedModes());
			cachedModes.add("car_pt");
			cachedModes.add("pt_car");
			dmcConfig.setCachedModes(cachedModes);


			// Activation of constraint intermodal modes Using
			Collection<String> tourConstraints = new HashSet<>(dmcConfig.getTourConstraints());
			tourConstraints.add("IntermodalModesConstraint");
			dmcConfig.setTourConstraints(tourConstraints);

			cmd.applyConfiguration(config);
			Scenario scenario = ScenarioUtils.createScenario(config);
			configurator.configureScenario(scenario);
			ScenarioUtils.loadScenario(scenario);
			configurator.adjustScenario(scenario);
			Controler controller = new Controler(scenario);
			configurator.configureController(controller);

			//set Park and ride lot locations
			List<Coord> parkRideCoords;
			readParkRideCoordsFromFile readFile = new readParkRideCoordsFromFile(locationFile);
			parkRideCoords = readFile.readCoords;
			ParkRideManager parkRideManager = new ParkRideManager();
			parkRideManager.setParkRideCoords(parkRideCoords);
			Network network = scenario.getNetwork();
			parkRideManager.setNetwork(network);
//////////
			controller.addOverridingModule(new EqasimAnalysisModule());
			controller.addOverridingModule(new EqasimModeChoiceModuleCarPt());
			controller.addOverridingModule(new IDFModeChoiceModuleCarPt(cmd, parkRideCoords, scenario.getNetwork(), scenario.getPopulation().getFactory()));
			controller.addOverridingModule(new EqasimCarPtModule(parkRideCoords));
			controller.addOverridingModule(new EqasimPtCarModule(parkRideCoords));

			controller.run();
		}

	}

}