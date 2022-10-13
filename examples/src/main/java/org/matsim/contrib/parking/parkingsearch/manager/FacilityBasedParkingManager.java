/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.contrib.parking.parkingsearch.manager;

import com.google.inject.Inject;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.parking.parkingsearch.ParkingUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.vehicles.Vehicle;

import java.util.*;
import java.util.Map.Entry;

/**
 * @author  jbischoff, schlenther
 *
 */
public class FacilityBasedParkingManager implements ParkingSearchManager {

	protected Map<Id<Link>, Integer> capacity = new HashMap<>();
	protected Map<Id<ActivityFacility>, MutableLong> occupation = new HashMap<>();
	protected 	Map<Id<ActivityFacility>, ActivityFacility> parkingFacilities;
	protected Map<Id<Vehicle>, Id<ActivityFacility>> parkingLocations = new HashMap<>();
	protected Map<Id<Vehicle>, Id<ActivityFacility>> parkingReservation = new HashMap<>();
	protected Map<Id<Vehicle>, Id<Link>> parkingLocationsOutsideFacilities = new HashMap<>();
	protected Map<Id<Link>, Set<Id<ActivityFacility>>> facilitiesPerLink = new HashMap<>();

    protected Network network;

	@Inject
	public FacilityBasedParkingManager(Scenario scenario) {
		this.network = scenario.getNetwork();
		parkingFacilities = scenario.getActivityFacilities()
				.getFacilitiesForActivityType(ParkingUtils.PARKACTIVITYTYPE);
		Logger.getLogger(getClass()).info(parkingFacilities.toString());

		for (ActivityFacility fac : this.parkingFacilities.values()) {
			Id<Link> linkId = fac.getLinkId();
			Set<Id<ActivityFacility>> parkingOnLink = new HashSet<>();
			if (this.facilitiesPerLink.containsKey(linkId)) {
				parkingOnLink = this.facilitiesPerLink.get(linkId);
			}
			parkingOnLink.add(fac.getId());
			this.facilitiesPerLink.put(linkId, parkingOnLink);
			this.occupation.put(fac.getId(), new MutableLong(0));

		}
	}

	@Override
	public boolean reserveSpaceIfVehicleCanParkHere(Id<Vehicle> vehicleId, Id<Link> linkId) {

	}

	private boolean linkIdHasAvailableParkingForVehicle(Id<Link> linkId, Id<Vehicle> vid) {
		// Logger.getLogger(getClass()).info("link "+linkId+" vehicle "+vid);
		if (!this.facilitiesPerLink.containsKey(linkId)) {
			// this implies: If no parking facility is present, we suppose that
			// we can park freely (i.e. the matsim standard approach)
			// it also means: a link without any parking spaces should have a
			// parking facility with 0 capacity.
			// Logger.getLogger(getClass()).info("link not listed as parking
			// space, we will say yes "+linkId);

			return true;
		}
		Set<Id<ActivityFacility>> parkingFacilitiesAtLink = this.facilitiesPerLink.get(linkId);
		for (Id<ActivityFacility> fac : parkingFacilitiesAtLink) {
			double cap = this.parkingFacilities.get(fac).getActivityOptions().get(ParkingUtils.PARKACTIVITYTYPE)
					.getCapacity();
			if (this.occupation.get(fac).doubleValue() < cap) {
				// Logger.getLogger(getClass()).info("occ:
				// "+this.occupation.get(fac).toString()+" cap: "+cap);
				this.occupation.get(fac).increment();
				this.parkingReservation.put(vid, fac);

				return true;
			}
		}
		return false;
	}

	@Override
	public Id<Link> getVehicleParkingLocation(Id<Vehicle> vehicleId) {
		if (this.parkingLocations.containsKey(vehicleId)) {
			return this.parkingFacilities.get(this.parkingLocations.get(vehicleId)).getLinkId();
		} else if (this.parkingLocationsOutsideFacilities.containsKey(vehicleId)) {
			return this.parkingLocationsOutsideFacilities.get(vehicleId);
		} else
			return null;
	}

	@Override
	public boolean parkVehicleHere(Id<Vehicle> vehicleId, Id<Link> linkId, double time) {
        return parkVehicleAtLink(vehicleId, linkId, time);
	}

	protected boolean parkVehicleAtLink(Id<Vehicle> vehicleId, Id<Link> linkId, double time) {


	}

	@Override
	public boolean reserveSpaceAtLinkIdIfVehicleCanParkHere(Id<Vehicle> vehicleId, Id<Link> linkId, double fromTime, double toTime) {
		boolean canPark = false;

		if (linkIdHasAvailableParkingForVehicle(linkId, vehicleId)) {
			canPark = true;
			// Logger.getLogger(getClass()).info("veh: "+vehicleId+" link
			// "+linkId + " can park "+canPark);
		}

		return canPark;
	}

	@Override
	public boolean reserveSpaceAtParkingFacilityIdIfVehicleCanParkHere(Id<Vehicle> vehicleId, Id<ActivityFacility> parkingFacilityId, double fromTime, double toTime) {
		return false;
	}

	@Override
	public Id<Link> getVehicleParkingLocationLinkId(Id<Vehicle> vehicleId) {
		return null;
	}

	@Override
	public Id<ActivityFacility> getVehicleParkingLocationParkingFacilityId(Id<Vehicle> vehicleId) {
		return null;
	}

	@Override
	public boolean parkVehicleAtLinkId(Id<Vehicle> vehicleId, Id<Link> linkId, double fromTime) {
		Set<Id<ActivityFacility>> parkingFacilitiesAtLink = this.facilitiesPerLink.get(linkId);
		if (parkingFacilitiesAtLink == null) {
			this.parkingLocationsOutsideFacilities.put(vehicleId, linkId);
			return true;
		} else {
			Id<ActivityFacility> facilityId = this.parkingReservation.remove(vehicleId);
			if (facilityId != null) {
				this.parkingLocations.put(vehicleId, facilityId);
				return true;
			} else {
				throw new RuntimeException("no parking reservation found for vehicle " + vehicleId.toString()
						+ "arrival on link " + linkId + " with parking restriction");
			}
		}
	}

	@Override
	public boolean parkVehicleAtParkingFacilityId(Id<Vehicle> vehicleId, Id<ActivityFacility> parkingFacilityId, double fromTime) {
		Id<ActivityFacility> facilityId = this.parkingReservation.remove(vehicleId);
		if (facilityId != null) {
			this.parkingLocations.put(vehicleId, facilityId);
			return true;
		} else {
			throw new RuntimeException("no parking reservation found for vehicle " + vehicleId.toString()
					+ "at facility " + parkingFacilityId);
		}
	}

	@Override
	public boolean unParkVehicleAtLinkId(Id<Vehicle> vehicleId, Id<Link> linkId, double toTime) {
		if (!this.parkingLocations.containsKey(vehicleId)) {
			this.parkingLocationsOutsideFacilities.remove(vehicleId);
			return true;

			// we assume the person parks somewhere else
		} else {
			Id<ActivityFacility> facilityId = this.parkingLocations.remove(vehicleId);
			this.occupation.get(facilityId).decrement();
			return true;
		}
	}

	@Override
	public boolean unParkVehicleAtParkingFacilityId(Id<Vehicle> vehicleId, Id<ActivityFacility> parkingFacilityId, double toTime) {
		return false;
	}

	@Override
	public List<String> produceStatistics() {
		List<String> stats = new ArrayList<>();
		for (Entry<Id<ActivityFacility>, MutableLong> e : this.occupation.entrySet()) {
			Id<Link> linkId = this.parkingFacilities.get(e.getKey()).getLinkId();
			double capacity = this.parkingFacilities.get(e.getKey()).getActivityOptions()
					.get(ParkingUtils.PARKACTIVITYTYPE).getCapacity();
			String s = linkId.toString() + ";" + e.getKey().toString() + ";" + capacity + ";" + e.getValue().toString();
			stats.add(s);
		}
		return stats;
	}

	public double getNrOfAllParkingSpacesOnLink (Id<Link> linkId){
		double allSpaces = 0;
		Set<Id<ActivityFacility>> parkingFacilitiesAtLink = this.facilitiesPerLink.get(linkId);
		if (!(parkingFacilitiesAtLink == null)) {
			for (Id<ActivityFacility> fac : parkingFacilitiesAtLink){
				allSpaces += this.parkingFacilities.get(fac).getActivityOptions().get(ParkingUtils.PARKACTIVITYTYPE).getCapacity();
			}
		}
		return allSpaces;
	}
	
	public double getNrOfFreeParkingSpacesOnLink (Id<Link> linkId){
		double allFreeSpaces = 0;
		Set<Id<ActivityFacility>> parkingFacilitiesAtLink = this.facilitiesPerLink.get(linkId);
		if (parkingFacilitiesAtLink == null) {
			return 0;
		} else {
			for (Id<ActivityFacility> fac : parkingFacilitiesAtLink){
				int cap = (int) this.parkingFacilities.get(fac).getActivityOptions().get(ParkingUtils.PARKACTIVITYTYPE).getCapacity();
				allFreeSpaces += (cap - this.occupation.get(fac).intValue());
			}
		}
		return allFreeSpaces;
	}


	@Override
	public void reset(int iteration) {
	}

	
}
