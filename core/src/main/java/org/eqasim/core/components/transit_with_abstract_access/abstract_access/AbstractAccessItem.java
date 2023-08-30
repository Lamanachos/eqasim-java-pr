package org.eqasim.core.components.transit_with_abstract_access.abstract_access;

import org.apache.commons.math3.analysis.function.Abs;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AbstractAccessItem {

    private final Id<AbstractAccessItem> id;

    private final TransitStopFacility centerStop;
    private final double radius;
    private final double avgSpeedToCenterStop;

    //private final String modeName;

    public AbstractAccessItem(Id<AbstractAccessItem> id, TransitStopFacility centerStop, double radius, double avgSpeedToCenterStop) {
        this.id = id;
        this.centerStop = centerStop;
        this.radius = radius;
        this.avgSpeedToCenterStop = avgSpeedToCenterStop;
    }

    public Id<AbstractAccessItem> getId() {
        return this.id;
    }

    public boolean applies(Coord coord) {
        return CoordUtils.calcEuclideanDistance(coord, this.centerStop.getCoord()) <= this.radius;
    }

    public boolean applies(double distanceToCenter) {
        return distanceToCenter <= this.radius;
    }
    public double getTimeToCenter(Coord coord) {
        double distanceToCenter = CoordUtils.calcEuclideanDistance(coord, this.centerStop.getCoord());
        if(this.applies(distanceToCenter)) {
            return distanceToCenter / this.avgSpeedToCenterStop;
        }
        return Double.MAX_VALUE;
    }

    public double getRadius() {
        return this.radius;
    }

    public static AbstractAccessItem getFastestAccessItemForCoord (Coord coord, Collection<AbstractAccessItem> accessItems) {
        AbstractAccessItem best = null;
        double minAccessTime = Double.MAX_VALUE;
        for(AbstractAccessItem accessItem: accessItems) {
            if(accessItem.applies(coord)) {
                double accessTime = accessItem.getTimeToCenter(coord);
                if(accessTime < minAccessTime) {
                    minAccessTime = accessTime;
                    best = accessItem;
                }
            }
        }
        return best;
    }

    public TransitStopFacility getCenterStop() {
        return this.centerStop;
    }

    public double getAvgSpeedToCenterStop() {
        return this.avgSpeedToCenterStop;
    }

    public boolean covers(AbstractAccessItem other) {
        if(!this.centerStop.getId().equals(other.centerStop.getId())) {
            return false;
        }
        return this.radius >= other.radius && this.avgSpeedToCenterStop <= other.avgSpeedToCenterStop;
    }

    public static IdMap<TransitStopFacility, List<AbstractAccessItem>> getAbstractAccessItemsByTransitStop(Collection<AbstractAccessItem> accessItems) {
        IdMap<TransitStopFacility, List<AbstractAccessItem>> itemsByTransitStop = new IdMap<>(TransitStopFacility.class);
        for(AbstractAccessItem item: accessItems) {
            if(!itemsByTransitStop.containsKey(item.getCenterStop().getId())) {
                itemsByTransitStop.put(item.getCenterStop().getId(), new ArrayList<>());
            }
            itemsByTransitStop.get(item.getCenterStop().getId()).add(item);
        }
        return itemsByTransitStop;
    }

    public static Collection<AbstractAccessItem> removeRedundantAbstractAccessItems(Collection<AbstractAccessItem> accessItems) {
        Collection<AbstractAccessItem> result = new ArrayList<>();
        for(AbstractAccessItem item1: accessItems) {
            boolean covered = false;
            for(AbstractAccessItem item2: accessItems) {
                if(item2.covers(item1)) {
                    covered = true;
                    break;
                }
            }
            if(!covered) {
                result.add(item1);
            }
        }
        return result;
    }
}
