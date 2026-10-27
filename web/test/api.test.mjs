// 아이디 로그인: 아이디를 로그인 이메일로 바꾸는 규칙
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { displayName, fetchAllPages, loginEmail } from '../js/api.js';

test('아이디는 대소문자 구분 없이 예약 도메인 주소로 바꾸고, 이메일은 그대로 쓴다', () => {
  assert.equal(loginEmail('AltroStudio'), 'altrostudio@drivelog.invalid');
  assert.equal(loginEmail('  altrostudio '), 'altrostudio@drivelog.invalid');
  assert.equal(loginEmail('admin@example.com'), 'admin@example.com');
  // 이메일 아이디도 대소문자를 구분하지 않는다 (개발자 계정 AltroStudio@altro.com)
  assert.equal(loginEmail(' AltroStudio@altro.com '), 'altrostudio@altro.com');
});

test('화면에는 표시 이름, 없으면 아이디나 이메일을 보여 준다', () => {
  assert.equal(displayName({ email: 'altrostudio@drivelog.invalid', name: 'AltroStudio' }), 'AltroStudio');
  assert.equal(displayName({ email: 'altrostudio@drivelog.invalid', name: null }), 'altrostudio');
  assert.equal(displayName({ email: 'admin@example.com', name: null }), 'admin@example.com');
});

test('운행을 서버 한도(한 번에 1000줄)보다 많이 받을 때는 나눠 받아 모두 이어 붙인다', async () => {
  const all = Array.from({ length: 1234 }, (_, i) => ({ id: i }));
  const calls = [];
  const rows = await fetchAllPages(async (offset, limit) => {
    calls.push(offset);
    return all.slice(offset, offset + limit);
  }, 500);
  assert.equal(rows.length, 1234);
  assert.deepEqual(calls, [0, 500, 1000]);
  assert.equal((await fetchAllPages(async () => [], 500)).length, 0);
});
