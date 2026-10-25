CREATE DATABASE IF NOT EXISTS railreserve_auth;
CREATE DATABASE IF NOT EXISTS railreserve_train;
CREATE DATABASE IF NOT EXISTS railreserve_booking;
CREATE DATABASE IF NOT EXISTS railreserve_notification;
GRANT ALL PRIVILEGES ON railreserve_auth.* TO 'railreserve_app'@'%';
GRANT ALL PRIVILEGES ON railreserve_train.* TO 'railreserve_app'@'%';
GRANT ALL PRIVILEGES ON railreserve_booking.* TO 'railreserve_app'@'%';
GRANT ALL PRIVILEGES ON railreserve_notification.* TO 'railreserve_app'@'%';
