CREATE TABLE IF NOT EXISTS users (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(80) NOT NULL,
    last_name VARCHAR(80) NOT NULL,
    email VARCHAR(160) NOT NULL UNIQUE,
    phone VARCHAR(30),
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('customer','owner') NOT NULL DEFAULT 'customer',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS venues (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id INT NULL,
    name VARCHAR(160) NOT NULL,
    type VARCHAR(80) NOT NULL,
    event_types VARCHAR(120),
    address TEXT NOT NULL,
    city VARCHAR(80) NOT NULL,
    state VARCHAR(80) NOT NULL,
    min_capacity INT NOT NULL,
    max_capacity INT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    price_unit VARCHAR(40) NOT NULL DEFAULT 'Per Event',
    amenities TEXT,
    image TEXT,
    map_url TEXT NULL,
    rating DECIMAL(2,1) NOT NULL DEFAULT 5.0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_venues_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_venues_capacity CHECK (min_capacity >= 1 AND max_capacity >= min_capacity),
    CONSTRAINT chk_venues_price CHECK (price > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS bookings (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    venue_id INT NOT NULL,
    event_date DATE NOT NULL,
    event_time VARCHAR(80) NOT NULL,
    guests INT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    requirements TEXT,
    customer_name VARCHAR(160) NOT NULL,
    customer_phone VARCHAR(30) NOT NULL,
    customer_email VARCHAR(160) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status ENUM('PENDING','CONFIRMED','REJECTED','COMPLETED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bookings_customer FOREIGN KEY (customer_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_bookings_venue FOREIGN KEY (venue_id) REFERENCES venues(id) ON DELETE CASCADE,
    INDEX idx_bookings_customer (customer_id, event_date),
    INDEX idx_bookings_venue (venue_id, event_date)
) ENGINE=InnoDB;


CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_reset_expiry (expires_at)
) ENGINE=InnoDB;


CREATE TABLE IF NOT EXISTS user_sessions (
    token CHAR(36) NOT NULL PRIMARY KEY,
    user_id INT NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_sessions_expiry (expires_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS venue_reviews (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id INT NOT NULL UNIQUE,
    customer_id INT NOT NULL,
    venue_id INT NOT NULL,
    rating TINYINT NOT NULL,
    review_text TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_review_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_customer FOREIGN KEY (customer_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_venue FOREIGN KEY (venue_id) REFERENCES venues(id) ON DELETE CASCADE,
    INDEX idx_reviews_venue (venue_id, created_at)
) ENGINE=InnoDB;

-- Hide only the original unowned demo seed listings; keep any related booking history.
UPDATE venues SET active=FALSE WHERE owner_id IS NULL AND name IN (
  'Royal Palace Banquet','The Grand Pavilion','Gardenia Events','Royal Orchid Hall'
);
