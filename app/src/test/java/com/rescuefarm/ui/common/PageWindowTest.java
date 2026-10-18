package com.rescuefarm.ui.common;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class PageWindowTest {
    private List<Integer> items(int size) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < size; i++) result.add(i);
        return result;
    }
    @Test public void homeStartsWithSixAndOnlyExpandsOnRequest() {
        PageWindow page = new PageWindow(6);
        assertEquals(items(6), page.take(items(30)));
        assertTrue(page.hasMore(30));
        page.advance();
        assertEquals(items(12), page.take(items(30)));
    }
    @Test public void partialLastPageContainsEveryItemExactlyOnce() {
        PageWindow page = new PageWindow(12);
        List<Integer> source = items(25);
        assertEquals(12, page.take(source).size());
        page.advance(); assertEquals(24, page.take(source).size());
        page.advance(); assertEquals(source, page.take(source));
        assertFalse(page.hasMore(source.size()));
    }
    @Test public void emptyOrExactlyFullPageDoesNotShowLocalMore() {
        PageWindow page = new PageWindow(12);
        assertTrue(page.take(null).isEmpty());
        assertTrue(page.take(Collections.emptyList()).isEmpty());
        assertFalse(page.hasMore(0)); assertFalse(page.hasMore(12));
        assertTrue(page.hasMore(13));
    }
    @Test public void realtimeUpdatesDoNotResetExpandedWindow() {
        PageWindow page = new PageWindow(6); page.advance();
        assertEquals(12, page.take(items(18)).size());
        assertEquals(12, page.take(items(24)).size());
        assertEquals(items(4), page.take(items(4)));
        assertFalse(page.hasMore(4));
    }
    @Test public void changingFilterCanResetToFirstPageWithoutLosingSource() {
        PageWindow page = new PageWindow(12); page.advance();
        List<Integer> source = items(30);
        page.reset(); assertEquals(items(12), page.take(source));
        assertEquals(30, source.size());
        List<Integer> copy = page.take(source); copy.clear();
        assertEquals(30, source.size());
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsInvalidPageSize() { new PageWindow(0); }
}
