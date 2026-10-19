package com.handsoff.hor.auth.users;

import com.handsoff.hor.auth.common.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StoreUserRepository {
    PageResult<StoreUserView> findAll(String userType, String status, String query, int limit, int offset);

    Optional<StoreUserView> findById(long id);

    List<UserStoreView> findStores(long userId);

    long create(String userType, String email, String fullName, String phoneNumber, String address,
            String passwordHash, long actorAdminId, Instant now);

    void changeEmail(String currentEmail, String newEmail);

    void changeFullName(long id, String fullName, Instant now);

    void changePhoneNumber(long id, String phoneNumber, Instant now);

    void changeAddress(long id, String address, Instant now);

    void changeStatus(long id, String status, Instant now);

    void resetPassword(long id, String passwordHash, Instant now);

    void unlock(long id, Instant now);
}
