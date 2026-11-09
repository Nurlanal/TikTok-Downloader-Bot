'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const { parsePayments, normalizePhoneInput, formatPhone, amountToMinor } = require('../src/parser');

const fixtures = JSON.parse(
  fs.readFileSync(path.join(__dirname, '..', '..', 'shared', 'parser-fixtures.json'), 'utf8')
);

for (const c of fixtures.cases) {
  test(`fixture: ${c.name}`, () => {
    const got = parsePayments(c.text, c.source).map(({ phone, amountMinor, txnId, paidAt, confidence, status }) => ({
      phone, amountMinor, txnId, paidAt, confidence, status,
    }));
    assert.deepEqual(got, c.expected);
  });
}

test('normalizePhoneInput müxtəlif formatlar', () => {
  assert.equal(normalizePhoneInput('055 777 66 55'), '+994557776655');
  assert.equal(normalizePhoneInput('+994 55 777-66-55'), '+994557776655');
  assert.equal(normalizePhoneInput('557776655'), '+994557776655');
  assert.equal(normalizePhoneInput('12345'), null);
  assert.equal(normalizePhoneInput('0337776655'), null);
});

test('formatPhone', () => {
  assert.equal(formatPhone('+994557776655'), '055 777 66 55');
});

test('amountToMinor', () => {
  assert.equal(amountToMinor('5'), 500);
  assert.equal(amountToMinor('12,5'), 1250);
  assert.equal(amountToMinor('1,250.75'), 125075);
  assert.equal(amountToMinor('1 250,50'), 125050);
  assert.equal(amountToMinor('0.05'), 5);
});

test('100 dəfə 5 AZN = 500 AZN', () => {
  const text = Array.from({ length: 100 }, () => '055 777 66 55 — 5 AZN').join('\n');
  const res = parsePayments(text, 'TEXT');
  assert.equal(res.length, 100);
  assert.equal(res.reduce((s, r) => s + r.amountMinor, 0), 50000);
});
