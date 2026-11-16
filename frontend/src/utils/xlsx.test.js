import { describe, expect, it } from 'vitest';
import { buildXlsx, crc32, sheetXml } from './xlsx';

describe('xlsx', () => {
  it('computes the standard CRC-32', () => {
    expect(crc32(new TextEncoder().encode('123456789')).toString(16)).toBe('cbf43926');
  });

  it('writes a zip with the workbook parts', () => {
    const bytes = buildXlsx({ sheetName: 'Sales', rows: [['Name', 'Amount'], ['TV', { v: 1500.5, fmt: 'money' }]] });
    expect(bytes[0]).toBe(0x50); // "PK"
    expect(bytes[1]).toBe(0x4b);
    const text = new TextDecoder().decode(bytes);
    expect(text).toContain('xl/worksheets/sheet1.xml');
    expect(text).toContain('<v>1500.5</v>');
  });

  it('escapes text, keeps Dari and marks right-to-left sheets', () => {
    const xml = sheetXml({ rows: [['فروش <1> & "2"']], rtl: true });
    expect(xml).toContain('rightToLeft="1"');
    expect(xml).toContain('فروش &lt;1&gt; &amp; &quot;2&quot;');
  });
});
