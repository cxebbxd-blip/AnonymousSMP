package dev.cxebby.anonymous;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class SkinSignatureTest {
    @Test void steveHasAValidMojangSignature() throws Exception {
        assertTrue(valid("steve"), "The exact transmitted Steve value must pass Mojang's signature check");
    }

    @Test void creatorHeadHasAValidMojangSignature() throws Exception {
        assertTrue(valid("author"));
    }

    private boolean valid(String name) throws Exception {
        Properties skins = new Properties();
        try (var input = getClass().getResourceAsStream("/skins.properties")) { skins.load(input); }
        String keys;
        try (var input = getClass().getResourceAsStream("/mojang-profile-keys.txt")) {
            keys = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var decoder = Base64.getDecoder();
        byte[] value = skins.getProperty(name + ".value").getBytes(StandardCharsets.UTF_8);
        byte[] signature = decoder.decode(skins.getProperty(name + ".signature"));
        for (String encodedKey : keys.split("\\R")) {
            if (encodedKey.isBlank()) continue;
            var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoder.decode(encodedKey)));
            Signature verifier = Signature.getInstance("SHA1withRSA");
            verifier.initVerify(key);
            verifier.update(value);
            if (verifier.verify(signature)) return true;
        }
        return false;
    }
}
