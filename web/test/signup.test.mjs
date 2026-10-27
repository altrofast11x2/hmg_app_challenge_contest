import assert from 'node:assert/strict';
import { test } from 'node:test';
import { normalizeJoinCode, SIGN_UP_MESSAGES, signUpProblem } from '../js/signup.js';

const valid = {
  joinCode: 'abcd 2345', name: '김운전', department: '영업팀', employeeNo: '', phone: '010-1234-5678',
  email: 'driver@example.com', password: 'password1', passwordConfirm: 'password1', consent: true,
};

test('기업 가입 코드는 대문자로 고치고 띄어쓰기·하이픈을 뺀다', () => {
  assert.equal(normalizeJoinCode(' abcd-2345 '), 'ABCD2345');
});

test('가입 정보가 다 맞으면 문제 없음, 하나라도 빠지면 그 칸을 알려 준다', () => {
  assert.equal(signUpProblem(valid), null);
  assert.match(signUpProblem({ ...valid, joinCode: 'ABCD1234' }), /가입 코드/, '헷갈리는 글자(1)가 든 코드는 없다');
  assert.match(signUpProblem({ ...valid, department: ' ' }), /부서/);
  assert.match(signUpProblem({ ...valid, phone: '1234' }), /휴대폰/);
  assert.match(signUpProblem({ ...valid, email: 'driver' }), /이메일/);
  assert.match(signUpProblem({ ...valid, password: 'short', passwordConfirm: 'short' }), /8자/);
  assert.match(signUpProblem({ ...valid, passwordConfirm: 'different1' }), /확인/);
  assert.match(signUpProblem({ ...valid, consent: false }), /동의/);
  assert.equal(signUpProblem({ ...valid, employeeNo: '' }), null, '사번은 없어도 된다');
});

test('서버 결과마다 안내 문장이 있다', () => {
  for (const status of ['INVALID_CODE', 'INVALID_NAME', 'INVALID_DEPARTMENT', 'INVALID_PHONE', 'INVALID_EMAIL', 'WEAK_PASSWORD', 'EMAIL_TAKEN', 'TOO_MANY']) {
    assert.ok(SIGN_UP_MESSAGES[status], status);
  }
});
