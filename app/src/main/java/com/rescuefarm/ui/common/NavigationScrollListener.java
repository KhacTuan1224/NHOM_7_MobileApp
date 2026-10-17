package com.rescuefarm.ui.common;

/** Receives vertical content scrolling so the host can animate shared navigation chrome. */
public interface NavigationScrollListener {
    void onContentScrolled(int deltaY, boolean atTop);
}
