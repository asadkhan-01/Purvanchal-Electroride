// Plain module (no React Native imports) so it can be unit-tested in node.
const ones = ['', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten', 'Eleven', 'Twelve',
  'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen'];
const tens = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];

function upTo99(n: number): string {
  return n < 20 ? ones[n] : tens[Math.floor(n / 10)] + (n % 10 ? ' ' + ones[n % 10] : '');
}
function upTo999(n: number): string {
  const h = Math.floor(n / 100), r = n % 100;
  return [h ? ones[h] + ' Hundred' : '', r ? upTo99(r) : ''].filter(Boolean).join(' ');
}

/** Indian-system amount in words, e.g. 99750 -> "Rupees Ninety Nine Thousand Seven Hundred Fifty Only". */
export function rupeesInWords(amount: number): string {
  const rupees = Math.floor(amount + 1e-9);
  const paise = Math.round((amount - rupees) * 100);
  if (rupees === 0 && paise === 0) return 'Rupees Zero Only';
  const parts: string[] = [];
  const crore = Math.floor(rupees / 1e7), lakh = Math.floor((rupees % 1e7) / 1e5);
  const thousand = Math.floor((rupees % 1e5) / 1e3), rest = rupees % 1e3;
  if (crore) parts.push(upTo999(crore) + ' Crore');
  if (lakh) parts.push(upTo99(lakh) + ' Lakh');
  if (thousand) parts.push(upTo99(thousand) + ' Thousand');
  if (rest) parts.push(upTo999(rest));
  let words = 'Rupees ' + (parts.join(' ') || 'Zero');
  if (paise) words += ' and ' + upTo99(paise) + ' Paise';
  return words + ' Only';
}
