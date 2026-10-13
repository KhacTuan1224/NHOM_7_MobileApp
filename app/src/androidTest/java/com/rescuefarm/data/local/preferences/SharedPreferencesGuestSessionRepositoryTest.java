package com.rescuefarm.data.local.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class SharedPreferencesGuestSessionRepositoryTest {

    private Context context;
    private SharedPreferencesGuestSessionRepository repository;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // Xóa dữ liệu SharedPreferences trước mỗi test case để đảm bảo môi trường sạch
        context.getSharedPreferences("rescuefarm_guest_session", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        repository = new SharedPreferencesGuestSessionRepository(context);
    }

    @Test
    public void getOrCreateGuestId_firstCall_generatesValidGuestId() {
        String guestId = repository.getOrCreateGuestId();

        assertNotNull("Guest ID không được null", guestId);
        assertTrue("Guest ID phải bắt đầu bằng tiền tố 'guest_'", guestId.startsWith("guest_"));

        // Phần UUID có độ dài chuẩn 36 ký tự (ví dụ: 550e8400-e29b-41d4-a716-446655440000)
        String uuidPart = guestId.substring("guest_".length());
        assertEquals("Độ dài phần UUID phải là 36 ký tự", 36, uuidPart.length());
    }

    @Test
    public void getOrCreateGuestId_subsequentCalls_returnsSameGuestId() {
        String firstGuestId = repository.getOrCreateGuestId();
        String secondGuestId = repository.getOrCreateGuestId();
        String thirdGuestId = repository.getOrCreateGuestId();

        assertEquals("Lần gọi thứ 2 phải trả về cùng guestId với lần 1", firstGuestId, secondGuestId);
        assertEquals("Lần gọi thứ 3 phải trả về cùng guestId với lần 1", firstGuestId, thirdGuestId);
    }

    @Test
    public void getOrCreateGuestId_concurrentCalls_returnsIdenticalGuestId() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        String[] results = new String[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                results[index] = repository.getOrCreateGuestId();
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        String expectedId = results[0];
        assertNotNull("ID của luồng đầu tiên không được null", expectedId);

        for (int i = 1; i < threadCount; i++) {
            assertEquals("Tất cả các luồng đồng thời phải nhận về cùng 1 guestId", expectedId, results[i]);
        }
    }

    @Test
    public void markOnboardingCompleted_persistsState() {
        assertFalse("Ban đầu chưa xem Onboarding thì phải trả về false", repository.hasCompletedOnboarding());

        repository.markOnboardingCompleted();

        assertTrue("Sau khi markOnboardingCompleted() thì phải trả về true", repository.hasCompletedOnboarding());
    }
}
