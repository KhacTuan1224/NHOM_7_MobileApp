package com.rescuefarm.service;
import com.rescuefarm.service.location.LocationPublishPolicy;
import org.junit.Test;
import static org.junit.Assert.*;
public class LocationPublishPolicyTest {
    @Test public void movingFixIsThrottledAndStationaryFixHasHeartbeat(){
        LocationPublishPolicy p=new LocationPublishPolicy();assertTrue(p.shouldPublish(10,106,0));p.published(10,106,0);
        assertFalse(p.shouldPublish(10.001,106,9999));assertTrue(p.shouldPublish(10.001,106,10000));
        assertFalse(p.shouldPublish(10,106,59999));assertTrue(p.shouldPublish(10,106,60000));
    }
    @Test public void jitterAndInvalidCoordinatesDoNotTriggerCloudWrites(){
        LocationPublishPolicy p=new LocationPublishPolicy();p.published(10,106,0);
        assertFalse(p.shouldPublish(10.00001,106,15000));assertFalse(p.shouldPublish(Double.NaN,106,60000));
        assertFalse(p.shouldPublish(91,106,60000));
    }
}
