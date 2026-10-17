package com.rescuefarm.ui.engagement;
import org.junit.Test;
import static org.junit.Assert.*;
import com.rescuefarm.domain.model.Review;
import com.rescuefarm.domain.enums.ReviewStatus;
import java.util.*;
public class ReviewMediaRendererTest {
    private Review review(List<String> urls, ReviewStatus status) {
        return Review.restore("r","customer","product","order","item",5,"Review",urls,status,new Date());
    }
    @Test public void publishedImagesKeepUploadOrderForAnyViewer() {
        List<String> urls=Arrays.asList("https://example.com/1","https://example.com/2","https://example.com/3");
        assertEquals(urls,ReviewMediaRenderer.publicImages(review(urls,ReviewStatus.PUBLISHED)));
    }
    @Test public void hiddenAndDeletedMediaNeverAppearPublicly() {
        for(ReviewStatus status:Arrays.asList(ReviewStatus.PENDING,ReviewStatus.HIDDEN,ReviewStatus.DELETED))
            assertTrue(ReviewMediaRenderer.publicImages(review(Collections.singletonList("https://example.com/1"),status)).isEmpty());
    }
    @Test public void fiveImagesKeepExactlyFiveGalleryPositions() {
        List<String> urls=Arrays.asList("https://e.com/1","https://e.com/2","https://e.com/3","https://e.com/4","https://e.com/5");
        assertEquals(urls,ReviewMediaRenderer.publicImages(review(urls,ReviewStatus.PUBLISHED)));
    }
    @Test public void removedAndInvalidImagesAreNotRendered() {
        Review initial=review(Arrays.asList("https://example.com/1","https://example.com/2"),ReviewStatus.PUBLISHED);
        assertEquals(2,ReviewMediaRenderer.publicImages(initial).size());
        Review edited=review(Arrays.asList("https://example.com/2","file:///private"),ReviewStatus.PUBLISHED);
        assertEquals(Collections.singletonList("https://example.com/2"),ReviewMediaRenderer.publicImages(edited));
    }
}
