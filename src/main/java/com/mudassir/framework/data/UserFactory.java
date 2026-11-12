package com.mudassir.framework.data;

import java.util.UUID;

/** Builds unique synthetic users so tests never collide when running in parallel. */
public final class UserFactory {

    private UserFactory() {
    }

    public static TestUser newUser() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return new TestUser(
                "QA Demo " + id,
                "qa.demo." + id + "@example.com",
                "Pass@" + id,
                "Mr",
                "10",
                "5",
                "1993",
                "QA",
                "Demo",
                "Demo Corp",
                "12 Test Street",
                "Suite " + id,
                "India",
                "Karnataka",
                "Bengaluru",
                "560001",
                "0000000000");
    }
}
