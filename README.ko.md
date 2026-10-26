# Resonance

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) ·
[日本語](README.ja.md) · [한국어](README.ko.md) · [Español](README.es.md)

로컬 파일 가져오기、재생 대기열、챕터、파일별 재개 위치、실시간 파형을 가진 오프라인 Android 미디어
플레이어입니다. Android10+、영어／번체／간체 UI와 명암／system을 지원하며 계정이 필요하지 않습니다.

## 가져오기와 배포

Android 파일 선택기로 가져와 App 비공개 저장소에 복사합니다. 기존 로컬 파일은 오프라인 재생하며
codec 지원은 장치에 달렸습니다. 개인 미디어 모음이나 타사 표지를 포함하지 않습니다. 선택적인 LAN
shelf만 자신의 컴퓨터에서 stream／copy합니다.

공개 배포는 [v0.9.1](https://github.com/codingEzio/Resonance/releases/tag/v0.9.1)의 서명
APK、SHA256SUMS、공개 인증서 지문입니다. 필요할 때 브라우저／file manager 설치를 허용하고
[RELEASE.md](RELEASE.md)로 검증합니다. 현재 소스 후보0.9.2／code11은 미공개、미서명이며 후보 검사는
실기／배포 검수가 아닙니다. F-Droid는0.9.1기록에서 신청 중이고 현재 제공 여부는 이번에 확인하지
않았습니다.

## 빌드

자유 JDK17、Android SDK37、build tools36.0.0과 ANDROID_HOME 설정이 필요합니다.
`./gradlew -Presonance.profile=portable :app:assembleRelease`는 미서명 APK를 만듭니다. Gradle은
Google Maven／Maven Central에서 의존을 받으며 계정、개인 설정、서명 키、미디어는 필요하지 않습니다.

GitHub 배포는 보존된 개발자 release key를 사용합니다. 같은 서명을 쓰는 F-Droid 재빌드 제안은 별도
승인／재현성 검증 대상입니다. `./gradlew :app:assembleDebug`는 .catalog를 붙입니다. 공개 ID는
io.github.codingezio.resonance로 개인 개발 App과 다르며 다른 App의 비공개 데이터를 자동 가져오지
않습니다.

## 선택적인 LAN shelf

Deno、ffprobe、shasum을 설치하고 server/config.example.json을 <private-data>/config.json에 복사한 뒤
명시한 라이브러리 절대 경로를 설정합니다. `deno task serve`를 시작하고 서버 localhost:12345의 연결
정보로 Android를 페어링합니다. 기본 소스는 비어 있고 자동 시작하지 않습니다. 선택한 root만 제공하며
HTTP는 암호화하지 않으므로 신뢰할 LAN에 한정합니다. Android 단독 가져오기／오프라인 재생은 shelf
없이 가능합니다.

server/config.json은 기본값、RESONANCE_CONFIG는 overlay、RESONANCE_BIND／RESONANCE_PORT는
bind／port를 덮어쓰고 state는 선택한 비공개 디렉터리에 둡니다. 개인 Mac 버전과 라이브러리는 공개
소스 범위 밖입니다.

## 비공개 데이터 프로필

`<private-data>` 기본값은 macOS의 `~/Library/Application Support/ResonanceShelf`、Linux XDG、Windows
Local AppData입니다. 이번 개정은 현재 Mac과 격리 fixture만 확인했습니다. RESONANCE_DATA는 checkout
밖의 절대 디렉터리이며 기본 설정은 그 안의 config.json입니다. RESONANCE_CONFIG는 설정 파일만
선택합니다. stateDirectory 점은 같은 프로필、상대 값도 그 기준입니다. source를 향하는 물리 링크와
부모 경로 이동을 거부합니다.

다른 프로필은 `RESONANCE_DATA=/path/to/private-shelf-data`로 지정하고 디렉터리에
server/config.example.json을 config.json으로 복사해 자신의 root를 선택합니다. Git에서 제외하고 별도
백업합니다. 경로 변경은 자동 데이터 이전이 아닙니다.

기존 Local/shelf／Local/portable-shelf가 있으면 일반 시작은 실패합니다. 자신의 shelf를 멈추고 설정을
유지하며 config.json／access-key／catalog.json을 기존 프로필에 덮어쓰지 않고 복사합니다.
stateDirectory를 점으로 바꾸고 키／카탈로그 hash와 다른 설정을 확인합니다. 원본은 source 밖에 비공개
보존한 뒤 재개합니다. 명시적인 새 RESONANCE_DATA는 별도 프로필 선택입니다. 후보 시험은 개인 데이터를
사용하지 않습니다.

## 개인정보와 권리

미디어、위치、대기열、설정、wishlist는 로컬에 둡니다. 광고／analytics SDK는 없고 선택한 shelf 연결만
pairing、미디어 metadata、요청한 파일을 보냅니다. Android cloud backup은 꺼져 있으며 삭제 설치는 App
비공개 데이터를 지웁니다.

코드와 자체 App artwork는 [GPL-3.0-or-later](LICENSE), 글꼴／제3자 부품은 원 조건을 유지합니다.
[PRIVACY.md](PRIVACY.md)、[DEPENDENCIES.md](DEPENDENCIES.md)、[QUALITY.md](QUALITY.md)를
참조하십시오. 사용할 권리가 있는 미디어를 제공합니다. 과거 스크린샷／0.9.1 증거는0.9.2
실기、성능、서명、F-Droid 검증이 아닙니다. 이 준비는 설치、shelf 시작、장치／미디어 접근을 하지
않습니다.
