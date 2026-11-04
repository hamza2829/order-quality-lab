CREATE TABLE IF NOT EXISTS orders (id UUID PRIMARY KEY, status VARCHAR(20) NOT NULL, reason VARCHAR(100));
CREATE TABLE IF NOT EXISTS order_lines (
  order_id UUID NOT NULL REFERENCES orders(id), position INTEGER NOT NULL,
  sku VARCHAR(40) NOT NULL, quantity INTEGER NOT NULL CHECK (quantity > 0),
  amount DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL,
  PRIMARY KEY (order_id, position)
);
