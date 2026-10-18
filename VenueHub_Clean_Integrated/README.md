# VenueHub — clean integrated project

This is a **standalone, clean replacement project** for the current mixed backend. It keeps the supplied VenueHub HTML/CSS/JavaScript pages and connects them to one Spring Boot + JDBC backend.

## Why this version is easier to integrate

- No JPA entities or Spring Data repositories: that removes the duplicate `Venue`, `VenueRepository`, and `VenueController` conflicts from the current folder.
- All database access uses `JdbcTemplate` and the SQL schema included here.
- The frontend and API share the same field names (`type`, `event_types`, `price`, `price_unit`, `image`, etc.).
- The project uses a separate database named `venuehub_app`, so it **does not overwrite or alter the existing `venuehub` database**. The schema only creates missing tables and deactivates the original unowned demo rows by their known names; it does not drop tables or delete booking history.

## Requirements

- JDK 21
- MySQL Server and MySQL Workbench
- Maven (the supplied `run-venuehub.ps1` uses the Maven path previously used on this PC; edit it if yours differs)

## Setup — follow in order

### 1. Keep a backup of the current folder

Rename the current folder to `venuehub-backup` or make a copy before using this clean project. Do not merge this project's Java files into the old `src/main/java` tree; that would bring the duplicate classes back.

### 2. Extract this ZIP into a new folder

For example:

`C:\Users\Pratham\Desktop\VenueHub-Clean`

Open that extracted folder in VS Code. Confirm `pom.xml` is in the project root.

### 3. Create the new database

Open MySQL Workbench, connect to your local server, open `setup.sql` from the project root, and execute it. It only creates the empty `venuehub_app` database; it does not modify `venuehub`.

### 4. Set your MySQL password

Set your local MySQL credentials using environment variables in PowerShell before starting the app (recommended, so you do not commit passwords to GitHub):

```powershell
$env:VENUEHUB_DB_USERNAME = "root"
$env:VENUEHUB_DB_PASSWORD = "YOUR_MYSQL_PASSWORD"
```

The defaults in `application.properties` still point to the local `venuehub_app` database. If you prefer, you can edit those defaults locally, but do not commit real passwords.

### 5. Start the app

Open PowerShell in the extracted project root and run:

```powershell
powershell -ExecutionPolicy Bypass -File .\run-venuehub.ps1
```

The script sets the JDK path used on this PC and runs Maven. The launcher calls the Spring Boot Maven plugin by its full coordinates to avoid Maven's plugin-prefix resolution error. Alternatively, if Java and Maven are already configured in your terminal:

```powershell
mvn org.springframework.boot:spring-boot-maven-plugin:3.5.6:run
```

On first startup, Spring Boot creates any missing tables. VenueHub starts with no public sample venues; owners add their own listings. Keep the server window open while using the site. After the one-time database password setup, double-click `Start VenueHub.bat` to start/open the site. If port 8080 is already occupied by VenueHub, the launcher opens the existing instance instead of starting a duplicate.

### 6. Open the site

Open `http://localhost:8080/` in your browser. To verify the API separately, open `http://localhost:8080/api/venues` — successful output contains `"success": true` and a `data` array.

If the API works, you can also run this optional PowerShell smoke test in a second terminal:

```powershell
powershell -ExecutionPolicy Bypass -File .\smoke-test.ps1
```

## Main features

- Customer and venue-owner registration/login (passwords hashed with BCrypt)
- Starts empty: no hardcoded venue cards or sample venue rows are publicly shown
- Persistent seven-day sessions stored in MySQL, so navigating pages and restarting the app does not automatically sign users out
- Role-aware navigation and server-side customer/owner authorization
- Booking requests validate venue capacity, date and overlapping time slots
- Per-plate bookings calculate price × guest count; per-event bookings retain the venue's fixed price
- Customers can cancel upcoming pending/confirmed bookings
- Owners can accept/reject requests and mark eligible past confirmed bookings completed
- Owner contact details are shown to the customer after confirmation
- Customers can review a venue only after a completed booking, once per booking
- Owner dashboard, create/edit/soft-remove venue listings, image fallback and city suggestions
- Password reset works with configured SMTP or provides a one-time, expiring local-demo reset link when email is not configured

## API routes implemented

- `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`
- `GET /api/venues`, `GET /api/venues/{id}`
- `GET /api/me`
- `POST /api/bookings`
- `GET /api/customer/stats`, `GET /api/customer/bookings`, `PATCH /api/customer/bookings/{id}/cancel`
- `GET /api/venues/{id}/reviews`, `POST /api/customer/reviews`
- `GET /api/owner/stats`, `GET /api/owner/venues`, `POST /api/owner/venues`, `DELETE /api/owner/venues/{id}`
- `GET /api/owner/bookings`, `PATCH /api/owner/bookings/{id}`

## Notes

- This is an educational mini-project, not production-grade authentication. Session tokens are stored in the local MySQL database and expire after seven days.
- This package creates its own schema in `venuehub_app`. It does not automatically migrate old records from the previous `venuehub` database. That separation is intentional to avoid further schema conflicts or accidental data loss.
- MySQL credentials cannot be assumed or tested from this package. You must enter the password for your local MySQL server before running it.


## Premium theme and improvement notes
- The shared stylesheet now uses the premium navy-and-gold palette across the pages.
- The homepage hamburger icon has been removed; navigation links wrap on small screens.
- The homepage footer now links to separate Customer FAQ and Owner FAQ sections on `faq.html`.
- Owner dashboard queries only active venues; removing a venue is still a soft delete so booking history remains intact.
- Venue image cards use a neutral fallback if an image URL is empty or cannot load. For best results, use a direct, publicly accessible HTTPS image URL.
- Booking Accept/Reject actions refresh the dashboard after the backend responds.
- Password recovery has request and reset pages and backend endpoints. To actually deliver reset links, configure `VENUEHUB_SMTP_HOST`, `VENUEHUB_SMTP_PORT`, `VENUEHUB_SMTP_USERNAME`, `VENUEHUB_SMTP_PASSWORD`, and `VENUEHUB_SMTP_FROM` as environment variables before starting the app. Without valid SMTP settings, the reset email cannot be sent. Reset tokens expire after 30 minutes and are stored hashed in `password_reset_tokens`.

**Database safety:** `schema.sql` creates the new reset-token table with `IF NOT EXISTS`; it does not drop or recreate existing users, venues, or bookings. Keep your existing MySQL password configured in `application.properties`; do not commit real credentials to a public repository.


## Updated functional notes

- `Start VenueHub.bat` is the convenient launcher. The MySQL password in `application.properties` must be configured once first.
- When SMTP is not configured, the local password-recovery page displays a one-time reset link for a registered account; this is intended for localhost demonstrations only. Configure SMTP before hosting the application publicly.
- Image URLs must be direct, publicly accessible HTTP/HTTPS image URLs. Broken links show a fallback illustration rather than a broken-image icon.
- Review eligibility is tied to a booking marked `COMPLETED` by the owner after the event date.


## Gmail password-reset email setup

Password recovery is wired to Spring Mail. For Gmail, use a dedicated Gmail account and a Google **App Password** (requires 2-Step Verification); never put your normal Gmail password in this project or commit an App Password. Set these environment variables in the same PowerShell window before starting VenueHub:

```powershell
$env:VENUEHUB_SMTP_HOST = "smtp.gmail.com"
$env:VENUEHUB_SMTP_PORT = "587"
$env:VENUEHUB_SMTP_USERNAME = "your-gmail-address@gmail.com"
$env:VENUEHUB_SMTP_PASSWORD = "your-16-character-google-app-password"
$env:VENUEHUB_SMTP_FROM = "your-gmail-address@gmail.com"
$env:VENUEHUB_APP_BASE_URL = "http://localhost:8080"
.\Start-VenueHub.ps1
```

Replace the two placeholder values with your Gmail address and generated App Password. Generate an App Password in your Google Account security settings after enabling 2-Step Verification. Do not share the App Password in chat. The reset link expires after 30 minutes. For a deployed site, set `VENUEHUB_APP_BASE_URL` to its HTTPS URL; `localhost` links only work on the computer running VenueHub. If SMTP is not configured, the local demo fallback remains enabled.

## Google Maps

Venue details now include a Google Maps embed and an “Open in Google Maps” directions/search link based on the saved venue name and address. This uses Google Maps' query/embed URL, so it does not require a Maps API key for this simple map view. It requires an internet connection.

## Venue validation

Venue price must be greater than zero. Minimum capacity must be at least 1, maximum capacity must be at least the minimum, and both capacities must be whole numbers no greater than 100,000. These rules are checked in the browser and again by the backend.


Google Sign-In is not included in this version. VenueHub uses email/password authentication.

## Owner-provided Google Maps links
When adding or editing a venue, an owner can paste the venue's Google Maps **Share → Copy link** URL into the optional Google Maps link field. Venue details will use that saved link for the directions button. The embedded map still uses the venue's saved address/name as its query, so the basic map does not require a Maps JavaScript API key. Existing databases are migrated automatically by adding the nullable `map_url` column if it is missing.


## Publishing a live website

Pushing this source code to GitHub does **not** by itself make a Spring Boot + MySQL website live. GitHub stores the code; the Java backend needs an application host and the database needs a reachable hosted MySQL instance. For a public demo, deploy the Spring Boot app to a Java-capable host (for example, Render or Railway) and use a hosted MySQL database. Configure `VENUEHUB_DB_URL`, `VENUEHUB_DB_USERNAME`, and `VENUEHUB_DB_PASSWORD` as environment variables on that host. Also configure `VENUEHUB_APP_BASE_URL` to the final HTTPS website URL if password-reset links are used. Never publish database passwords in GitHub.

Before sharing the public link, test registration, login/logout, customer booking, owner accept/reject, venue create/edit/remove, and the browser console on the deployed instance.
