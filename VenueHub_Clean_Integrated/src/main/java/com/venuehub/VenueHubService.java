package com.venuehub;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VenueHubService {
    private final JdbcTemplate db;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    @Value("${spring.mail.host:}") private String mailHost;
    @Value("${spring.mail.username:}") private String mailUsername;
    @Value("${venuehub.mail.from:}") private String mailFrom;
    @Value("${venuehub.app.base-url:http://localhost:8080}") private String appBaseUrl;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();


    public VenueHubService(JdbcTemplate db, ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.db = db;
        this.mailSenderProvider = mailSenderProvider;
    }

    public Map<String, Object> requestPasswordReset(String emailInput) {
        String email = str(emailInput).toLowerCase(Locale.ROOT).trim();
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        boolean mailConfigured = StringUtils.hasText(mailHost) && StringUtils.hasText(mailFrom) && mailSenderProvider.getIfAvailable() != null;
        List<Map<String, Object>> users = db.queryForList("SELECT id FROM users WHERE email = ?", email);
        // Do not disclose whether an email address is registered.
        if (!users.isEmpty()) {
            int userId = ((Number) users.get(0).get("id")).intValue();
            String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            String hash = sha256(token);
            db.update("UPDATE password_reset_tokens SET used=TRUE WHERE user_id=? AND used=FALSE", userId);
            db.update("INSERT INTO password_reset_tokens(user_id,token_hash,expires_at,used) VALUES(?,?,DATE_ADD(NOW(), INTERVAL 30 MINUTE),FALSE)", userId, hash);
            String resetLink = appBaseUrl.replaceAll("/+$", "") + "/reset-password.html?token=" + token;
            if (mailConfigured) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(mailFrom);
                message.setTo(email);
                message.setSubject("Reset your VenueHub password");
                message.setText("We received a request to reset your VenueHub password. Open this link within 30 minutes:\n\n" + resetLink + "\n\nIf you did not request this, you can ignore this email.");
                try { mailSenderProvider.getObject().send(message); }
                catch (Exception ex) {
                    db.update("UPDATE password_reset_tokens SET used=TRUE WHERE token_hash=?", hash);
                    throw new IllegalStateException("The reset email could not be sent. Check the SMTP settings and try again.");
                }
                return Map.of("message", "If an account exists for that email, a password reset link has been sent.");
            }
            // Local college-demo fallback: expose a one-time, 30-minute link instead of showing an SMTP configuration error.
            return Map.of("message", "Email delivery is not configured on this local demo. Use the secure one-time link below; it expires in 30 minutes.", "resetLink", resetLink);
        }
        return Map.of("message", "If an account exists for that email, a password reset link has been sent.");
    }

    @Transactional
    public Map<String, Object> resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Reset token is missing.");
        if (newPassword == null || newPassword.length() < 8) throw new IllegalArgumentException("Password must contain at least 8 characters.");
        String hash = sha256(token.trim());
        List<Map<String, Object>> matches = db.queryForList("SELECT user_id FROM password_reset_tokens WHERE token_hash=? AND used=FALSE AND expires_at>NOW()", hash);
        if (matches.isEmpty()) throw new IllegalArgumentException("This reset link is invalid or expired. Request a new one.");
        int userId = ((Number) matches.get(0).get("user_id")).intValue();
        db.update("UPDATE users SET password_hash=? WHERE id=?", passwordEncoder.encode(newPassword), userId);
        db.update("UPDATE password_reset_tokens SET used=TRUE WHERE user_id=?", userId);
        return Map.of("message", "Password reset successfully. You can now sign in with your new password.");
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    @Transactional
    public Map<String, Object> register(Map<String, Object> p) {
        String first = str(p.get("firstName"));
        String last = str(p.get("lastName"));
        String email = str(p.get("email")).toLowerCase(Locale.ROOT);
        String phone = str(p.get("phone"));
        String password = str(p.get("password"));
        String requestedRole = str(p.get("accountType")).toLowerCase(Locale.ROOT);
        String role = "owner".equals(requestedRole) ? "owner" : "customer";

        if (!first.matches("^[\\p{L}][\\p{L} '-]{1,79}$") || !last.matches("^[\\p{L}][\\p{L} '-]{1,79}$")) throw new IllegalArgumentException("Enter valid first and last names using letters, spaces, apostrophes or hyphens.");
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Enter a valid email address.");
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 10 || digits.length() > 15 || !phone.matches("[+0-9() .-]{10,20}")) throw new IllegalArgumentException("Enter a valid phone number containing 10 to 15 digits.");
        if (!"customer".equals(requestedRole) && !"owner".equals(requestedRole)) throw new IllegalArgumentException("Choose Customer or Venue Owner.");
        if (password.length() < 8 || password.length() > 72 || !password.matches("(?s)(?=.*[A-Za-z])(?=.*\\d).{8,72}")) throw new IllegalArgumentException("Password must be 8–72 characters and include at least one letter and one number.");
        Integer count = db.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        if (count != null && count > 0) throw new IllegalArgumentException("An account with this email already exists.");

        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            db.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO users(first_name,last_name,email,phone,password_hash,role) VALUES(?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, first);
                ps.setString(2, last);
                ps.setString(3, email);
                ps.setString(4, phone);
                ps.setString(5, passwordEncoder.encode(password));
                ps.setString(6, role);
                return ps;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key == null) throw new IllegalStateException("The account was created but its ID could not be read.");
            return createSession(key.intValue());
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }
    }

    public Map<String, Object> login(String emailInput, String password) {
        String email = str(emailInput).toLowerCase(Locale.ROOT);
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Enter a valid email address.");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Password is required.");
        List<Map<String, Object>> rows = db.queryForList("SELECT id, password_hash FROM users WHERE email = ?", email);
        if (rows.isEmpty() || !passwordEncoder.matches(password, str(rows.get(0).get("password_hash")))) {
            throw new IllegalArgumentException("Invalid email or password.");
        }
        return createSession(((Number) rows.get(0).get("id")).intValue());
    }

    /** Creates a database-backed login session so it survives application restarts. */
    private Map<String, Object> createSession(int userId) {
        String token = UUID.randomUUID().toString();
        db.update("INSERT INTO user_sessions(token, user_id, expires_at) VALUES(?,?,DATE_ADD(NOW(), INTERVAL 7 DAY))",
                token, userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("user", user(userId));
        return result;
    }

    /** Returns the user ID for a valid, unexpired bearer token. */
    public int requireUser(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Please log in to continue.");
        }
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT user_id FROM user_sessions WHERE token=? AND expires_at>NOW()", token.trim());
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Your session has expired. Please log in again.");
        }
        return ((Number) rows.get(0).get("user_id")).intValue();
    }

    /** Ensures the signed-in user has the requested account role. */
    public int requireRole(String token, String requiredRole) {
        int userId = requireUser(token);
        List<Map<String, Object>> rows = db.queryForList("SELECT role FROM users WHERE id=?", userId);
        if (rows.isEmpty() || !requiredRole.equalsIgnoreCase(String.valueOf(rows.get(0).get("role")))) {
            throw new IllegalArgumentException("You do not have permission to perform this action.");
        }
        return userId;
    }

    /** Ends a session on logout. */
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            db.update("DELETE FROM user_sessions WHERE token=?", token.trim());
        }
    }

    /** Returns the public profile fields used by the front end; never returns a password hash. */
    public Map<String, Object> user(int userId) {
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT id, first_name, last_name, email, phone, role, created_at FROM users WHERE id=?", userId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("User account not found.");
        }
        return rows.get(0);
    }

    public List<Map<String, Object>> venues() {
        return db.queryForList("SELECT * FROM venues WHERE active = TRUE ORDER BY id DESC");
    }

    public Map<String, Object> venue(int id) {
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM venues WHERE id = ? AND active = TRUE", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Venue not found or no longer available.");
        return rows.get(0);
    }

    public List<Map<String, Object>> ownerVenues(int ownerId) {
        return db.queryForList("SELECT * FROM venues WHERE owner_id = ? AND active = TRUE ORDER BY id DESC", ownerId);
    }

    @Transactional
    public int saveVenue(int ownerId, Map<String, Object> p, Integer editId) {
        String name = str(p.get("name"));
        String type = str(p.get("type"));
        String eventTypes = str(p.get("eventTypes")).isBlank() ? "All Events" : str(p.get("eventTypes"));
        String address = str(p.get("address"));
        String city = str(p.get("city"));
        String state = str(p.get("state"));
        String priceUnit = str(p.get("priceUnit")).isBlank() ? "Per Event" : str(p.get("priceUnit"));
        String amenities = str(p.get("amenities"));
        String image = str(p.get("image"));
        String mapUrl = str(p.get("mapUrl"));
        if (!mapUrl.isBlank()) {
            try { java.net.URI u = java.net.URI.create(mapUrl); if (!("https".equalsIgnoreCase(u.getScheme()) && (u.getHost() != null) && (u.getHost().equalsIgnoreCase("google.com") || u.getHost().endsWith(".google.com") || u.getHost().equalsIgnoreCase("maps.app.goo.gl") || u.getHost().equalsIgnoreCase("goo.gl")))) throw new IllegalArgumentException("Use a Google Maps link starting with https://www.google.com/maps or a Google Maps short link."); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Enter a valid Google Maps URL starting with https://."); }
        }
        int min = intValue(p.get("minCapacity"), "minimum capacity");
        int max = intValue(p.get("maxCapacity"), "maximum capacity");
        double price = decimalValue(p.get("price"), "price");

        if (name.isBlank() || type.isBlank() || address.isBlank() || city.isBlank() || state.isBlank()) {
            throw new IllegalArgumentException("Name, type, address, city, and state are required.");
        }
        if (min < 1 || max < min) throw new IllegalArgumentException("Enter a valid capacity range (minimum at least 1 and maximum not below minimum).");
        if (min > 100000 || max > 100000) throw new IllegalArgumentException("Capacity must be between 1 and 100,000 guests.");
        if (price <= 0 || !Double.isFinite(price)) throw new IllegalArgumentException("Price must be greater than zero.");
        if (!Set.of("Per Event", "Per Plate").contains(priceUnit)) throw new IllegalArgumentException("Choose Per Event or Per Plate pricing.");
        if (name.length() > 160 || city.length() > 80 || state.length() > 80) throw new IllegalArgumentException("One or more fields exceed their maximum length.");

        if (editId == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            db.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO venues(owner_id,name,type,event_types,address,city,state,min_capacity,max_capacity,price,price_unit,amenities,image,map_url,rating,active) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,TRUE)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, ownerId);
                ps.setString(2, name);
                ps.setString(3, type);
                ps.setString(4, eventTypes);
                ps.setString(5, address);
                ps.setString(6, city);
                ps.setString(7, state);
                ps.setInt(8, min);
                ps.setInt(9, max);
                ps.setDouble(10, price);
                ps.setString(11, priceUnit);
                ps.setString(12, amenities);
                ps.setString(13, image);
                ps.setString(14, mapUrl);
                ps.setDouble(15, 5.0);
                return ps;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key == null) throw new IllegalStateException("Venue was saved but its ID could not be read.");
            return key.intValue();
        }

        int changed = db.update("UPDATE venues SET name=?,type=?,event_types=?,address=?,city=?,state=?,min_capacity=?,max_capacity=?,price=?,price_unit=?,amenities=?,image=?,map_url=? WHERE id=? AND owner_id=? AND active=TRUE",
                name, type, eventTypes, address, city, state, min, max, price,
                priceUnit, amenities, image, mapUrl, editId, ownerId);
        if (changed == 0) throw new IllegalArgumentException("Venue not found, removed, or not owned by this account.");
        return editId;
    }

    public void deleteVenue(int ownerId, int venueId) {
        int changed = db.update("UPDATE venues SET active = FALSE WHERE id = ? AND owner_id = ? AND active = TRUE", venueId, ownerId);
        if (changed == 0) throw new IllegalArgumentException("Venue not found or not owned by this account.");
    }

    @Transactional
    public Map<String, Object> createBooking(int customerId, Map<String, Object> p) {
        int venueId = intValue(p.get("venueId"), "venue");
        // Lock the venue row while checking the booking slot to avoid parallel double-bookings.
        List<Integer> lockedVenue = db.query("SELECT id FROM venues WHERE id = ? AND active = TRUE FOR UPDATE",
                (rs, rowNum) -> rs.getInt(1), venueId);
        if (lockedVenue.isEmpty()) throw new IllegalArgumentException("Venue not found or no longer available.");
        Map<String, Object> venue = venue(venueId);
        int guests = intValue(p.get("guests"), "guest count");
        int minCapacity = ((Number) venue.get("min_capacity")).intValue();
        int maxCapacity = ((Number) venue.get("max_capacity")).intValue();
        if (guests < minCapacity || guests > maxCapacity) {
            throw new IllegalArgumentException("Guest count must be between " + minCapacity + " and " + maxCapacity + ".");
        }

        String dateText = str(p.get("eventDate"));
        String eventTime = str(p.get("eventTime"));
        if (dateText.isBlank() || eventTime.isBlank()) throw new IllegalArgumentException("Event date and time are required.");
        LocalDate date;
        try {
            date = LocalDate.parse(dateText);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Enter a valid event date.");
        }
        if (date.isBefore(LocalDate.now())) throw new IllegalArgumentException("Event date cannot be in the past.");

        int requestedStart = parseStartMinutes(eventTime);
        List<String> occupiedTimes = db.query("SELECT event_time FROM bookings WHERE venue_id=? AND event_date=? AND status IN ('PENDING','CONFIRMED')", (rs, rowNum) -> rs.getString(1), venueId, date);
        for (String occupied : occupiedTimes) {
            int occupiedStart = parseStartMinutes(occupied);
            if (Math.abs(requestedStart - occupiedStart) < 180) {
                throw new IllegalArgumentException("This time overlaps another pending or confirmed booking. Choose a time at least 3 hours apart.");
            }
        }

        double basePrice = decimalValue(venue.get("price"), "venue price");
        String priceUnit = str(venue.get("price_unit"));
        double amount = priceUnit.equalsIgnoreCase("Per Plate") ? basePrice * guests : basePrice;
        if (!Double.isFinite(amount) || amount > 999999999999.0) throw new IllegalArgumentException("Calculated booking total is outside the supported range.");
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            db.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO bookings(customer_id,venue_id,event_date,event_time,guests,event_type,requirements,customer_name,customer_phone,customer_email,amount,status) VALUES(?,?,?,?,?,?,?,?,?,?,?, 'PENDING')",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, customerId);
                ps.setInt(2, venueId);
                ps.setDate(3, java.sql.Date.valueOf(date));
                ps.setString(4, eventTime);
                ps.setInt(5, guests);
                ps.setString(6, requiredStr(p.get("eventType"), "event type"));
                ps.setString(7, str(p.get("requirements")));
                ps.setString(8, requiredStr(p.get("customerName"), "customer name"));
                ps.setString(9, requiredStr(p.get("customerPhone"), "customer phone"));
                ps.setString(10, requiredStr(p.get("customerEmail"), "customer email"));
                ps.setDouble(11, amount);
                return ps;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key == null) throw new IllegalStateException("Booking was created but its ID could not be read.");
            List<Map<String, Object>> rows = db.queryForList(
                    "SELECT b.*, v.name AS venue_name, v.city FROM bookings b JOIN venues v ON v.id=b.venue_id WHERE b.id=?",
                    key.intValue());
            return rows.get(0);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("The booking could not be saved because a related database record is missing or the slot was changed. Please reload and try again.");
        }
    }

    private static int parseStartMinutes(String range) {
        try {
            String start = range.split("\\s+-\\s+", 2)[0].trim();
            LocalTime time = LocalTime.parse(start, DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
            return time.getHour() * 60 + time.getMinute();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Choose a valid booking time slot.");
        }
    }

    public List<Map<String, Object>> customerBookings(int customerId) {
        return db.queryForList("SELECT b.*, v.name AS venue_name, v.city, v.image, CASE WHEN b.status IN ('CONFIRMED','COMPLETED') THEN owner.first_name ELSE NULL END AS owner_first_name, CASE WHEN b.status IN ('CONFIRMED','COMPLETED') THEN owner.last_name ELSE NULL END AS owner_last_name, CASE WHEN b.status IN ('CONFIRMED','COMPLETED') THEN owner.phone ELSE NULL END AS owner_phone, CASE WHEN b.status IN ('CONFIRMED','COMPLETED') THEN owner.email ELSE NULL END AS owner_email, CASE WHEN b.status='COMPLETED' THEN 1 ELSE 0 END AS review_eligible, (SELECT COUNT(*) FROM venue_reviews r WHERE r.booking_id=b.id) AS has_review FROM bookings b JOIN venues v ON v.id=b.venue_id LEFT JOIN users owner ON owner.id=v.owner_id WHERE b.customer_id=? ORDER BY b.event_date DESC,b.id DESC", customerId);
    }

    public List<Map<String, Object>> ownerBookings(int ownerId) {
        return db.queryForList("SELECT b.*, v.name AS venue_name, u.first_name, u.last_name, u.email, u.phone FROM bookings b JOIN venues v ON v.id=b.venue_id JOIN users u ON u.id=b.customer_id WHERE v.owner_id=? ORDER BY b.id DESC", ownerId);
    }

    public void updateBooking(int ownerId, int bookingId, String statusInput) {
        String status = statusInput.toUpperCase(Locale.ROOT);
        if (!Set.of("CONFIRMED", "REJECTED", "CANCELLED", "COMPLETED").contains(status)) {
            throw new IllegalArgumentException("Invalid booking status.");
        }
        int changed;
        if ("COMPLETED".equals(status)) {
            changed = db.update("UPDATE bookings b JOIN venues v ON v.id=b.venue_id SET b.status=? WHERE b.id=? AND v.owner_id=? AND b.status='CONFIRMED' AND b.event_date<=CURDATE()", status, bookingId, ownerId);
            if (changed == 0) throw new IllegalArgumentException("Only confirmed bookings dated today or earlier can be marked completed.");
        } else {
            changed = db.update("UPDATE bookings b JOIN venues v ON v.id=b.venue_id SET b.status=? WHERE b.id=? AND v.owner_id=? AND b.status='PENDING'", status, bookingId, ownerId);
            if (changed == 0) throw new IllegalArgumentException("Pending booking not found or not owned by this account.");
        }
    }

    public void cancelCustomerBooking(int customerId, int bookingId) {
        int changed = db.update("UPDATE bookings SET status='CANCELLED' WHERE id=? AND customer_id=? AND status IN ('PENDING','CONFIRMED') AND event_date>=CURDATE()", bookingId, customerId);
        if (changed == 0) throw new IllegalArgumentException("Only your upcoming pending or confirmed bookings can be cancelled.");
    }

    public List<Map<String, Object>> venueReviews(int venueId) {
        return db.queryForList("SELECT r.id,r.rating,r.review_text,r.created_at,u.first_name,u.last_name FROM venue_reviews r JOIN users u ON u.id=r.customer_id WHERE r.venue_id=? ORDER BY r.created_at DESC", venueId);
    }

    @Transactional
    public void createReview(int customerId, int bookingId, int rating, String reviewText) {
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("Choose a rating from 1 to 5 stars.");
        List<Map<String,Object>> eligible = db.queryForList("SELECT id,venue_id FROM bookings WHERE id=? AND customer_id=? AND status='COMPLETED'", bookingId, customerId);
        if (eligible.isEmpty()) throw new IllegalArgumentException("You can review a venue only after its owner marks your booking completed.");
        Map<String,Object> booking = eligible.get(0);
        try { db.update("INSERT INTO venue_reviews(booking_id,customer_id,venue_id,rating,review_text) VALUES(?,?,?,?,?)", bookingId, customerId, booking.get("venue_id"), rating, reviewText == null ? "" : reviewText.trim()); }
        catch (DataIntegrityViolationException ex) { throw new IllegalArgumentException("A review has already been submitted for this booking."); }
        db.update("UPDATE venues SET rating=(SELECT ROUND(AVG(rating),1) FROM venue_reviews WHERE venue_id=?) WHERE id=?", booking.get("venue_id"), booking.get("venue_id"));
    }

    public Map<String, Object> customerStats(int customerId) {
        return db.queryForMap("SELECT COUNT(*) AS total, COALESCE(SUM(status='PENDING'),0) AS pending, COALESCE(SUM(status='CONFIRMED'),0) AS confirmed, COALESCE(SUM(CASE WHEN status='CONFIRMED' THEN amount ELSE 0 END),0) AS total_value FROM bookings WHERE customer_id=?", customerId);
    }

    public Map<String, Object> ownerStats(int ownerId) {
        return db.queryForMap("SELECT (SELECT COUNT(*) FROM venues WHERE owner_id=? AND active=TRUE) AS venues, (SELECT COUNT(*) FROM bookings b JOIN venues v ON v.id=b.venue_id WHERE v.owner_id=?) AS bookings, (SELECT COALESCE(SUM(b.amount),0) FROM bookings b JOIN venues v ON v.id=b.venue_id WHERE v.owner_id=? AND b.status='CONFIRMED') AS revenue, (SELECT COALESCE(ROUND(AVG(r.rating),1),0) FROM venue_reviews r JOIN venues v ON v.id=r.venue_id WHERE v.owner_id=? AND v.active=TRUE) AS average_rating", ownerId, ownerId, ownerId, ownerId);
    }

    private static String requiredStr(Object value, String label) {
        String result = str(value);
        if (result.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return result;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intValue(Object value, String label) {
        try {
            return new java.math.BigDecimal(str(value)).intValueExact();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Enter a valid " + label + ".");
        }
    }

    private static double decimalValue(Object value, String label) {
        try {
            return new java.math.BigDecimal(str(value)).doubleValue();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Enter a valid " + label + ".");
        }
    }
}
