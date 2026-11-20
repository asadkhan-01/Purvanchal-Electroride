import assert from 'node:assert/strict';
import test from 'node:test';

test('amount in words uses the Indian numbering system', async () => {
  // billingDocs imports react-native's Platform; stub it for node.
  const { rupeesInWords } = await import('../billingWords.ts');
  assert.equal(rupeesInWords(99750), 'Rupees Ninety Nine Thousand Seven Hundred Fifty Only');
  assert.equal(rupeesInWords(112100), 'Rupees One Lakh Twelve Thousand One Hundred Only');
  assert.equal(rupeesInWords(12345678.5), 'Rupees One Crore Twenty Three Lakh Forty Five Thousand Six Hundred Seventy Eight and Fifty Paise Only');
  assert.equal(rupeesInWords(0), 'Rupees Zero Only');
});
