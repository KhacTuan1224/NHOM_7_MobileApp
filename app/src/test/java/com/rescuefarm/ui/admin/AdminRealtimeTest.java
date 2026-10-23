package com.rescuefarm.ui.admin;

import static org.junit.Assert.*;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.rescuefarm.data.repository.AdminRepository;
import com.rescuefarm.data.repository.AdminRepository.Section;
import com.rescuefarm.data.repository.admin.AdminListItem;
import org.junit.Rule;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class AdminRealtimeTest {
    @Rule public final InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Test public void realtimeKeepsSearchStatusAndLoadedPagesAndRemovesDeletedRows() {
        List<AdminListItem> data = new ArrayList<>();
        for (int i=1; i<=30; i++) data.add(row(String.format(java.util.Locale.ROOT, "%02d", i), "Rau " + i));
        List<String> filters = new ArrayList<>();
        AdminRepository repository = (AdminRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{AdminRepository.class}, (proxy, method, args) -> {
            assertEquals("loadSectionPage", method.getName());
            filters.add(args[2] + ":" + args[4]);
            String after = (String) args[3]; int limit = (Integer) args[1];
            List<AdminListItem> candidates = new ArrayList<>();
            for (AdminListItem item : data) if (item.getId().compareTo(after) > 0) candidates.add(item);
            List<AdminListItem> page = new ArrayList<>(candidates.subList(0, Math.min(limit, candidates.size())));
            ((AdminRepository.PageCallback) args[7]).onSuccess(page, page.isEmpty() ? "" : page.get(page.size()-1).getId(), candidates.size() > limit);
            return null;
        });
        AdminViewModel vm = new AdminViewModel(repository);
        vm.refreshSection(Section.PRODUCTS); vm.setSearchTerm("Rau"); vm.setStatusFilter("ACTIVE"); vm.loadNextPage();
        assertEquals(24, vm.getState().getValue().getItems().size());
        data.removeIf(item -> item.getId().equals("05")); data.add(0, row("00", "Rau mới"));
        data.set(6, row(data.get(6).getId(), "Rau đã sửa"));
        filters.clear(); vm.refreshSectionRealtime(Section.PRODUCTS);
        List<AdminListItem> visible = vm.getState().getValue().getItems();
        assertEquals(24, visible.size()); assertEquals("00", visible.get(0).getId());
        assertFalse(visible.stream().anyMatch(item -> item.getId().equals("05")));
        assertTrue(visible.stream().anyMatch(item -> item.getTitle().equals("Rau đã sửa")));
        assertTrue(filters.stream().allMatch(filter -> filter.equals("ACTIVE:Rau")));
        vm.loadNextPage();
        assertEquals(30, vm.getState().getValue().getItems().size()); assertFalse(vm.hasMore());
    }
    private AdminListItem row(String id, String name) { return new AdminListItem(id, name, "", "ACTIVE", true); }
}
