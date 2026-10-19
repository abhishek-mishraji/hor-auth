package com.handsoff.hor.auth.password;

public interface PasswordHasher {
    String hash(String password);

    boolean matches(String password, String encodedPassword);
}
