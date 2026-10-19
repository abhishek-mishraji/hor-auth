package com.handsoff.hor.auth.common;

import com.handsoff.hor.auth.client.DomainException;
import com.handsoff.hor.auth.client.ErrorCode;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatusCode;

public final class ConstraintErrors {
    private static final Map<String, String> INVALID_FIELDS = Map.ofEntries(
            Map.entry("ck_account_emails_email", "EMAIL"),
            Map.entry("ck_platform_admins_name", "FULL_NAME"),
            Map.entry("ck_platform_admins_phone", "PHONE_NUMBER"),
            Map.entry("ck_store_users_type", "USER_TYPE"),
            Map.entry("ck_store_users_name", "FULL_NAME"),
            Map.entry("ck_store_users_phone", "PHONE_NUMBER"),
            Map.entry("ck_store_users_address", "ADDRESS"),
            Map.entry("ck_stores_code", "STORE_CODE"),
            Map.entry("ck_stores_name", "STORE_NAME"),
            Map.entry("ck_stores_vendor", "POS_VENDOR"),
            Map.entry("ck_stores_s3", "S3_FOLDER"),
            Map.entry("ck_stores_state", "STATE_CODE"),
            Map.entry("ck_stores_country", "COUNTRY_CODE"),
            Map.entry("ck_stores_zip", "POSTAL_CODE"),
            Map.entry("ck_stores_phone", "CONTACT_NUMBER"),
            Map.entry("ck_stores_timezone", "TIMEZONE"),
            Map.entry("ck_stores_status", "STATUS"),
            Map.entry("ck_store_user_stores_role", "STORE_ROLE"));

    private ConstraintErrors() {
    }

    public static DomainException toDomainException(DataIntegrityViolationException exception) {
        String constraint = findConstraint(exception);
        if (constraint == null) {
            return new DomainException(AuthErrorCode.INTERNAL_ERROR, "An internal error occurred.");
        }
        ErrorCode code = switch (constraint) {
            case "account_emails_pkey", "uq_account_emails_email_kind", "uq_platform_admins_email", "uq_store_users_email" -> AuthErrorCode.EMAIL_TAKEN;
            case "uq_stores_store_code" -> AuthErrorCode.STORE_CODE_TAKEN;
            case "uq_stores_s3_folder" -> AuthErrorCode.S3_FOLDER_TAKEN;
            case "uq_store_user_stores_one_owner" -> AuthErrorCode.OWNER_EXISTS;
            default -> invalidField(constraint);
        };
        return new DomainException(code, "The requested value violates a data constraint.");
    }

    private static ErrorCode invalidField(String constraint) {
        String field = INVALID_FIELDS.get(constraint);
        return field == null ? AuthErrorCode.INTERNAL_ERROR : new InvalidFieldCode(field);
    }

    public static ErrorCode invalidFieldCode(String field) {
        return new InvalidFieldCode(field);
    }

    private static String findConstraint(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) {
                continue;
            }
            int marker = message.toLowerCase(java.util.Locale.ROOT).indexOf("constraint ");
            int openingQuote = marker < 0 ? -1 : message.indexOf('"', marker + 11);
            int closingQuote = openingQuote < 0 ? -1 : message.indexOf('"', openingQuote + 1);
            if (openingQuote >= 0 && closingQuote > openingQuote) {
                return message.substring(openingQuote + 1, closingQuote);
            }
        }
        return null;
    }

    private record InvalidFieldCode(String field) implements ErrorCode {
        @Override
        public String code() {
            return "INVALID_" + field;
        }

        @Override
        public HttpStatusCode status() {
            return HttpStatusCode.valueOf(422);
        }
    }
}
