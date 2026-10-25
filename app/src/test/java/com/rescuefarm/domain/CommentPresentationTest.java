package com.rescuefarm.domain;
import com.rescuefarm.domain.model.Comment;
import org.junit.Test;
import java.util.Arrays;
import java.util.Date;
import static org.junit.Assert.*;
public class CommentPresentationTest {
    @Test public void anonymousAlwaysRedactsNameAndAvatarButKeepsCommentPhoto(){
        Comment c=Comment.create("c","campaign","uid","Nội dung",new Date());
        c.setPresentation("PRIVATE","https://example.test/avatar",true,Arrays.asList("https://example.test/photo"));
        assertEquals("Người dùng ẩn danh",c.getSenderName());assertEquals("",c.getSenderAvatarUrl());assertEquals(1,c.getImageUrls().size());
    }
    @Test public void legacyCommentHasSafeFallbackAndDeletingClearsPhotos(){
        Comment c=Comment.create("c","campaign","uid","Nội dung",new Date());assertEquals("Người dùng",c.getSenderName());
        c.setPresentation("Tên","",false,Arrays.asList("https://example.test/photo"));c.delete();assertTrue(c.getImageUrls().isEmpty());assertEquals("",c.getContent());
    }
}
