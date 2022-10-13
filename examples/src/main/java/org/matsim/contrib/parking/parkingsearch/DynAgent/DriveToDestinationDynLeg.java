/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
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

package org.matsim.contrib.parking.parkingsearch.DynAgent;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.dynagent.DriverDynLeg;
import org.matsim.contrib.parking.parkingsearch.events.StartParkingSearchEvent;
import org.matsim.contrib.parking.parkingsearch.manager.MultipleParkingTypeParkingManager;
import org.matsim.contrib.parking.parkingsearch.search.ParkingSearchLogic;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.facilities.ActivityFacility;
import org.matsim.vehicles.Vehicle;

import java.util.List;

public class DriveToDestinationDynLeg implements DriverDynLeg {
    protected final NetworkRoute route;
    protected int currentLinkIdx;
    protected final String mode;
    protected Tuple<Id<Link>, Id<Link>> currentAndNextParkLink = null;
    protected Id<Link> currentLinkId;
    protected boolean parkingMode = false;
//    protected ParkingSearchManager parkingManager;
    protected MultipleParkingTypeParkingManager parkingManager;
    protected Id<Vehicle> vehicleId;
    protected ParkingSearchLogic logic;
    protected MobsimTimer timer;
    protected EventsManager events;
    protected boolean hasFoundParking = false;
    private final Id<ActivityFacility> parkingFacilityId;

    public DriveToDestinationDynLeg(String mode, NetworkRoute route, ParkingSearchLogic logic, MultipleParkingTypeParkingManager parkingManager,
                                    Id<Vehicle> vehicleId, MobsimTimer timer, EventsManager events, Id<ActivityFacility> parkingFacilityId) {
        this.mode = mode;
        this.route = route;
        this.currentLinkIdx = -1;
        this.currentLinkId = route.getStartLinkId();
        this.logic = logic;
        this.parkingManager = parkingManager;
        this.vehicleId = vehicleId;
        this.timer = timer;
        this.events = events;
        this.parkingFacilityId = parkingFacilityId;
    }

    @Override
    public void movedOverNode(Id<Link> newLinkId) {
        currentLinkIdx++;
        currentLinkId = newLinkId;
        double time = timer.getTimeOfDay();
        if (!parkingMode) {
            if (currentLinkId.equals(this.getDestinationLinkId())) {
                this.parkingMode = true;
                this.events.processEvent(new StartParkingSearchEvent(time, vehicleId, currentLinkId));
                hasFoundParking = parkingManager.reserveSpaceAtFacilityIdIfAvailable(vehicleId, parkingFacilityId, time, time);
            }
        } else {
            hasFoundParking = parkingManager.reserveSpaceAtFacilityIdIfAvailable(vehicleId, parkingFacilityId, time, time);
        }
    }

    @Override
    public Id<Link> getNextLinkId() {
        if (!parkingMode) {
            List<Id<Link>> linkIds = route.getLinkIds();

            if (currentLinkIdx == linkIds.size() - 1) {
                return route.getEndLinkId();
            }

            return linkIds.get(currentLinkIdx + 1);

        } else {
            if (hasFoundParking) {
                // easy, we can just park where at our destination link
                return null;
            } else {
                if (this.currentAndNextParkLink != null) {
                    if (currentAndNextParkLink.getFirst().equals(currentLinkId)) {
                        // we already calculated this
                        return currentAndNextParkLink.getSecond();
                    }
                }
                // need to find the next link
                Id<Link> nextLinkId = this.logic.getNextLink(currentLinkId, vehicleId, mode);
                currentAndNextParkLink = new Tuple<Id<Link>, Id<Link>>(currentLinkId, nextLinkId);
                return nextLinkId;

            }
        }
    }

    @Override
    public Id<Link> getDestinationLinkId() {
        // used only for teleportation
        return route.getEndLinkId();
    }

    @Override
    public String getMode() {
        return mode;
    }


    @Override
    public Id<Vehicle> getPlannedVehicleId()
    {
        return this.vehicleId;
    }


    @Override
    public void arrivedOnLinkByNonNetworkMode(Id<Link> linkId) {
        if (!getDestinationLinkId().equals(linkId)) {
            throw new IllegalStateException();
        }

        currentLinkIdx = route.getLinkIds().size();
    }

    @Override
    public OptionalTime getExpectedTravelTime() {
        // travel time estimation does not take into account time required for
        // parking search
        // TODO add travel time at the destination link??
        return route.getTravelTime();
    }

    public Double getExpectedTravelDistance() {
        // travel time estimation does not take into account the distance
        // required for parking search
        // TODO add length of the destination link??
        return route.getDistance();
    }
}
