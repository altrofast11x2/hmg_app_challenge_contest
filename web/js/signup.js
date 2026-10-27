// 직원 회원가입 입력 확인과 서버 결과 안내. 화면(DOM)을 쓰지 않는 순수 함수라 node --test로 시험한다.
// 서버(member_sign_up)도 같은 규칙으로 다시 확인한다.

/** 기업 가입 코드: 헷갈리는 글자(0 O 1 I L)를 뺀 8자리. 소문자·띄어쓰기는 고쳐서 받는다 */
export const normalizeJoinCode = (value) => String(value ?? '').toUpperCase().replace(/[\s-]/g, '');

const JOIN_CODE = /^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}$/;
const PHONE = /^[0-9+\- ]{9,20}$/;
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

/** 입력이 모자라거나 이상하면 안내 문장, 괜찮으면 null */
export function signUpProblem(form) {
  if (!JOIN_CODE.test(normalizeJoinCode(form.joinCode))) return '기업 가입 코드 8자리를 확인하세요. 회사 관리자에게 받을 수 있어요.';
  if (!form.name?.trim()) return '이름을 입력하세요.';
  if (!form.department?.trim()) return '부서를 입력하세요.';
  if (!PHONE.test(form.phone?.trim() ?? '')) return '휴대폰 번호를 확인하세요 (예: 010-1234-5678).';
  if (!EMAIL.test(form.email?.trim() ?? '')) return '아이디로 쓸 이메일을 확인하세요.';
  if ((form.password ?? '').length < 8) return '비밀번호는 8자 이상이어야 해요.';
  if (form.password !== form.passwordConfirm) return '비밀번호 확인이 같지 않아요.';
  if (!form.consent) return '개인정보 수집·이용에 동의해야 가입할 수 있어요.';
  return null;
}

/** 서버 결과 → 안내 문장 (OK는 따로 보여 준다) */
export const SIGN_UP_MESSAGES = {
  INVALID_CODE: '기업 가입 코드가 맞지 않아요. 회사 관리자에게 다시 확인하세요.',
  INVALID_NAME: '이름을 확인하세요.',
  INVALID_DEPARTMENT: '부서를 확인하세요.',
  INVALID_PHONE: '휴대폰 번호를 확인하세요.',
  INVALID_EMAIL: '아이디로 쓸 이메일을 확인하세요.',
  WEAK_PASSWORD: '비밀번호는 8자 이상이어야 해요.',
  EMAIL_TAKEN: '이미 가입된 이메일이에요. 로그인하거나 다른 이메일을 쓰세요.',
  TOO_MANY: '가입 신청이 너무 많아요. 잠시 뒤 다시 시도하세요.',
};
