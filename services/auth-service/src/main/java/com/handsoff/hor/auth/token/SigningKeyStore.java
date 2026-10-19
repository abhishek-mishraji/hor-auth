package com.handsoff.hor.auth.token;

import com.handsoff.hor.auth.config.AuthProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.KeyFactory;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class SigningKeyStore {
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;
    private final RSAKey publicJwk;

    public SigningKeyStore(AuthProperties properties) {
        try {
            Path path = properties.signingKeyPath();
            Files.createDirectories(path.toAbsolutePath().getParent());
            if (Files.notExists(path)) {
                createKey(path);
            }
            privateKey = readPrivateKey(path);
            publicKey = derivePublicKey(privateKey);
            publicJwk = new RSAKey.Builder(publicKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
            keyId = publicJwk.computeThumbprint().toString();
        } catch (IOException | GeneralSecurityException | com.nimbusds.jose.JOSEException exception) {
            throw new IllegalStateException("Unable to load or create the auth signing key.", exception);
        }
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public String keyId() {
        return keyId;
    }

    public RSAKey publicJwk() {
        return publicJwk;
    }

    private void createKey(Path path) throws GeneralSecurityException, IOException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String encoded = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(pair.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
        Files.createFile(path);
        setOwnerOnlyPermissions(path);
        Files.writeString(path, pem, StandardCharsets.US_ASCII);
    }

    private RSAPrivateKey readPrivateKey(Path path) throws IOException, GeneralSecurityException {
        String pem = Files.readString(path, StandardCharsets.US_ASCII)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] encoded = Base64.getDecoder().decode(pem);
        PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        return (RSAPrivateKey) privateKey;
    }

    private RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) throws GeneralSecurityException {
        if (!(privateKey instanceof RSAPrivateCrtKey crtKey)) {
            throw new GeneralSecurityException("RSA private key does not expose public-key parameters.");
        }
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
    }

    private void setOwnerOnlyPermissions(Path path) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) {
            posix.setPermissions(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
        AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (aclView != null) {
            AclEntry ownerEntry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(aclView.getOwner())
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .build();
            aclView.setAcl(List.of(ownerEntry));
        }
    }
}
