CREATE TABLE IF NOT EXISTS stock (sku VARCHAR(40) PRIMARY KEY, available INTEGER NOT NULL CHECK (available >= 0));
CREATE TABLE IF NOT EXISTS reservations (id UUID PRIMARY KEY);
CREATE TABLE IF NOT EXISTS reservation_lines (
  reservation_id UUID NOT NULL REFERENCES reservations(id),
  sku VARCHAR(40) NOT NULL, quantity INTEGER NOT NULL CHECK (quantity > 0),
  PRIMARY KEY (reservation_id, sku)
);
