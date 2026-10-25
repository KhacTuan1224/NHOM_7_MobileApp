package com.rescuefarm.service.location;

/** Private Home browsing choice, separate from the profile's saved address. */
public final class ScopedHomeLocation {
    public interface Storage { String get(String key); void put(String key,String value); }
    public static final class Selection {
        public final double latitude,longitude;
        public final String label;
        public Selection(double lat,double lng,String label) {
            if(!Double.isFinite(lat)||!Double.isFinite(lng)||Math.abs(lat)>90||Math.abs(lng)>180)
                throw new IllegalArgumentException("Invalid location");
            latitude=lat;longitude=lng;this.label=label==null?"":label;
        }
    }
    private final Storage storage;
    private String owner;
    public ScopedHomeLocation(Storage storage,String uid){this.storage=storage;changeOwner(uid);}
    public boolean changeOwner(String uid){
        String next=uid==null?"":uid;
        boolean changed=owner!=null&&!owner.equals(next);
        if(!next.equals(storage.get("owner"))) {
            storage.put("account",null);storage.put("owner",next);
        }
        owner=next;return changed;
    }
    public boolean clearAccount(String uid){
        if(uid==null||!uid.equals(owner))return false;
        storage.put("account",null);return true;
    }
    public Selection read(){
        String raw=storage.get(owner.isEmpty()?"guest":"account");if(raw==null)return null;
        try{String[] p=raw.split("\n",3);return new Selection(Double.parseDouble(p[0]),Double.parseDouble(p[1]),p[2]);}
        catch(RuntimeException invalid){return null;}
    }
    public void save(Selection value){storage.put(owner.isEmpty()?"guest":"account",
        value.latitude+"\n"+value.longitude+"\n"+value.label);}
}
