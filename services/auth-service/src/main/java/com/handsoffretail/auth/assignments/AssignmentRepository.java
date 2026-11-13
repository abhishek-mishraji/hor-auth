package com.handsoffretail.auth.assignments;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AssignmentRepository {
    boolean storeExists(long storeId);

    Optional<AssignmentUser> findUser(long userId);

    Optional<AssignmentRecord> find(long storeId, long userId);

    List<StoreMemberResponse> findMembers(long storeId);

    void insert(long storeId, AssignmentUser user, StoreRole role, long actorAdminId, Instant now);

    void changeRole(long storeId, long userId, StoreRole role, long actorAdminId, Instant now);

    boolean delete(long storeId, long userId);
}
