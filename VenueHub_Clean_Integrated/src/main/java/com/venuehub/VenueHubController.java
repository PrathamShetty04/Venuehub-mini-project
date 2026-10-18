package com.venuehub;

import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.Callable;

@RestController
@RequestMapping("/api")
public class VenueHubController {
    private final VenueHubService service;

    public VenueHubController(VenueHubService service) {
        this.service = service;
    }

    private String token(String header) {
        if (header == null || !header.startsWith("Bearer ") || header.substring(7).isBlank()) {
            throw new IllegalArgumentException("Please log in to continue.");
        }
        return header.substring(7).trim();
    }

    @PostMapping("/auth/register")
    public Object register(@RequestBody Map<String, Object> payload) {
        return wrap(() -> service.register(payload));
    }

    @PostMapping("/auth/password-reset/request")
    public Object requestPasswordReset(@RequestBody Map<String, Object> payload) {
        return wrap(() -> service.requestPasswordReset(str(payload.get("email"))));
    }

    @PostMapping("/auth/password-reset/confirm")
    public Object confirmPasswordReset(@RequestBody Map<String, Object> payload) {
        return wrap(() -> service.resetPassword(str(payload.get("token")), str(payload.get("password"))));
    }

    @PostMapping("/auth/login")
    public Object login(@RequestBody Map<String, Object> payload) {
        return wrap(() -> service.login(str(payload.get("email")), str(payload.get("password"))));
    }

    @PostMapping("/auth/logout")
    public Object logout(@RequestHeader(value = "Authorization", required = false) String header) {
        if (header != null && header.startsWith("Bearer ")) {
            service.logout(header.substring(7).trim());
        }
        return Api.ok(true);
    }

    @GetMapping("/venues")
    public Object venues() {
        return Api.ok(service.venues());
    }

    @GetMapping("/venues/{id}")
    public Object venue(@PathVariable int id) {
        return wrap(() -> service.venue(id));
    }

    @GetMapping("/me")
    public Object me(@RequestHeader(value = "Authorization", required = false) String header) {
        return wrap(() -> service.user(service.requireUser(token(header))));
    }

    @GetMapping("/customer/stats")
    public Object customerStats(@RequestHeader(value = "Authorization", required = false) String header) {
        return wrap(() -> service.customerStats(service.requireRole(token(header), "customer")));
    }

    @GetMapping("/customer/bookings")
    public Object customerBookings(@RequestHeader(value = "Authorization", required = false) String header) {
        return wrap(() -> service.customerBookings(service.requireRole(token(header), "customer")));
    }

    @PostMapping("/bookings")
    public Object createBooking(@RequestHeader(value = "Authorization", required = false) String header,
                                @RequestBody Map<String, Object> payload) {
        return wrap(() -> service.createBooking(service.requireRole(token(header), "customer"), payload));
    }

    @PatchMapping("/customer/bookings/{id}/cancel")
    public Object cancelBooking(@RequestHeader(value = "Authorization", required = false) String header,
                                @PathVariable int id) {
        return wrap(() -> {
            service.cancelCustomerBooking(service.requireRole(token(header), "customer"), id);
            return true;
        });
    }

    @GetMapping("/venues/{id}/reviews")
    public Object venueReviews(@PathVariable int id) {
        return wrap(() -> service.venueReviews(id));
    }

    @PostMapping("/customer/reviews")
    public Object createReview(@RequestHeader(value = "Authorization", required = false) String header,
                               @RequestBody Map<String, Object> payload) {
        return wrap(() -> {
            service.createReview(service.requireRole(token(header), "customer"), intValue(payload.get("bookingId")), intValue(payload.get("rating")), str(payload.get("reviewText")));
            return true;
        });
    }

    @GetMapping("/owner/stats")
    public Object ownerStats(@RequestHeader(value = "Authorization", required = false) String header) {
        return wrap(() -> service.ownerStats(service.requireRole(token(header), "owner")));
    }

    @GetMapping("/owner/venues")
    public Object ownerVenues(@RequestHeader(value = "Authorization", required = false) String header) {
        return wrap(() -> service.ownerVenues(service.requireRole(token(header), "owner")));
    }

    @PostMapping("/owner/venues")
    public Object saveVenue(@RequestHeader(value = "Authorization", required = false) String header,
                            @RequestBody Map<String, Object> payload) {
        return wrap(() -> {
            int ownerId = service.requireRole(token(header), "owner");
            Integer id = payload.get("id") == null || str(payload.get("id")).isBlank()
                    ? null : intValue(payload.get("id"));
            return service.saveVenue(ownerId, payload, id);
        });
    }

    @DeleteMapping("/owner/venues/{id}")
    public Object deleteVenue(@RequestHeader(value = "Authorization", required = false) String header,
                              @PathVariable int id) {
        return wrap(() -> {
            service.deleteVenue(service.requireRole(token(header), "owner"), id);
            return true;
        });
    }

    @GetMapping("/owner/bookings")
    public Object ownerBookings(@RequestHeader(value = "Authorization", required = false) String header) {
        return wrap(() -> service.ownerBookings(service.requireRole(token(header), "owner")));
    }

    @PatchMapping("/owner/bookings/{id}")
    public Object updateBooking(@RequestHeader(value = "Authorization", required = false) String header,
                                @PathVariable int id,
                                @RequestBody Map<String, Object> payload) {
        return wrap(() -> {
            service.updateBooking(service.requireRole(token(header), "owner"), id, str(payload.get("status")));
            return true;
        });
    }

    private Object wrap(Callable<Object> action) {
        try {
            return Api.ok(action.call());
        } catch (Exception ex) {
            return Api.error(ex.getMessage());
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intValue(Object value) {
        try {
            return new java.math.BigDecimal(str(value)).intValueExact();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid numeric value: " + value);
        }
    }
}
