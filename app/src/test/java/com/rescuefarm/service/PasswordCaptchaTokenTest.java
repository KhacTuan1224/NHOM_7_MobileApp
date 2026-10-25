package com.rescuefarm.service;

import com.rescuefarm.service.auth.PasswordCaptchaToken;
import org.junit.Test;
import static org.junit.Assert.*;

public class PasswordCaptchaTokenTest {
    @Test public void responseIsSingleUseBoundToEmailAndNotValidAtExpiry(){
        PasswordCaptchaToken t=new PasswordCaptchaToken();t.accept("synthetic-response"," demo@example.test ",100);
        assertTrue(t.isReady("demo@example.test",101));assertFalse(t.isReady("other@example.test",101));
        assertFalse(t.isReady("demo@example.test",99));
        assertFalse(t.isReady("demo@example.test",100+PasswordCaptchaToken.MAX_AGE_MS));
        assertEquals("synthetic-response",t.consume("demo@example.test",102));
        assertEquals("",t.consume("demo@example.test",103));
    }
    @Test public void emptyMalformedAndClearedResponsesCannotProceed(){
        PasswordCaptchaToken t=new PasswordCaptchaToken();t.accept("short","email",1);assertFalse(t.isReady("email",2));
        t.accept("synthetic-response","email",1);t.clear();assertFalse(t.isReady("email",2));
        t.accept(null,"email",1);assertFalse(t.isReady("email",2));
    }
}
