package com.handsoffretail.auth.bootstrap;

import java.time.Instant;
import java.util.Optional;

public interface BootstrapRepository {
    boolean hasAdmins();

    Optional<Long> firstAdminId();

    boolean hasStores();

    long createAdmin(String fullName, String email, String passwordHash, Instant now);

    long createStore(BootstrapStore store, Instant now);
}
