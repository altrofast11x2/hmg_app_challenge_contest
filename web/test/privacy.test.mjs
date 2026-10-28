// 웹 공개 처리방침은 앱 처리방침(strings.xml)에서 만든다
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { privacyPage, privacySections } from '../scripts/privacy.mjs';

test('앱 처리방침의 항목을 제목과 문단으로 나누고, 글자는 HTML로 안전하게 바꾼다', () => {
  const xml = '<string name="privacy_body">머리말 &lt;A&gt;\\n\\n1. 수집 항목\\n거리\\n\\n2. 문의\\nAltroStudio \\\'팀\\\'</string>';

  const { intro, sections } = privacySections(xml);
  assert.equal(intro, '머리말 <A>');
  assert.deepEqual(sections, [
    { title: '1. 수집 항목', lines: ['거리'] },
    { title: '2. 문의', lines: ["AltroStudio '팀'"] },
  ]);
  const page = privacyPage(xml);
  assert.ok(page.includes('<p>머리말 &lt;A&gt;</p>'));
  assert.match(page, /<h2>1\. 수집 항목<\/h2>\n {4}<p>거리<\/p>/);
});

test('실제 앱 처리방침의 모든 항목(수집 항목부터 문의까지)이 페이지에 들어간다', () => {
  const xml = readFileSync(new URL('../../app/src/main/res/values/strings.xml', import.meta.url), 'utf8');
  const page = privacyPage(xml);
  const titles = privacySections(xml).sections.map((section) => section.title);

  assert.equal(titles[0], '1. 수집 항목');
  assert.match(titles.at(-1), /문의$/);
  for (const title of titles) assert.ok(page.includes(`<h2>${title}</h2>`), title);
  assert.ok(page.includes('kazinobel9807@gmail.com'));
});
