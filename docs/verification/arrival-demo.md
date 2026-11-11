# 도착 재현 시연 빌드

## 빌드와 실행

```sh
./gradlew :app:assembleDemo :app:installDemo
```

Android Studio에서는 app의 Build Variant를 `demo`로 선택하고 실행한다.
APK: `app/build/outputs/apk/demo/app-demo.apk`
앱 이름: **TripArchive Demo**, 패키지: `com.triparchive.demo`.
일반 앱과 별도로 설치되므로 시연 앱에서 프로필을 한 번 생성해야 한다.

`debug`와 `release`에는 도착 재현 컨트롤 구현이 포함되지 않는다. `src/demo`에만 조작 UI가 있고 다른 빌드는 같은 함수의 빈 구현을 사용한다.

## 시연 순서

1. 순정 내비게이션에서 목적지를 설정하고 안내를 시작한다.
2. TripArchive Demo에서 프로필을 선택한다. 홈 하단에 실제 SDK 목적지가 표시된다.
3. 우측 중앙의 노란 데모 패널에서 **도착 재현**을 누른다. 활성 SDK 목적지가 없으면 버튼은 비활성화된다.
4. 해당 목적지 이름으로 홈 위에 도착 다이얼로그가 열린다. **데모 · 도착 재현 중** 표시가 유지된다.
5. **도착 확인하고 계속하기** 또는 뒤로 가기로 다이얼로그를 닫는다. 5초 카운트다운 후에도 자동으로 닫히며, 홈에서 최신 SDK 상태를 표시한다. 여행 종료 확인창과 앱 백그라운드에서는 카운트다운을 멈추고 복귀 시 남은 시간을 이어서 센다.
6. 다시 재현할 수 있다. **여기서 여행 마치기 → 취소**는 도착 다이얼로그를 다시 표시하고, **마치기**는 기존 로그아웃 흐름을 실행한다.

SDK 관찰은 계속 유지되며 재현 중 목적지 갱신이 와도 도착 다이얼로그를 덮어쓰지 않는다. 재현은 순정 경로를 변경하거나 취소하지 않는다. 따라서 계속하기·초기화 후에는 순정 경로가 활성 상태라면 같은 목적지가 다시 표시되는 것이 정상이다. 프로필을 나가면 재현 상태는 폐기된다.

이 기능은 앱 화면의 도착 상태만 재현한다. 순정 내비게이션의 실제 `onDestinationArrived` 수신 검증과 구분해야 한다. 앱 재시작 시 재현 상태는 유지하지 않는다.

## 자동 검증

```sh
./gradlew :app:connectedDemoAndroidTest -PtestBuildType=demo -Pandroid.testInstrumentationRunnerArguments.class=com.triparchive.DestinationHomeTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.triparchive.DestinationHomeTest
```

데모 테스트는 목적지 부재 시 비활성화, 실제 데이터 흐름 역할의 Flow 갱신 중 재현 유지, 초기화, 계속하기, 종료 취소·확인 및 원본 상태 보존을 확인한다. 일반 빌드 테스트는 데모 버튼이 없는지 확인한다. 테스트는 가짜 Flow를 사용하므로 실제 SDK 연결 검증을 대신하지 않는다.
