// 앱의 개인정보 처리방침(app/src/main/res/values/strings.xml의 privacy_body)으로 웹 공개 페이지 privacy.html을 만든다.
// Pleos App Market 심사 제출에는 처리방침 URL이 필요하다. 앱과 웹의 문구가 어긋나지 않도록 앱 문구 하나만 고친다
import { readFileSync } from 'node:fs';

const STRINGS_XML = new URL('../../app/src/main/res/values/strings.xml', import.meta.url);

const escapeHtml = (text) => text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

/** strings.xml 내용에서 처리방침을 꺼내 HTML 문단으로 바꾼다 ("\n\n"은 항목 구분, 항목의 첫 줄은 제목) */
export function privacySections(stringsXml) {
  const match = stringsXml.match(/<string name="privacy_body">([\s\S]*?)<\/string>/);
  if (!match) throw new Error('strings.xml에 privacy_body가 없습니다');
  // XML 문자 참조와 안드로이드 문자열의 \' \" 를 원래 글자로 되돌린다 (HTML로는 privacyPage에서 다시 바꾼다)
  const body = match[1]
    .replace(/\\'/g, "'").replace(/\\"/g, '"')
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&');
  const [intro, ...sections] = body.split('\\n\\n');
  return {
    intro,
    sections: sections.map((section) => {
      const [title, ...lines] = section.split('\\n');
      return { title, lines };
    }),
  };
}

export function privacyPage(stringsXml = readFileSync(STRINGS_XML, 'utf8')) {
  const { intro, sections } = privacySections(stringsXml);
  const content = [
    `<p>${escapeHtml(intro)}</p>`,
    ...sections.flatMap(({ title, lines }) => [`<h2>${escapeHtml(title)}</h2>`, ...lines.map((line) => `<p>${escapeHtml(line)}</p>`)]),
  ].map((line) => `    ${line}`).join('\n');
  return `<!doctype html>
<html lang="ko" data-theme="dark">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta http-equiv="Content-Security-Policy"
        content="default-src 'none'; style-src 'self' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; base-uri 'none'; form-action 'none'">
  <meta name="referrer" content="no-referrer">
  <title>차곡 개인정보 처리방침</title>
  <link rel="icon" href="data:,">
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Asta+Sans:wght@400;500;600;700&display=swap">
  <link rel="stylesheet" href="css/tokens.css">
  <link rel="stylesheet" href="css/policy.css">
</head>
<body>
  <main class="policy">
    <h1>차곡 개인정보 처리방침</h1>
    <p class="policy-meta">차량 앱(Pleos Connect)에 적용 · 시행일 2026년 10월 28일</p>
${content}
  </main>
</body>
</html>
`;
}
