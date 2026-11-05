package com.example.library;

import com.example.library.util.Passwords;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordsTest {
    @Test void saltedPasswordsVerifyWithoutAcceptingWrongPassword() {
        String hash=Passwords.hash("阅读 Password 123");
        assertTrue(Passwords.matches("阅读 Password 123",hash));
        assertFalse(Passwords.matches("阅读 Password 124",hash));
        assertNotEquals(hash,Passwords.hash("阅读 Password 123"));
    }
    @Test void teacherBackupPasswordsRemainUsable() {
        assertTrue(Passwords.matches("legacy-pass","legacy-pass"));
        assertFalse(Passwords.matches("other","legacy-pass"));
        assertFalse(Passwords.matches("other",null));
    }
    @Test void malformedHashesFailClosed() {
        assertFalse(Passwords.matches("anything","pbkdf2$invalid"));
        assertFalse(Passwords.matches("anything","pbkdf2$2$bad-salt$bad-hash"));
    }
}
