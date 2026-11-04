// 소개 페이지. 예전 앱(0.2.0)이 만든 휴대폰 QR 주소(https://…/#share=토큰&from=…&to=…)는 관리자 웹으로 그대로 넘긴다.
// 토큰은 # 뒤에 있어 서버로 가지 않고, 주소 기록에도 남기지 않도록 replace를 쓴다.
if (location.hash.startsWith('#share=')) {
  location.replace(`app.html${location.hash}`);
}
