package com.rescuefarm.service;

import com.rescuefarm.service.address.VietnamAddressCatalog;
import com.rescuefarm.service.address.VietnamAddressCatalog.Unit;
import org.junit.Test;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public class VietnamAddressCatalogTest {
    private VietnamAddressCatalog bundled() throws IOException {
        try (java.io.Reader reader = Files.newBufferedReader(
                Paths.get("src/main/assets/address/dvhcvn.tsv"), StandardCharsets.UTF_8)) {
            return VietnamAddressCatalog.read(reader);
        }
    }

    @Test public void fullSnapshotHasUniqueIdsAndCorrectParentHierarchy() throws Exception {
        VietnamAddressCatalog catalog = bundled();
        assertEquals(63, catalog.provinces().size());
        int districts = 0, wards = 0;
        Set<String> ids = new HashSet<>();
        for (Unit province : catalog.provinces()) {
            assertTrue(ids.add(province.id));
            assertEquals(2, province.id.length());
            for (Unit district : province.children()) {
                districts++;
                assertTrue(ids.add(district.id));
                assertEquals(3, district.id.length());
                for (Unit ward : district.children()) {
                    wards++;
                    assertTrue(ids.add(ward.id));
                    assertEquals(5, ward.id.length());
                    assertTrue(ward.children().isEmpty());
                }
            }
        }
        assertEquals(696, districts); assertEquals(10047, wards);
    }

    @Test public void savedNamesMatchWithOrWithoutAccentsAndPrefixes() throws Exception {
        VietnamAddressCatalog catalog = bundled();
        Unit hanoi = VietnamAddressCatalog.find(catalog.provinces(), "  Ha Noi ");
        assertEquals("01", hanoi.id);
        Unit badinh = VietnamAddressCatalog.find(hanoi.children(), "Ba Đình");
        assertEquals("001", badinh.id);
        assertEquals("00001", VietnamAddressCatalog.find(badinh.children(), "phuc xa").id);
        assertEquals("Thành phố Hà Nội", hanoi.name);
        assertNull(VietnamAddressCatalog.find(catalog.provinces(), "Địa chỉ không có"));
    }

    @Test public void districtsAndWardsCannotBeTakenFromAnotherParent() throws Exception {
        VietnamAddressCatalog catalog = bundled();
        Unit hanoi = VietnamAddressCatalog.find(catalog.provinces(), "Hà Nội");
        Unit badinh = VietnamAddressCatalog.find(hanoi.children(), "Quận Ba Đình");
        assertNull(VietnamAddressCatalog.find(hanoi.children(), "Quận Bình Thạnh"));
        assertNull(VietnamAddressCatalog.find(badinh.children(), "Phường Hàng Bạc"));
        Unit hoankiem = VietnamAddressCatalog.find(hanoi.children(), "Quận Hoàn Kiếm");
        assertNotNull(VietnamAddressCatalog.find(hoankiem.children(), "Phường Hàng Bạc"));
    }

    @Test public void ambiguousShortNameRequiresExplicitAdministrativeType() throws Exception {
        VietnamAddressCatalog catalog = VietnamAddressCatalog.read(new StringReader(
                "01\t\tTỉnh Test\n001\t01\tHuyện Test\n00001\t001\tXã An Phú\n00002\t001\tThị trấn An Phú\n"));
        List<Unit> wards = catalog.provinces().get(0).children().get(0).children();
        assertNull(VietnamAddressCatalog.find(wards, "An Phú"));
        assertEquals("00002", VietnamAddressCatalog.find(wards, "Thị trấn An Phú").id);
    }

    @Test(expected = IOException.class) public void missingParentIsRejected() throws Exception {
        VietnamAddressCatalog.read(new StringReader("00001\t001\tPhường Test\n"));
    }
    @Test(expected = IOException.class) public void duplicateIdIsRejected() throws Exception {
        VietnamAddressCatalog.read(new StringReader("01\t\tTỉnh A\n01\t\tTỉnh B\n"));
    }
}
