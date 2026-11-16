/** Seeded categories carry a code and get a translated name; categories the user created show their own name. */
export function categoryLabel(t, category) {
  if (!category) return '';
  const code = category.code ?? category.categoryCode;
  const name = category.name ?? category.categoryName;
  return code ? t(`expenses.categoryCodes.${code}`, name) : name;
}
