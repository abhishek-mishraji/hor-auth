package com.handsoff.hor.auth.admins;

import com.handsoff.hor.auth.common.PageResult;
import java.time.Instant;
import java.util.Optional;

public interface AdminRepository {
    PageResult<PlatformAdminView> findAll(String status, String query, int limit, int offset, Instant now);

    Optional<PlatformAdminView> findById(long id, Instant now);

    long create(String email, String fullName, String phoneNumber, String passwordHash, Instant now);

    void changeEmail(String currentEmail, String newEmail);

    void changeFullName(long id, String fullName, Instant now);

    void changePhoneNumber(long id, String phoneNumber, Instant now);

    void changeStatus(long id, String status, Instant now);

    void resetPassword(long id, String passwordHash, Instant now);

    void unlock(long id, Instant now);

    long countActive();

    void lockActiveRows();
}
