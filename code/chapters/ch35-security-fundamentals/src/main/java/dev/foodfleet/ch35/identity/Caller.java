package dev.foodfleet.ch35.identity;

import java.util.Set;

/**
 * Who is calling, as established by authentication: an id and roles. Authorization decisions take a Caller,
 * never ids from the request body or path, which the caller controls.
 *
 * @param restaurantIds for restaurant owners, the restaurants they own (from the identity, not from the request)
 */
public record Caller(String id, Set<Role> roles, Set<Long> restaurantIds) {

    public Caller {
        roles = Set.copyOf(roles);
        restaurantIds = Set.copyOf(restaurantIds);
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }
}
