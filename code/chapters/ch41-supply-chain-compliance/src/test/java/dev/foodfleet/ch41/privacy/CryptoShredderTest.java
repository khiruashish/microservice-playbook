package dev.foodfleet.ch41.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.crypto.AEADBadTagException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class CryptoShredderTest {

    static SecretKey kek() throws Exception {
        KeyGenerator g = KeyGenerator.getInstance("AES");
        g.init(256);
        return g.generateKey();
    }

    @Test
    void roundTripsAndErasureMakesEveryCopyUnreadable() throws Exception {
        InMemoryKeyStore keys = new InMemoryKeyStore();
        CryptoShredder s = new CryptoShredder(kek(), keys, 100);
        String phone = s.encrypt("cust-42", "phone", "+91 98450 12345");
        String backupCopy = phone;                                          // the same bytes in a backup, a topic, a replica
        assertThat(phone.startsWith("ff1:")).isTrue();
        assertThat(phone.contains("98450")).isFalse();
        assertThat(s.decrypt("cust-42", "phone", phone)).contains("+91 98450 12345");
        assertThat(s.erase("cust-42")).isTrue();
        assertThat(s.decrypt("cust-42", "phone", backupCopy)).isEmpty();
        assertThat(keys.size()).isZero();
    }

    @Test
    void aValueMovedToAnotherCustomerOrFieldDoesNotDecrypt() throws Exception {
        CryptoShredder s = new CryptoShredder(kek(), new InMemoryKeyStore(), 100);
        String address = s.encrypt("cust-42", "address", "No. 12/3, 1st Cross, HSR Layout");
        s.encrypt("cust-7", "address", "x");
        assertThatThrownBy(() -> s.decrypt("cust-42", "phone", address)).isInstanceOf(AEADBadTagException.class);
        assertThatThrownBy(() -> s.decrypt("cust-7", "address", address)).isInstanceOf(AEADBadTagException.class);
    }

    @Test
    void keysSurviveARestartButOnlyWithTheKek() throws Exception {
        InMemoryKeyStore keys = new InMemoryKeyStore();
        SecretKey kek = kek();
        String name = new CryptoShredder(kek, keys, 100).encrypt("cust-42", "name", "Asha Rao");
        assertThat(new CryptoShredder(kek, keys, 100).decrypt("cust-42", "name", name)).contains("Asha Rao");
        assertThatThrownBy(() -> new CryptoShredder(kek(), keys, 100).decrypt("cust-42", "name", name))
                .isInstanceOf(AEADBadTagException.class);                  // stolen key table without the KEK: useless
    }
}
