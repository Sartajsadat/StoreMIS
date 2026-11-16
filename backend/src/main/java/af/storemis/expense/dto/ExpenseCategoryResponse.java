package af.storemis.expense.dto;

import af.storemis.expense.entity.ExpenseCategory;

public record ExpenseCategoryResponse(Long id, String code, String name, boolean active, int version) {

    public static ExpenseCategoryResponse from(ExpenseCategory c) {
        return new ExpenseCategoryResponse(c.getId(), c.getCode(), c.getName(), c.isActive(), c.getVersion());
    }
}
