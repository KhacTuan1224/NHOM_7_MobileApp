package com.rescuefarm.service.campaign;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;
import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.domain.enums.*;
public final class CampaignTimeFormatter {
    private CampaignTimeFormatter() { }
    public static boolean withinSchedule(RescueCampaign c, Date now) {
        return c != null && c.getStatus() == CampaignStatus.ACTIVE && c.getEndDate() != null && now.before(c.getEndDate())
                && (c.getStartDate() == null || !now.before(c.getStartDate()));
    }
    public static boolean open(RescueCampaign c, Date now) {return withinSchedule(c,now)&&c.calculateRemainingQuantity()>.000001;}
    public static boolean canUpdateLocation(RescueCampaign c, Date now) {
        return c!=null&&c.getStatus()==CampaignStatus.ACTIVE&&c.getRescueMode()==RescueMode.MOBILE_POINT&&c.getEndDate()!=null&&now.before(c.getEndDate());
    }
    public static String locationStatus(RescueCampaign c, Date now) {
        if(!withinSchedule(c,now))return "Điểm chưa hoạt động hoặc chiến dịch đã kết thúc";
        if(!c.isLocationSharingEnabled())return "Đang dừng chia sẻ vị trí";
        return c.isLocationFresh(now)?"Vị trí còn hiệu lực":"Vị trí đã cũ hoặc chưa cập nhật";
    }
    public static boolean canPurchase(RescueCampaign c, Date now) {
        return open(c, now) && "VERIFIED".equals(c.getUnitStatus())
                && (c.getRescueMode() != RescueMode.MOBILE_POINT || c.isLocationFresh(now));
    }
    public static String remaining(RescueCampaign c, Date now) {
        if(c == null || c.getEndDate() == null) return "Chưa có thời gian kết thúc";
        if(c.getStatus() == CampaignStatus.COMPLETED) return "Đã hoàn thành chiến dịch";
        if(c.getStatus() == CampaignStatus.STOPPED) return "Chiến dịch đã dừng";
        if(!now.before(c.getEndDate())) return "Chiến dịch đã kết thúc";
        boolean future=c.getStartDate()!=null && now.before(c.getStartDate());
        long seconds=((future?c.getStartDate():c.getEndDate()).getTime()-now.getTime()+999)/1000;
        long days=seconds/86400, hours=seconds%86400/3600, minutes=seconds%3600/60;
        String duration=days>0 ? days+" ngày "+hours+" giờ" : hours>0 ? hours+" giờ "+minutes+" phút" : minutes>0 ? minutes+" phút" : seconds+" giây";
        return future ? "Bắt đầu sau "+duration : "Còn "+duration;
    }
    public static String locationUpdated(Date at, Date now) {
        if(at==null) return "Chưa cập nhật vị trí";
        if(now.before(at)) return "Thời gian vị trí không hợp lệ";
        long minutes=(now.getTime()-at.getTime())/60000;
        return "Cập nhật lúc "+new SimpleDateFormat("dd/MM HH:mm",Locale.forLanguageTag("vi-VN")).format(at)
                +" · "+(minutes==0?"vừa xong":minutes+" phút trước");
    }
}
