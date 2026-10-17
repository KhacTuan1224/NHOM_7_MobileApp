package com.rescuefarm.ui.profile;
import org.junit.Test;import static org.junit.Assert.*;
public class BusinessProfilePolicyTest {
    @Test public void individualProfileDoesNotRequireBusinessFields() {assertNull(ProfileValidator.validateProfile("Customer","0912345678"));}
    @Test public void businessFieldsKeepTheRegistrationContract() {
        assertNull(ProfileValidator.validateBusiness("RescueFarm Company","0123456789"));
        assertNotNull(ProfileValidator.validateBusiness(" ","0123456789"));
        assertNotNull(ProfileValidator.validateBusiness("Company"," "));
        assertNotNull(ProfileValidator.validateBusiness(new String(new char[161]).replace('\0','x'),"tax"));
        assertNotNull(ProfileValidator.validateBusiness("Company",new String(new char[41]).replace('\0','1')));
    }
}
