package com.rescuefarm.ui.rescue;

import org.junit.Test;
import static org.junit.Assert.*;

public class RescueMarketUiTest {
    @Test public void countdownStopsAtZeroAndKeepsTheLastPartialSecond() {
        assertEquals("Đã kết thúc", RescueMarketUi.countdown(999, 1000));
        assertEquals("Đã kết thúc", RescueMarketUi.countdown(1000, 1000));
        assertEquals("00 : 00 : 01", RescueMarketUi.countdown(1001, 1000));
        assertEquals("02 : 00 : 00", RescueMarketUi.countdown(7201000, 1000));
        assertEquals("00 : 01 : 00", RescueMarketUi.countdown(61000, 1000));
    }
    @Test public void discountUsesActualOfferPricesAndHandlesInvalidOriginalValues() {
        assertEquals(80, RescueMarketUi.discount(100000, 20000));
        assertEquals(0, RescueMarketUi.discount(0, 20000));
        assertEquals(0, RescueMarketUi.discount(20000, 30000));
        assertEquals(100, RescueMarketUi.discount(20000, 0));
    }
}
