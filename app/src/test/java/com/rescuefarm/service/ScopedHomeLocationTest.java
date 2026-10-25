package com.rescuefarm.service;
import com.rescuefarm.service.location.ScopedHomeLocation;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.Map;
public class ScopedHomeLocationTest {
    private static class Disk implements ScopedHomeLocation.Storage {
        final Map<String,String> data=new HashMap<>();
        public String get(String key){return data.get(key);}
        public void put(String key,String value){if(value==null)data.remove(key);else data.put(key,value);}
    }
    private ScopedHomeLocation.Selection point(String label){return new ScopedHomeLocation.Selection(10.77,106.7,label);}
    @Test public void selectionSurvivesNewScreenAndProcessForSameOwner(){
        Disk disk=new Disk();ScopedHomeLocation first=new ScopedHomeLocation(disk,"a");first.save(point("TP. Hồ Chí Minh"));
        assertEquals("TP. Hồ Chí Minh",new ScopedHomeLocation(disk,"a").read().label);
    }
    @Test public void guestIsSeparateAndAccountSelectionIsDeletedAtLogout(){
        Disk disk=new Disk();ScopedHomeLocation s=new ScopedHomeLocation(disk,null);s.save(point("Khách"));
        s.changeOwner("a");assertNull(s.read());s.save(point("Tài khoản A"));s.clearAccount("a");
        assertNull(disk.get("account"));s.changeOwner(null);assertEquals("Khách",s.read().label);
        s.changeOwner("a");assertNull(s.read());
    }
    @Test public void accountSwitchAndLateCleanupCannotExposeOrDeleteAnotherAccount(){
        Disk disk=new Disk();ScopedHomeLocation s=new ScopedHomeLocation(disk,"a");s.save(point("A"));
        s.changeOwner("b");assertNull(s.read());s.save(point("B"));s.clearAccount("a");assertEquals("B",s.read().label);
        assertNull(new ScopedHomeLocation(disk,"a").read());
    }
    @Test public void staleAccountFromPreviousProcessAndInvalidCacheAreNotRestoredForGuest(){
        Disk disk=new Disk();new ScopedHomeLocation(disk,"a").save(point("A"));
        ScopedHomeLocation guest=new ScopedHomeLocation(disk,null);assertNull(guest.read());assertNull(disk.get("account"));
        disk.put("guest","NaN\n106\nbroken");assertNull(guest.read());
    }
}
