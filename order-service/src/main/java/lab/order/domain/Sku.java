package lab.order.domain;

public record Sku(String value) {
    public Sku {
        if (value == null || !value.matches("[A-Z0-9][A-Z0-9_-]{0,39}"))
            throw new IllegalArgumentException("SKU must be 1 to 40 uppercase letters, digits, underscores or hyphens");
    }
}
