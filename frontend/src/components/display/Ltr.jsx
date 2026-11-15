/**
 * Keeps numbers, amounts, invoice numbers, codes and phone numbers left-to-right and isolated,
 * so they stay readable inside Dari/Pashto text.
 */
export default function Ltr({ children, component: Component = 'span', style, ...props }) {
  return (
    <Component dir="ltr" style={{ unicodeBidi: 'isolate', ...style }} {...props}>
      {children}
    </Component>
  );
}
