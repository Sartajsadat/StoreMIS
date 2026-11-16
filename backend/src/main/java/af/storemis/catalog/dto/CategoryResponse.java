package af.storemis.catalog.dto;

import af.storemis.catalog.entity.Category;

public record CategoryResponse(Long id, String name, String description, boolean active, long productCount, int version) {

    public static CategoryResponse from(Category c, long productCount) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isActive(), productCount, c.getVersion());
    }
}
