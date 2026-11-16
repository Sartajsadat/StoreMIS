/**
 * Text typed by users (product, customer, supplier names…). It may be English or Dari/Pashto, so the browser
 * decides its direction from its own first letters and isolates it from the surrounding layout. Without this,
 * an English name like `LED TV 43"` would render as `"LED TV 43` inside a right-to-left page.
 */
export default function UserText({ children, component: Component = 'bdi', ...props }) {
  return (
    <Component dir="auto" {...props}>
      {children}
    </Component>
  );
}
