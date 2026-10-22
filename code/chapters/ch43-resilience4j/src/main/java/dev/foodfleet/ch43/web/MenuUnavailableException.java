package dev.foodfleet.ch43.web;

/** No fresh menu and no cached one: the caller shows "menu unavailable, try again" (HTTP 503 at the edge). */
public class MenuUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MenuUnavailableException(String restaurantId, Throwable cause) {
        super("menu of restaurant " + restaurantId + " unavailable", cause);
    }
}
