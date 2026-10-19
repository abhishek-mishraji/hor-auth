package com.handsoff.hor.auth.stores;

import com.handsoff.hor.auth.client.DomainException;
import com.handsoff.hor.auth.common.ConstraintErrors;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class StoreRules {
    private static final Pattern US_POSTAL_CODE = Pattern.compile("^[0-9]{5}(-[0-9]{4})?$");

    public String uppercase(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    public void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new DomainException(ConstraintErrors.invalidFieldCode("TIMEZONE"), "Timezone is invalid.");
        }
    }

    public void validateS3Folder(String folder, String storeCode) {
        if (folder == null || folder.startsWith("/") || !folder.endsWith("/") || !folder.contains(storeCode)) {
            throw new DomainException(ConstraintErrors.invalidFieldCode("S3_FOLDER"), "S3 folder is invalid.");
        }
    }

    public void validatePostalCode(String countryCode, String postalCode) {
        if ("US".equals(countryCode) && (postalCode == null || !US_POSTAL_CODE.matcher(postalCode).matches())) {
            throw new DomainException(ConstraintErrors.invalidFieldCode("POSTAL_CODE"),
                    "US postal code must use 5 digits or 5+4 digits.");
        }
    }
}
