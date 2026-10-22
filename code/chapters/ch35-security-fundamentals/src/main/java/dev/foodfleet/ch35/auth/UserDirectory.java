package dev.foodfleet.ch35.auth;

import dev.foodfleet.ch35.identity.Caller;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Accounts with password hashes, in memory for the examples (chapter 37 delegates this to an identity provider). */
public class UserDirectory {

    public record Account(String username, String passwordHash, Caller caller) {
    }

    private final ConcurrentHashMap<String, Account> accounts = new ConcurrentHashMap<>();

    public void add(Account account) {
        accounts.put(account.username(), account);
    }

    public Optional<Account> find(String username) {
        return Optional.ofNullable(accounts.get(username));
    }

    public void replaceHash(String username, String hash) {
        accounts.computeIfPresent(username, (u, a) -> new Account(u, hash, a.caller()));
    }
}
