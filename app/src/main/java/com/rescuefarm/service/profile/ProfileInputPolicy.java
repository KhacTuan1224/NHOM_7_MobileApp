package com.rescuefarm.service.profile;
public final class ProfileInputPolicy {
    private ProfileInputPolicy() { }
    private static String clean(String v) {return v==null?"":v.trim();}
    public static String profile(String name,String phone) {
        if(clean(name).length()<2||clean(name).length()>100)return "Họ tên phải có từ 2 đến 100 ký tự.";
        if(!clean(phone).matches("[0-9]{9,11}"))return "Số điện thoại phải có 9 đến 11 chữ số.";
        return null;
    }
    public static String business(String name,String tax) {
        if(clean(name).length()<2||clean(name).length()>160)return "Tên doanh nghiệp phải có từ 2 đến 160 ký tự.";
        if(clean(tax).isEmpty()||clean(tax).length()>40)return "Mã số thuế phải có từ 1 đến 40 ký tự.";
        return null;
    }
}
