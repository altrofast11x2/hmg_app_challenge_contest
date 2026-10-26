// web/js/config.js 의 모양. config.js는 커밋하지 않는다 (.gitignore).
// 직접 만들지 말고 저장소 루트에서 `node web/scripts/write-config.mjs`를 실행한다
// (환경 변수 SUPABASE_URL·SUPABASE_PUBLISHABLE_KEY, 없으면 루트 local.properties의 supabase.url·supabase.anonKey를 읽는다).
// 공개 키(Publishable key)만 넣는다. secret/service_role 키는 절대 넣지 않는다.
export const SUPABASE_URL = 'https://<project-ref>.supabase.co';
export const SUPABASE_KEY = '<publishable key>';
